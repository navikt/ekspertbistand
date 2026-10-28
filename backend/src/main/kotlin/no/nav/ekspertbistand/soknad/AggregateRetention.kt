package no.nav.ekspertbistand.soknad

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.oebs.model.FAGSYSTEM_KILDE
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Data om en søknad eller sak oppbevares i 10 kalenderår etter siste oppdatering. */
const val OPPBEVARINGSTID_AAR = 10

/**
 * Sletter aggregater (søknad/sak med alt tilknyttet) når siste event er eldre enn
 * [OPPBEVARINGSTID_AAR] år. Se specifications/sletterutiner_10_ar_og_notifikasjon_455_dager.md.
 *
 * Et aggregat slettes bare når alle projeksjoner har lest eventene, køen er tom og det ikke
 * finnes uferdige OeBS-meldinger. Hvert aggregat slettes i egen transaksjon, og kriteriene
 * sjekkes på nytt i den transaksjonen.
 */
@OptIn(ExperimentalTime::class)
class AggregateRetention(private val database: Database) {
    private val log = logger()

    private val slettetCounter = Metrics.meterRegistry.counter("aggregat_slettet_total")
    private val utsattCounter = Metrics.meterRegistry.counter("aggregat_sletting_utsatt_total")
    private val feilCounter = Metrics.meterRegistry.counter("aggregat_sletting_feil_total")

    /** Returnerer antall slettede aggregater. */
    fun slettGamleAggregater(clock: Clock = Clock.System, batchSize: Int = 100): Int {
        val cutoff = oppbevaringsgrense(clock.now())
        var slettet = 0
        val behandlet = mutableSetOf<String>()

        while (true) {
            val kandidater = transaction(database) { finnKandidater(cutoff, batchSize + behandlet.size) }
                .filterNot { it in behandlet }
                .take(batchSize)
            if (kandidater.isEmpty()) break

            kandidater.forEach { aggregateRootId ->
                behandlet += aggregateRootId
                try {
                    val ble = transaction(database) { slettAggregat(aggregateRootId, cutoff) }
                    if (ble) {
                        slettet++
                        slettetCounter.increment()
                    } else {
                        utsattCounter.increment()
                    }
                } catch (e: Exception) {
                    feilCounter.increment()
                    log.error("Sletting av aggregat $aggregateRootId feilet", e)
                }
            }
        }

        log.info("Slettet $slettet aggregater med siste oppdatering før $cutoff")
        return slettet
    }

    private fun JdbcTransaction.finnKandidater(cutoff: LocalDateTime, limit: Int): List<String> =
        query(
            """
            SELECT e.aggregate_root_id
            FROM event_log e
            GROUP BY e.aggregate_root_id
            HAVING max(e.created_at) < ?::timestamp
               AND max(e.id) <= (SELECT min(p.position) FROM projection_builder_state p)
            ORDER BY max(e.created_at)
            LIMIT ?
            """.trimIndent(),
            TextColumnType() to cutoff.toString(),
            IntegerColumnType() to limit,
        ) { getString(1) }

