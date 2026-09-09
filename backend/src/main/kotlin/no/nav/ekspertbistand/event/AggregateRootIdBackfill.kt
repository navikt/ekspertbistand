package no.nav.ekspertbistand.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.isActiveAndNotTerminating
import no.nav.ekspertbistand.infrastruktur.logger
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.ForUpdate
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * P4 — engangs backfill av `aggregate_root_id` for rader som ble skrevet før kolonnen fantes.
 *
 * Jobben er en bakgrunnsløkke i backend, startet fra `Application.kt` etter samme mønster som
 * projeksjonene. Den er **selv-avsluttende**: når begge tabellene er ferdige settes `completed_at`
 * på state-radene, og påfølgende oppstarter gjør ett billig oppslag og returnerer. Den koster altså
 * ingenting på senere deploys.
 *
 * Rekkefølge: `event_queue` først (liten, og fjerner kilden til nye NULL-rader i loggen), deretter
 * `event_log`.
 *
 * Egenskaper:
 * - Restartbar: markør i [BackfillState.cursorPos], oppdatert per batch i samme transaksjon som UPDATE.
 * - Én pod om gangen: `FOR UPDATE SKIP LOCKED` på state-raden (samme mønster som projeksjonene).
 * - Idempotent: setter kun rader der `aggregate_root_id IS NULL`; å kjøre den to ganger er et no-op.
 * - Skånsom: batch på N rader, `delay` mellom batcher, `lock_timeout` + `statement_timeout` per batch.
 */
