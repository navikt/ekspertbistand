package no.nav.ekspertbistand.soknad

import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.tilsagndata.findTilsagnDataByTilsagnNummer
import no.nav.ekspertbistand.tilsagndata.insertTilsagndata
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RetentionTest {

    private val now = Instant.parse("2036-06-15T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@RetentionTest.now
    }

    private val ellevaarSiden = "2025-06-01 12:00:00"
    private val niAarSiden = "2027-06-01 12:00:00"

    @Test
    fun `oppbevaringsgrensen er 10 kalenderaar i UTC`() {
        assertEquals("2026-06-15T12:00", oppbevaringsgrense(now).toString())
        assertEquals("2026-02-28T08:30", oppbevaringsgrense(Instant.parse("2036-02-29T08:30:00Z")).toString())
    }

    @Test
    fun `sletter alt for aggregat der siste oppdatering er eldre enn 10 aar`() = medDatabase { db ->
        val soknadId = UUID.randomUUID()
        val tilsagnnummer = "2025:1:1"
        transaction(db) {
            settInnSoknad(soknadId)
            settInnSakMedVedtak(soknadId, arenaSakId = "2025/1")
            settInnEvent(1, soknadId.toString(), ellevaarSiden)
            settInnEvent(2, soknadId.toString(), ellevaarSiden)
            settProjeksjonsposisjon(10)
            sql("""INSERT INTO tilsagndata (id, tilsagnnummer, soknad_id, "tilsagnData") VALUES (gen_random_uuid(), ?, ?::uuid, '{}')""", tilsagnnummer, soknadId)
            sql("INSERT INTO tilskuddsbrev_vist_state (tilsagn_nummer, tilskuddsbrev_opprettet_at) VALUES (?, now())", tilsagnnummer)
            sql("INSERT INTO soknad_behandlet_forsinkelse_state (soknad_id, innsendt_at) VALUES (?, now())", soknadId)
            sql("INSERT INTO fagsak_id_mapping (soknad_id) VALUES (?)", soknadId)
            sql("INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json, status) VALUES ('E-2025/1-1', 'BESTILLING', '{}', 'PUBLISHED')")
            sql("INSERT INTO oebs_lopenummer (sak_id) VALUES ('2025/1')")
            sql("INSERT INTO event_handler_states (id, handler_id, result) VALUES (1, 'h', '{}')")
            sql("INSERT INTO idempotency_guard_records (event_id, sub_task, event_name, status) VALUES (1, 's', 'e', 'COMPLETED')")
        }

        assertEquals(1, AggregateRetention(db).slettGamleAggregater(clock))

        transaction(db) {
            listOf(
                "soknad", "sak", "forelopig_vedtak", "vedtak", "event_log", "tilsagndata",
                "tilskuddsbrev_vist_state", "soknad_behandlet_forsinkelse_state", "fagsak_id_mapping",
                "oebs_outbox", "oebs_lopenummer", "event_handler_states", "idempotency_guard_records",
            ).forEach { tabell -> assertEquals(0, antall(tabell), "$tabell skal være tom") }
        }
    }

    @Test
    fun `sletter tilsagn fra Altinn uten soknad`() = medDatabase { db ->
        val tilsagnnummer = "2025:2:1"
        transaction(db) {
            settInnEvent(1, tilsagnnummer, ellevaarSiden)
            settProjeksjonsposisjon(10)
            sql("""INSERT INTO tilsagndata (id, tilsagnnummer, soknad_id, "tilsagnData") VALUES (gen_random_uuid(), ?, NULL, '{}')""", tilsagnnummer)
            sql("INSERT INTO tilskuddsbrev_vist_state (tilsagn_nummer, tilskuddsbrev_opprettet_at) VALUES (?, now())", tilsagnnummer)
        }

        assertEquals(1, AggregateRetention(db).slettGamleAggregater(clock))

        transaction(db) {
            assertEquals(0, antall("tilsagndata"))
            assertEquals(0, antall("tilskuddsbrev_vist_state"))
            assertEquals(0, antall("event_log"))
        }
    }

    @Test
    fun `beholder aggregat med oppdatering nyere enn 10 aar`() = medDatabase { db ->
        val soknadId = UUID.randomUUID()
        transaction(db) {
            settInnSoknad(soknadId)
            settInnEvent(1, soknadId.toString(), ellevaarSiden)
            settInnEvent(2, soknadId.toString(), niAarSiden)
            settProjeksjonsposisjon(10)
        }

        assertEquals(0, AggregateRetention(db).slettGamleAggregater(clock))
        transaction(db) { assertEquals(1, antall("soknad")) }
    }

    @Test
    fun `beholder aggregat med event i koen`() = medDatabase { db ->
        val soknadId = UUID.randomUUID()
        transaction(db) {
            settInnSoknad(soknadId)
            settInnEvent(1, soknadId.toString(), ellevaarSiden)
            settProjeksjonsposisjon(10)
            sql("INSERT INTO event_queue (event_json, aggregate_root_id) VALUES ('{}', ?)", soknadId)
        }

        assertEquals(0, AggregateRetention(db).slettGamleAggregater(clock))
        transaction(db) { assertEquals(1, antall("soknad")) }
    }

    @Test
    fun `beholder aggregat som ikke alle projeksjoner har lest`() = medDatabase { db ->
        val soknadId = UUID.randomUUID()
        transaction(db) {
            settInnSoknad(soknadId)
            settInnEvent(5, soknadId.toString(), ellevaarSiden)
            settProjeksjonsposisjon(10)
            sql("INSERT INTO projection_builder_state (builder_name, position) VALUES ('treg', 4)")
        }

        assertEquals(0, AggregateRetention(db).slettGamleAggregater(clock))
        transaction(db) { assertEquals(1, antall("event_log")) }
    }

    @Test
    fun `beholder aggregat med uferdig OeBS-melding`() = medDatabase { db ->
        val soknadId = UUID.randomUUID()
        transaction(db) {
            settInnSoknad(soknadId)
            settInnSakMedVedtak(soknadId, arenaSakId = "2025/9")
            settInnEvent(1, soknadId.toString(), ellevaarSiden)
            settProjeksjonsposisjon(10)
            sql("INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json) VALUES ('E-2025/9-1', 'BESTILLING', '{}')")
        }

        assertEquals(0, AggregateRetention(db).slettGamleAggregater(clock))
        transaction(db) { assertEquals(1, antall("soknad")) }
    }

    @Test
    fun `arbeidsgiver ser ikke soknad eller tilsagn etter fristen`() = medDatabase { db ->
        val aktiv = UUID.randomUUID()
        val utlopt = UUID.randomUUID()
        transaction(db) {
            settInnSoknad(aktiv, arbeidsgiverTilgangTil = "2036-06-16 12:00:00")
            settInnSoknad(utlopt, arbeidsgiverTilgangTil = "2036-06-14 12:00:00")
            val tilsagn = TestEventData.sampleTilsagnData
            insertTilsagndata(utlopt, tilsagn.copy(tilsagnNummer = TilsagnData.TilsagnNummer(2036, 1, 1)))
            // Tilsagn uten søknad (legacy Altinn-sporet): fristen regnes fra siste event på tilsagnsnummeret.
            insertTilsagndata(null, tilsagn.copy(tilsagnNummer = TilsagnData.TilsagnNummer(2036, 2, 1)))
            settInnEvent(1, "2036:2:1", "2035-03-01 00:00:00")
            insertTilsagndata(null, tilsagn.copy(tilsagnNummer = TilsagnData.TilsagnNummer(2036, 3, 1)))
            settInnEvent(2, "2036:3:1", "2036-06-01 00:00:00")
        }

        transaction(db) {
            assertNotNull(findSoknadById(aktiv, now = now))
            assertNull(findSoknadById(utlopt, now = now))
            assertNull(findSoknadOrUtkastById(utlopt, now = now))
            assertNull(findTilsagnDataByTilsagnNummer("2036:1:1", now = now))
            assertNull(findTilsagnDataByTilsagnNummer("2036:2:1", now = now))
            assertNotNull(findTilsagnDataByTilsagnNummer("2036:3:1", now = now))
            // Saksbehandler-oppslaget filtrerer ikke på arbeidsgivers frist.
            assertNotNull(findSoknadById(utlopt, filterForArbeidsgiver = false))
            assertNotNull(findTilsagnDataByTilsagnNummer("2036:2:1", filterForArbeidsgiver = false))
        }
    }

    private fun medDatabase(block: (Database) -> Unit) =
        TestDatabase.isolert().use { block(it.config.jdbcDatabase) }

    private fun JdbcTransaction.settInnSoknad(
        id: UUID,
        arbeidsgiverTilgangTil: String = "2026-01-01 00:00:00",
    ) = sql(
        """
        INSERT INTO soknad (id, virksomhetsnummer, virksomhetsnavn, kontaktperson_navn, kontaktperson_epost,
            kontaktperson_telefon, ansatt_fnr, ansatt_navn, ekspert_navn, ekspert_virksomhet, ekspert_kompetanse,
            behov_for_bistand_begrunnelse, behov_for_bistand, behov_for_bistand_estimert_kostnad,
            behov_for_bistand_timer, behov_for_bistand_tilrettelegging, behov_for_bistand_startdato,
            nav_kontaktperson, opprettet_av, status, arbeidsgiver_tilgang_til)
        VALUES (?::uuid, '1337', 'AS', '', '', '', '', '', '', '', '', '', '', '', '', '', '2025-01-01', '', '42',
            'godkjent', ?::timestamp)
        """.trimIndent(),
        id, arbeidsgiverTilgangTil,
    )

    private fun JdbcTransaction.settInnSakMedVedtak(soknadId: UUID, arenaSakId: String) {
        sql("INSERT INTO sak (soknad_id, arena_sak_id) VALUES (?::uuid, ?)", soknadId, arenaSakId)
        sql(
            """
            INSERT INTO forelopig_vedtak (sak_id, versjon, event_id, utfall, begrunnelse_deltaker,
                begrunnelse_arbeidsgiver, innstilt_av_ident, sendt_tidspunkt)
            SELECT sak_id, 1, 1001, 'AVSLAA', 'b', 'b', 'Z1', now() FROM sak WHERE soknad_id = ?::uuid
            """.trimIndent(),
            soknadId,
        )
        sql(
            """
            INSERT INTO vedtak (sak_id, forelopig_vedtak_id, utfall, fattet_av_ident, fattet_av_navn,
                fattet_tidspunkt, event_id)
            SELECT f.sak_id, f.id, 'AVSLATT', 'Z2', 'N', now(), 1002
            FROM forelopig_vedtak f JOIN sak s ON s.sak_id = f.sak_id WHERE s.soknad_id = ?::uuid
            """.trimIndent(),
            soknadId,
        )
    }

    private fun JdbcTransaction.settInnEvent(id: Long, aggregateRootId: String, createdAt: String) = sql(
        """
        INSERT INTO event_log (id, event_json, status, created_at, updated_at, aggregate_root_id)
        VALUES ($id, '{}', 0, ?::timestamp, ?::timestamp, ?)
        """.trimIndent(),
        createdAt, createdAt, aggregateRootId,
    )

    private fun JdbcTransaction.settProjeksjonsposisjon(position: Long) =
        sql("INSERT INTO projection_builder_state (builder_name, position) VALUES ('test', $position)")

    private fun JdbcTransaction.antall(tabell: String): Long =
        exec("SELECT count(*) FROM $tabell") { rs -> rs.next(); rs.getLong(1) } ?: 0

    private fun JdbcTransaction.sql(sql: String, vararg args: Any) {
        exec(sql, args.map { (TextColumnType() as IColumnType<*>) to it.toString() })
    }
}