    /** Returnerer false hvis aggregatet ikke (lenger) kan slettes. */
    private fun JdbcTransaction.slettAggregat(aggregateRootId: String, cutoff: LocalDateTime): Boolean {
        if (!erFortsattKandidat(aggregateRootId, cutoff)) return false

        val soknadId = aggregateRootId.tilUuidOrNull()
        val arenaSakId = soknadId?.let { arenaSakIdFor(it) }
        val oebsPrefiks = arenaSakId?.let { "$FAGSYSTEM_KILDE-$it-" }

        if (oebsPrefiks != null && harUferdigOebsMelding(oebsPrefiks)) return false

        if (oebsPrefiks != null) {
            listOf(
                "oebs_outbox",
                "oebs_sendt_melding",
                "oebs_mottatt_status",
                "oebs_bestilling_status",
                "oebs_faktura_lopenummer",
            ).forEach { tabell ->
                update("DELETE FROM $tabell WHERE starts_with(bestillingsnummer, ?)", TextColumnType() to oebsPrefiks)
            }
            update("DELETE FROM oebs_lopenummer WHERE sak_id = ?", TextColumnType() to arenaSakId)
        }

        val text = TextColumnType() to aggregateRootId
        if (soknadId != null) {
            update(
                """
                DELETE FROM tilskuddsbrev_vist_state
                WHERE tilsagn_nummer IN (SELECT tilsagnnummer FROM tilsagndata WHERE soknad_id = ?::uuid)
                """.trimIndent(),
                text,
            )
            update("DELETE FROM tilsagndata WHERE soknad_id = ?::uuid", text)
        } else {
            // Tilsagn fra Altinn uten søknad: aggregatroten er tilsagnsnummeret.
            update("DELETE FROM tilskuddsbrev_vist_state WHERE tilsagn_nummer = ?", text)
            update("DELETE FROM tilsagndata WHERE tilsagnnummer = ? AND soknad_id IS NULL", text)
        }

        update("DELETE FROM soknad_behandlet_forsinkelse_state WHERE soknad_id = ?", text)
        update("DELETE FROM fagsak_id_mapping WHERE soknad_id = ?", text)

        if (soknadId != null) {
            // Cascade: sak, saksvilkar, sakslogg, sak_retur, forelopig_vedtak, vedtak,
            // refusjonskrav, sluttrapport og vedlegg.
            update("DELETE FROM soknad WHERE id = ?::uuid", text)
        }

        update(
            """
            DELETE FROM event_handler_states
            WHERE id IN (SELECT id FROM event_log WHERE aggregate_root_id = ?)
            """.trimIndent(),
            text,
        )
        update(
            """
            DELETE FROM idempotency_guard_records
            WHERE event_id IN (SELECT id FROM event_log WHERE aggregate_root_id = ?)
            """.trimIndent(),
            text,
        )
        update("DELETE FROM event_log WHERE aggregate_root_id = ?", text)

        return true
    }

    private fun JdbcTransaction.erFortsattKandidat(aggregateRootId: String, cutoff: LocalDateTime): Boolean =
        query(
            """
            SELECT
                max(e.created_at) < ?::timestamp
                AND max(e.id) <= (SELECT min(p.position) FROM projection_builder_state p)
                AND NOT EXISTS (SELECT 1 FROM event_queue q WHERE q.aggregate_root_id = ?)
            FROM event_log e
            WHERE e.aggregate_root_id = ?
            """.trimIndent(),
            TextColumnType() to cutoff.toString(),
            TextColumnType() to aggregateRootId,
            TextColumnType() to aggregateRootId,
        ) { getBoolean(1) }.singleOrNull() == true

    private fun JdbcTransaction.arenaSakIdFor(soknadId: UUID): String? =
        query(
            "SELECT arena_sak_id FROM sak WHERE soknad_id = ?::uuid AND arena_sak_id IS NOT NULL",
            TextColumnType() to soknadId.toString(),
        ) { getString(1) }.singleOrNull()

    private fun JdbcTransaction.harUferdigOebsMelding(prefiks: String): Boolean =
        query(
            "SELECT 1 FROM oebs_outbox WHERE starts_with(bestillingsnummer, ?) AND status <> 'PUBLISHED' LIMIT 1",
            TextColumnType() to prefiks,
        ) { getInt(1) }.isNotEmpty()

    private fun <T> JdbcTransaction.query(
        sql: String,
        vararg args: Pair<IColumnType<*>, Any?>,
        map: java.sql.ResultSet.() -> T,
    ): List<T> = exec(sql, args.toList()) { rs ->
        buildList { while (rs.next()) add(rs.map()) }
    } ?: emptyList()

    private fun JdbcTransaction.update(sql: String, vararg args: Pair<IColumnType<*>, Any?>) {
        exec(sql, args.toList())
    }
}

/** Siste oppdatering må være før denne grensen (UTC) for at aggregatet kan slettes. */
@OptIn(ExperimentalTime::class)
internal fun oppbevaringsgrense(now: Instant): LocalDateTime {
    val naa = now.toLocalDateTime(TimeZone.UTC)
    return LocalDateTime(naa.date.minus(OPPBEVARINGSTID_AAR, DateTimeUnit.YEAR), naa.time)
}

private fun String.tilUuidOrNull(): UUID? =
    if (UUID_REGEX.matches(this)) UUID.fromString(this) else null

private val UUID_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