@OptIn(ExperimentalTime::class)
class AggregateRootIdBackfill(
    private val database: Database,
    private val config: AggregateRootIdBackfillConfig = AggregateRootIdBackfillConfig(),
) {
    private val log = logger()

    /** Kjører til begge tabellene er ferdige, deretter returnerer den. */
    suspend fun run() = withContext(Dispatchers.IO) {
        if (!config.enabled) {
            log.info("AggregateRootIdBackfill er deaktivert (enabled=false), hopper over")
            return@withContext
        }
        // event_queue først, så event_log — se klassedokumentasjonen.
        listOf(BackfillTable.EVENT_QUEUE, BackfillTable.EVENT_LOG).forEach { table ->
            backfillTable(table)
        }
    }

    private suspend fun CoroutineScope.backfillTable(table: BackfillTable) {
        if (isCompleted(table.jobName)) {
            log.info("Backfill ${table.jobName} allerede fullført, hopper over")
            return
        }
        initState(table.jobName)

        var endReachedCount = 0
        while (isActiveAndNotTerminating) {
            val progress = try {
                claimAndRunBatch(table)
            } catch (e: ExposedSQLException) {
                // En batch-timeout (lock_timeout/statement_timeout) er ikke en feil — markøren står
                // stille, så ingenting går tapt. Logg på WARN, vent, og prøv igjen.
                log.warn("Backfill ${table.jobName}: batch feilet (timeout?), prøver igjen", e)
                delay(config.pause)
                continue
            }

            when {
                // En annen pod holder låsen på state-raden. Den fører jobben videre; vi trekker oss.
                progress == null -> {
                    log.info("Backfill ${table.jobName}: state-raden er låst av en annen pod, avslutter her")
                    return
                }
                // Markøren har nådd enden → sveip-fase.
                progress.newCursor == null -> if (finishOrRewind(table, ++endReachedCount)) return
                else -> log.info(
                    "Backfill ${table.jobName}: markør ${progress.newCursor}, oppdaterte ${progress.updated}"
                )
            }
            delay(config.pause)
        }
    }

    /**
     * Kjører én batch i én transaksjon: claim av state-raden med `FOR UPDATE SKIP LOCKED`, den
     * atomiske batch-spørringen, og oppdatering av markør/tellere. Returnerer null dersom en annen
     * pod holder låsen.
     */
    private fun claimAndRunBatch(table: BackfillTable): BatchProgress? = transaction(database) {
        // SET LOCAL gjelder kun denne transaksjonen. Verdiene kommer fra config (kode), ikke input.
        exec("SET LOCAL lock_timeout = '${config.lockTimeout.inWholeMilliseconds}ms'")
        exec("SET LOCAL statement_timeout = '${config.statementTimeout.inWholeMilliseconds}ms'")

        val stateRow = BackfillState
            .select(BackfillState.cursorPos, BackfillState.scanned, BackfillState.updated, BackfillState.skipped)
            .where { BackfillState.jobName eq table.jobName }
            .forUpdate(ForUpdate(SKIP_LOCKED))
            .firstOrNull()
            ?: return@transaction null

        val result = runBatchQuery(table, stateRow[BackfillState.cursorPos])

        BackfillState.update(where = { BackfillState.jobName eq table.jobName }) {
            if (result.newCursor != null) it[cursorPos] = result.newCursor
            it[scanned] = stateRow[BackfillState.scanned] + result.scanned
            it[updated] = stateRow[BackfillState.updated] + result.updated
            it[skipped] = stateRow[BackfillState.skipped] + (result.scanned - result.updated)
            it[updatedAt] = Clock.System.now()
        }
        BatchProgress(newCursor = result.newCursor, updated = result.updated)
    }

    /**
     * Én statement per batch. Flytter markøren og gjør oppdateringen atomisk, og returnerer tellere.
     * Markøren går på primærnøkkelen (ikke på `aggregate_root_id IS NULL`), slik at scannen er monoton
     * og indeksert og aldri kan henge på uderiverbare rader.
     */
    private fun JdbcTransaction.runBatchQuery(table: BackfillTable, cursor: Long): BatchResult =
        exec(
            batchSql(table.tableName),
            args = listOf(LongColumnType() to cursor, IntegerColumnType() to config.batchSize),
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            rs.next()
            val newCursor = rs.getLong("new_cursor").takeUnless { rs.wasNull() }
            BatchResult(
                newCursor = newCursor,
                scanned = rs.getLong("scanned"),
                updated = rs.getLong("updated"),
            )
        }!!

    /**
     * Sveip-fase: når markøren er i enden telles gjenstående NULL-rader.
     * - 0 → sett `completed_at`, ferdig.
     * - > 0 og første runde → nullstill markøren og kjør en runde til (billig, nesten alt har verdi).
     * - > 0 etter to runder → mapping-feil; logg på ERROR med de første id-ene og stopp jobben.
     */
    private fun finishOrRewind(table: BackfillTable, endReachedCount: Int): Boolean = transaction(database) {
        val remaining = countNull(table)
        when {
            remaining == 0L -> {
                val now = Clock.System.now()
                BackfillState.update(where = { BackfillState.jobName eq table.jobName }) {
                    it[completedAt] = now
                    it[updatedAt] = now
                }
                log.info("Backfill ${table.jobName} fullført: alle rader har aggregate_root_id")
                true
            }

            endReachedCount >= 2 -> {
                val ids = firstNullIds(table, limit = 20)
                log.error(
                    "Backfill ${table.jobName}: $remaining rader mangler fortsatt aggregate_root_id " +
                        "etter to runder. Første id-er: $ids. Stopper — dette er en mapping-feil som " +
                        "skal fikses i kode, ikke maskeres."
                )
                true
            }

            else -> {
                log.warn(
                    "Backfill ${table.jobName}: $remaining rader mangler fortsatt aggregate_root_id, " +
                        "nullstiller markør og kjører en runde til"
                )
                BackfillState.update(where = { BackfillState.jobName eq table.jobName }) {
                    it[cursorPos] = 0
                }
                false
            }
        }
    }

    private fun isCompleted(jobName: String): Boolean = transaction(database) {
        BackfillState
            .select(BackfillState.completedAt)
            .where { BackfillState.jobName eq jobName }
            .firstOrNull()?.get(BackfillState.completedAt) != null
    }

    private fun initState(jobName: String) = transaction(database) {
        BackfillState.insertIgnore {
            it[BackfillState.jobName] = jobName
            it[cursorPos] = 0
        }
    }

    private fun JdbcTransaction.countNull(table: BackfillTable): Long =
        exec(
            "SELECT count(*) AS c FROM ${table.tableName} WHERE aggregate_root_id IS NULL",
            explicitStatementType = StatementType.SELECT,
        ) { rs -> if (rs.next()) rs.getLong("c") else 0L } ?: 0L

    private fun JdbcTransaction.firstNullIds(table: BackfillTable, limit: Int): List<Long> =
        exec(
            "SELECT id FROM ${table.tableName} WHERE aggregate_root_id IS NULL ORDER BY id LIMIT $limit",
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            buildList { while (rs.next()) add(rs.getLong("id")) }
        } ?: emptyList()

    private data class BatchResult(val newCursor: Long?, val scanned: Long, val updated: Long)
    private data class BatchProgress(val newCursor: Long?, val updated: Long)
}

