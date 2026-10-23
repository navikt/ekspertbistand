package no.nav.ekspertbistand.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import no.nav.ekspertbistand.infrastruktur.isActiveAndNotTerminating
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.NyTidStrategi
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Engangsjobb: skedulerer hard delete i notifikasjonsplattformen for saker som ble opprettet før vi
 * satte `hardDelete` på sakene. Slettetidspunktet er siste oppdatering på saken pluss
 * [hardDeleteINotifikasjonsplattformenOm]. Siste oppdatering er det seneste av siste event i
 * `event_log` og `soknad.opprettet_tidspunkt` for aggregatet. Grupperingsid i plattformen er
 * aggregatroten: søknads-id, eller tilsagnsnummeret for legacy-tilsagn fra Altinn.
 *
 * - Selvavsluttende: setter `backfill_state.completed_at` når alle saker er behandlet uten feil.
 *   Senere oppstarter gjør ett oppslag og returnerer.
 * - Idempotent: bruker [NyTidStrategi.FORLENG], så jobben forkorter aldri en frist som allerede er
 *   satt, og kan trygt kjøres flere ganger.
 * - Én pod om gangen: en lease på state-raden (`updated_at`), fornyet underveis.
 * - Feiler et kall, fortsetter jobben med neste sak. Den kjøres på nytt etter [nyttForsokOm] til
 *   alle kall har gått bra.
 * - Saker der fristen allerede er passert, hoppes over og listes i rapporten. Det skal ikke finnes
 *   slike, siden løsningen har vært i produksjon i under 455 dager.
 */
@OptIn(ExperimentalTime::class)
class SkedulerHardDeleteForEldreSakerJobb(
    private val database: Database,
    private val produsentApiKlient: ProdusentApiKlient,
    private val clock: Clock = Clock.System,
    private val pauseMellomKall: Duration = 50.milliseconds,
    private val nyttForsokOm: Duration = 1.hours,
) {
    private val log = logger()

    suspend fun run() = withContext(Dispatchers.IO) {
        while (isActiveAndNotTerminating) {
            when (kjorEnGang()) {
                Utfall.FERDIG, Utfall.ANNEN_POD -> return@withContext
                Utfall.FEIL -> delay(nyttForsokOm)
            }
        }
    }

    internal suspend fun kjorEnGang(): Utfall {
        if (!taLease()) return Utfall.ANNEN_POD

        val saker = transaction(database) { hentSaker() }
        val rapport = Rapport(totalt = saker.size)
        val naa = clock.now()
        var sistFornyet = naa

        for (sak in saker) {
            val slettes = sak.sistOppdatert + hardDeleteINotifikasjonsplattformenOm
            if (slettes <= naa) {
                rapport.fristPassert += sak.grupperingsid
                continue
            }
            try {
                produsentApiKlient.skedulerHardDeleteSak(sak.grupperingsid, slettes, NyTidStrategi.FORLENG)
                rapport.skedulert++
            } catch (e: SkedulerHardDeleteSakFinnesIkkeException) {
                rapport.finnesIkke++
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                rapport.feilet++
                log.warn("Klarte ikke å skedulere hard delete for sak {}", sak.grupperingsid, e)
            }
            if (clock.now() - sistFornyet > LEASE_FORNYES_ETTER) {
                fornyLease()
                sistFornyet = clock.now()
            }
            delay(pauseMellomKall)
        }

        val ferdig = rapport.feilet == 0
        avsluttKjoring(rapport, ferdig)
        log.info(
            "Skedulering av hard delete for eldre saker {}: totalt={}, skedulert={}, finnesIkkeIPlattformen={}, " +
                "feilet={}, fristPassert={} {}",
            if (ferdig) "fullført" else "ikke fullført, prøver igjen om $nyttForsokOm",
            rapport.totalt, rapport.skedulert, rapport.finnesIkke, rapport.feilet,
            rapport.fristPassert.size, rapport.fristPassert,
        )
        return if (ferdig) Utfall.FERDIG else Utfall.FEIL
    }

    private fun JdbcTransaction.hentSaker(): List<Sak> =
        exec(
            """
            SELECT grupperingsid, floor(extract(epoch FROM max(sist_oppdatert)))::bigint
            FROM (
                SELECT aggregate_root_id AS grupperingsid, created_at AS sist_oppdatert FROM event_log
                UNION ALL
                SELECT id::text, opprettet_tidspunkt FROM soknad
            ) a
            GROUP BY grupperingsid
            ORDER BY grupperingsid
            """.trimIndent(),
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            buildList {
                while (rs.next()) add(Sak(rs.getString(1), Instant.fromEpochSeconds(rs.getLong(2))))
            }
        } ?: emptyList()

    /** Tar jobben hvis den ikke er fullført og ingen annen pod har fornyet leasen nylig. */
    private fun taLease(): Boolean = transaction(database) {
        oppdater(
            "INSERT INTO backfill_state (job_name, updated_at) VALUES (?, '-infinity') ON CONFLICT DO NOTHING",
            TextColumnType() to JOB_NAME,
        )
        exec(
            """
            UPDATE backfill_state SET updated_at = now() AT TIME ZONE 'UTC'
            WHERE job_name = ? AND completed_at IS NULL
              AND updated_at < (now() AT TIME ZONE 'UTC') - make_interval(secs => ?)
            RETURNING job_name
            """.trimIndent(),
            args = listOf(TextColumnType() to JOB_NAME, LongColumnType() to LEASE_VARIGHET.inWholeSeconds),
            explicitStatementType = StatementType.SELECT,
        ) { rs -> rs.next() } ?: false
    }

    private fun fornyLease() = transaction(database) {
        oppdater(
            "UPDATE backfill_state SET updated_at = now() AT TIME ZONE 'UTC' WHERE job_name = ?",
            TextColumnType() to JOB_NAME,
        )
    }

    /** Lagrer tellere og slipper leasen, så neste forsøk ikke må vente på at den går ut. */
    private fun avsluttKjoring(rapport: Rapport, ferdig: Boolean) = transaction(database) {
        oppdater(
            """
            UPDATE backfill_state
            SET scanned = ?, updated = ?, skipped = ?, updated_at = '-infinity',
                completed_at = CASE WHEN ? THEN now() AT TIME ZONE 'UTC' END
            WHERE job_name = ?
            """.trimIndent(),
            LongColumnType() to rapport.totalt.toLong(),
            LongColumnType() to rapport.skedulert.toLong(),
            LongColumnType() to (rapport.totalt - rapport.skedulert).toLong(),
            BooleanColumnType() to ferdig,
            TextColumnType() to JOB_NAME,
        )
    }

    private fun JdbcTransaction.oppdater(sql: String, vararg args: Pair<IColumnType<*>, Any?>) {
        exec(sql, args.toList())
    }

    internal enum class Utfall { FERDIG, ANNEN_POD, FEIL }

    private data class Sak(val grupperingsid: String, val sistOppdatert: Instant)

    private class Rapport(
        val totalt: Int,
        var skedulert: Int = 0,
        var finnesIkke: Int = 0,
        var feilet: Int = 0,
        val fristPassert: MutableList<String> = mutableListOf(),
    )

    companion object {
        const val JOB_NAME = "skeduler_hard_delete_eldre_saker"
        private val LEASE_VARIGHET = 15.minutes
        private val LEASE_FORNYES_ETTER = 5.minutes
    }
}
