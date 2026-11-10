package no.nav.ekspertbistand.refusjon

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.domain.VedleggDomain
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreFattetVedtak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.vedlegg.SluttrapportTable
import no.nav.ekspertbistand.vedlegg.VedleggDb
import no.nav.ekspertbistand.vedlegg.VedleggTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class InnsendingHendelserTest {
    private lateinit var testDb: TestDatabase
    private val db get() = testDb.config.jdbcDatabase

    @BeforeTest
    fun setup() {
        testDb = TestDatabase.isolert()
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `lagring av refusjonskrav publiserer hendelse og saksloggoppdatering`() {
        val sak = godkjentSoknad()
        val filer = listOf(
            RefusjonsfilInput("kvittering.pdf", "PDF-kvittering".toByteArray()),
            RefusjonsfilInput("reise.pdf", "PDF-reise".toByteArray()),
        )
        val refusjonskravId = RefusjonDb(db).lagreRefusjonskrav(
            soknadId = sak.soknadId,
            virksomhetsnummer = VIRKSOMHETSNUMMER,
            innsendtAv = INNSENDT_AV,
            belopOre = BELOP_ORE,
            filer = filer,
        )

        val (kravIdFraTabell, opprettet, vedlegg) = transaction(db) {
            val krav = RefusjonskravTable.selectAll().single()
            val vedlegg = VedleggTable.selectAll().toList().map {
                VedleggDomain(
                    vedleggId = it[VedleggTable.id].value.toString(),
                    filnavn = it[VedleggTable.filnavn],
                    storrelse = it[VedleggTable.storrelse],
                )
            }
            Triple(krav[RefusjonskravTable.id].value, krav[RefusjonskravTable.opprettet], vedlegg)
        }
        assertEquals(kravIdFraTabell, refusjonskravId)
        val (events, aggregateRootIds) = hentHendelser()
        val refusjonskravMottatt = assertIs<EventData.RefusjonskravMottatt>(
            events.filterIsInstance<EventData.RefusjonskravMottatt>().single(),
        )
        assertEquals(refusjonskravId.toString(), refusjonskravMottatt.refusjonskravId)
        assertEquals(sak.sakId.toString(), refusjonskravMottatt.sakId)
        assertEquals(sak.soknadId.toString(), refusjonskravMottatt.soknadId)
        assertEquals(VIRKSOMHETSNUMMER, refusjonskravMottatt.virksomhetsnummer)
        assertEquals(INNSENDT_AV, refusjonskravMottatt.innsendtAv)
        assertEquals(BELOP_ORE, refusjonskravMottatt.belopOre)
        assertEquals(INNVILGET_BELOP_KRONER, refusjonskravMottatt.innvilgetBelopKroner)
        assertEquals(vedlegg.toSet(), refusjonskravMottatt.vedlegg.toSet())
        assertEquals(opprettet, refusjonskravMottatt.tidspunkt)

        val sakOppdatert = events.filterIsInstance<EventData.SakOppdatert>().single()
        assertEquals(sak.sakId.toString(), sakOppdatert.sakId)
        assertEquals(sak.soknadId.toString(), sakOppdatert.soknadId)
        assertEquals(AktorRolle.SYSTEM, sakOppdatert.utfortAvRolle)
        assertEquals(null, sakOppdatert.utfortAvIdent)
        assertEquals("Refusjonskrav mottatt fra arbeidsgiver", sakOppdatert.notat)
        assertEquals(opprettet, sakOppdatert.tidspunkt)
        assertEquals(listOf(sak.soknadId.toString(), sak.soknadId.toString()), aggregateRootIds)
    }

    @Test
    fun `hver sluttrapport publiserer hendelse og saksloggoppdatering`() {
        val sak = godkjentSoknad()
        val vedleggDb = VedleggDb(db)
        val vedleggId1 = vedleggDb.lagreSluttrapport(
            soknadId = sak.soknadId,
            virksomhetsnummer = VIRKSOMHETSNUMMER,
            innsendtAv = INNSENDT_AV,
            filnavn = "sluttrapport-1.pdf",
            innhold = "PDF-1".toByteArray(),
        )
        val vedleggId2 = vedleggDb.lagreSluttrapport(
            soknadId = sak.soknadId,
            virksomhetsnummer = VIRKSOMHETSNUMMER,
            innsendtAv = INNSENDT_AV,
            filnavn = "sluttrapport-2.pdf",
            innhold = "PDF-2".toByteArray(),
        )

        val (reports, attachments) = transaction(db) {
            val reports = SluttrapportTable.selectAll().toList()
            val attachments = VedleggTable.selectAll().toList().associate {
                it[VedleggTable.sluttrapportId]!! to VedleggDomain(
                    vedleggId = it[VedleggTable.id].value.toString(),
                    filnavn = it[VedleggTable.filnavn],
                    storrelse = it[VedleggTable.storrelse],
                )
            }
            reports to attachments
        }
        val (events, aggregateRootIds) = hentHendelser()
        val reportsReceived = events.filterIsInstance<EventData.SluttrapportMottatt>()
        assertEquals(2, reportsReceived.size)
        assertEquals(
            reports.map { it[SluttrapportTable.id].toString() }.toSet(),
            reportsReceived.map { it.sluttrapportId }.toSet(),
        )
        assertEquals(
            setOf(vedleggId1.toString(), vedleggId2.toString()),
            attachments.values.map { it.vedleggId }.toSet(),
        )
        reportsReceived.forEach { event ->
            assertEquals(sak.sakId.toString(), event.sakId)
            assertEquals(sak.soknadId.toString(), event.soknadId)
            assertEquals(VIRKSOMHETSNUMMER, event.virksomhetsnummer)
            assertEquals(INNSENDT_AV, event.innsendtAv)

            val report = reports.single { it[SluttrapportTable.id].toString() == event.sluttrapportId }
            assertEquals(report[SluttrapportTable.opprettet], event.tidspunkt)
            assertEquals(attachments.getValue(report[SluttrapportTable.id]), event.vedlegg)
        }
        val sakOppdatert = events.filterIsInstance<EventData.SakOppdatert>()
        assertEquals(2, sakOppdatert.size)
        sakOppdatert.forEach { event ->
            assertEquals(sak.sakId.toString(), event.sakId)
            assertEquals(sak.soknadId.toString(), event.soknadId)
            assertEquals(AktorRolle.SYSTEM, event.utfortAvRolle)
            assertEquals(null, event.utfortAvIdent)
            assertEquals("Sluttrapport mottatt fra arbeidsgiver", event.notat)
            assertTrue(reports.any { it[SluttrapportTable.opprettet] == event.tidspunkt })
        }
        assertEquals(
            reports.map { it[SluttrapportTable.opprettet] }.toSet(),
            sakOppdatert.map { it.tidspunkt }.toSet(),
        )
        assertEquals(4, events.size)
        assertEquals(List(4) { sak.soknadId.toString() }, aggregateRootIds)
    }

    @Test
    fun `avvist refusjonskrav oppretter ingen rad eller hendelser`() {
        val sak = godkjentSoknad()
        val refusjonDb = RefusjonDb(db)
        assertFailsWith<BelopOverInnvilgetException> {
            refusjonDb.lagreRefusjonskrav(
                soknadId = sak.soknadId,
                virksomhetsnummer = VIRKSOMHETSNUMMER,
                innsendtAv = INNSENDT_AV,
                belopOre = INNVILGET_BELOP_KRONER.toLong() * 100 + 1,
                filer = listOf(RefusjonsfilInput("for-hoyt.pdf", "PDF".toByteArray())),
            )
        }
        transaction(db) {
            assertTrue(RefusjonskravTable.selectAll().empty())
            assertTrue(VedleggTable.selectAll().empty())
            assertTrue(QueuedEvents.selectAll().empty())
        }

        refusjonDb.lagreRefusjonskrav(
            soknadId = sak.soknadId,
            virksomhetsnummer = VIRKSOMHETSNUMMER,
            innsendtAv = INNSENDT_AV,
            belopOre = 100_00,
            filer = listOf(RefusjonsfilInput("gyldig.pdf", "PDF".toByteArray())),
        )
        assertFailsWith<RefusjonskravFinnesAlleredeException> {
            refusjonDb.lagreRefusjonskrav(
                soknadId = sak.soknadId,
                virksomhetsnummer = VIRKSOMHETSNUMMER,
                innsendtAv = INNSENDT_AV,
                belopOre = 200_00,
                filer = listOf(RefusjonsfilInput("duplikat.pdf", "PDF".toByteArray())),
            )
        }
        transaction(db) {
            assertEquals(1, RefusjonskravTable.selectAll().count())
            assertEquals(1, VedleggTable.selectAll().count())
            assertEquals(2, QueuedEvents.selectAll().count())
        }
    }

    @Test
    fun `innsendinghendelser kan serialiseres og deserialiseres med typefelt`() {
        val tidspunkt = kotlin.time.Instant.parse("2026-11-10T12:00:00Z")
        val vedlegg = VedleggDomain(
            vedleggId = "vedlegg-id",
            filnavn = "innsending.pdf",
            storrelse = 123,
        )
        val refusjonskrav = EventData.RefusjonskravMottatt(
            refusjonskravId = "refusjonskrav-id",
            sakId = "sak-id",
            soknadId = "soknad-id",
            virksomhetsnummer = VIRKSOMHETSNUMMER,
            innsendtAv = INNSENDT_AV,
            belopOre = 12_345,
            innvilgetBelopKroner = INNVILGET_BELOP_KRONER,
            vedlegg = listOf(vedlegg),
            tidspunkt = tidspunkt,
        )
        val sluttrapport = EventData.SluttrapportMottatt(
            sluttrapportId = "sluttrapport-id",
            sakId = "sak-id",
            soknadId = "soknad-id",
            virksomhetsnummer = VIRKSOMHETSNUMMER,
            innsendtAv = INNSENDT_AV,
            vedlegg = vedlegg,
            tidspunkt = tidspunkt,
        )

        listOf(
            refusjonskrav to "refusjonskravMottatt",
            sluttrapport to "sluttrapportMottatt",
        ).forEach { (event, type) ->
            val json = Json.encodeToString(EventData.serializer(), event)
            assertTrue(json.contains("\"type\":\"$type\""))
            assertEquals(event, Json.decodeFromString(EventData.serializer(), json))
        }
    }

    private fun godkjentSoknad(): LagretSak {
        val sak = lagreSoknadOgSak(db)
        transaction(db) {
            SoknadTable.update({ SoknadTable.id eq sak.soknadId }) {
                it[status] = "godkjent"
            }
            SakTable.update({ SakTable.sakId eq sak.sakId }) {
                it[SakTable.status] = Saksstatus.OPPRETTET.name
            }
            lagreFattetVedtak(sak.sakId, ForelopigUtfall.INNVILGE, INNVILGET_BELOP_KRONER)
        }
        return sak
    }

    private fun hentHendelser(): Pair<List<EventData>, List<String>> = transaction(db) {
        val rows = QueuedEvents.selectAll().toList()
        rows.map { it[QueuedEvents.eventData] } to rows.map { it[QueuedEvents.aggregateRootId] }
    }

    private companion object {
        const val VIRKSOMHETSNUMMER = "987654321"
        const val INNSENDT_AV = "01017012345"
        const val INNVILGET_BELOP_KRONER = 24_900
        const val BELOP_ORE = 1_234_567L
    }
}