/**
 * Tabellene som backfilles, med jobbnavnet som brukes som nøkkel i [BackfillState].
 * `tableName` interpoleres inn i SQL — verdiene er konstanter i koden, aldri brukerinput.
 */
enum class BackfillTable(val tableName: String) {
    EVENT_QUEUE("event_queue"),
    EVENT_LOG("event_log");

    val jobName: String get() = "aggregate_root_id:$tableName"
}

/**
 * Batch-spørringen. Flytter markøren og gjør oppdateringen atomisk i én statement, og returnerer
 * `new_cursor` (null = tom batch, dvs. markøren har nådd enden), `scanned` og `updated`.
 *
 * Deriveringen speiler [EventData.aggregateRootId]: søknad-id har forrang, deretter tilsagnnummer
 * satt sammen som `aar:loepenrSak:loepenrTilsagn` (jf. `TilsagnNummer.concat()`), til slutt et
 * toppnivå `tilsagnNummer` (TilskuddsbrevVist uten søknad).
 */
private fun batchSql(tableName: String) = """
    WITH batch AS (
        SELECT id,
               coalesce(
                   event_json -> 'soknad' ->> 'id',
                   nullif(concat_ws(':',
                       event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'aar',
                       event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrSak',
                       event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrTilsagn'
                   ), ''),
                   event_json ->> 'tilsagnNummer'
               ) AS arid
        FROM $tableName
        WHERE id > ?
        ORDER BY id
        LIMIT ?
    ),
    upd AS (
        UPDATE $tableName e
           SET aggregate_root_id = b.arid
          FROM batch b
         WHERE e.id = b.id
           AND e.aggregate_root_id IS NULL
           AND b.arid IS NOT NULL
        RETURNING e.id
    )
    SELECT (SELECT max(id)   FROM batch) AS new_cursor,
           (SELECT count(*)  FROM batch) AS scanned,
           (SELECT count(*)  FROM upd)   AS updated
""".trimIndent()

/**
 * Konfigurasjon settes i kode etter mønster fra [EventManagerConfig] — ingen env-vars. Å skru
 * jobben av (`enabled = false`) krever en kodeendring og en deploy, med den fordelen at avgjørelsen
 * ligger i git-historikken.
 */
@OptIn(ExperimentalTime::class)
data class AggregateRootIdBackfillConfig(
    val enabled: Boolean = true,
    val batchSize: Int = basedOnEnv(other = 1000, prod = 1000, dev = 200),
    val pause: Duration = 100.milliseconds,
    val lockTimeout: Duration = 2.seconds,
    val statementTimeout: Duration = 30.seconds,
)

/** Generisk state for restartbare backfill-jobber (opprettet i V8). */
@OptIn(ExperimentalTime::class)
object BackfillState : Table("backfill_state") {
    val jobName = text("job_name")
    val cursorPos = long("cursor_pos")
    val scanned = long("scanned")
    val updated = long("updated")
    val skipped = long("skipped")
    val completedAt = timestamp("completed_at").nullable()
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(jobName)
}
