package no.nav.ekspertbistand.statistikk

import com.google.cloud.bigquery.Field
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.event.projections.ProjectionBuilderState
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.ReturArsak
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.soknad.DTO
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SaksbehandlingsstatistikkProjectionTest {

    private lateinit var testDb: TestDatabase
    private lateinit var projection: SaksbehandlingsstatistikkProjection
    private val rader = mutableListOf<SaksbehandlingsstatistikkRad>()
    private val skjermede = mutableSetOf<String>()
    private val registrerte = mutableListOf<String>()
    private var pdlFeil: Exception? = null
    private val start = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
    private val saksbehandler = "Z111111"
    private val beslutter = "Z222222"
    private val periodeFra = LocalDate(2026, 11, 1)
    private val periodeTil = LocalDate(2027, 1, 31)

    @BeforeTest
    fun setup() {
        testDb = TestDatabase.isolert()
        projection = SaksbehandlingsstatistikkProjection(
            database = testDb.config.jdbcDatabase,
            skriver = rader::add,
            adressebeskyttelse = { fnr ->
                pdlFeil?.let { throw it }
                fnr in skjermede
            },
            skjermede = object : SkjermedeBehandlinger {
                override fun registrer(behandlingIder: Collection<String>) {
                    registrerte += behandlingIder
                }

                override fun uskjermede() = emptyMap<String, String>()
            },
        )
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `livslop med retur fra beslutter gir en melding per relevant hendelse`() = medDb {
        val soknad = soknad()
        registrer(soknad, enhet = "0315")
        publishAndFinalize(tildeltSaksbehandler(soknad, saksbehandler, tid(1)))
        publishAndFinalize(forelopigVedtak(soknad, ForelopigUtfall.INNVILGE_REDUSERT, tid(2), belop = 5000))
        publishAndFinalize(tildeltBeslutter(soknad, beslutter, tid(3)))
        publishAndFinalize(retur(soknad, versjon = 1, tid(4)))
        publishAndFinalize(forelopigVedtak(soknad, ForelopigUtfall.INNVILGE_REDUSERT, tid(5), belop = 6000))
        publishAndFinalize(vedtak(soknad, versjon = 2, Saksstatus.INNVILGET, beslutter, tid(6)))
        pollAlt()

        val meldinger = meldinger(soknad)
        assertEquals(
            listOf(
                listOf("REGISTRERT", null, null, "AUTOMATISK", null, null),
                listOf("UNDER_BEHANDLING", null, null, "MANUELL", saksbehandler, null),
                listOf("TIL_BESLUTNING", "DELVIS_INNVILGET", null, "MANUELL", saksbehandler, null),
                listOf("TIL_BESLUTNING", "DELVIS_INNVILGET", null, "MANUELL", saksbehandler, beslutter),
                listOf("UNDERKJENT_BESLUTTER", null, "FEIL_FAKTUM,FEIL_LOVANVENDELSE", "MANUELL", saksbehandler, beslutter),
                listOf("TIL_BESLUTNING", "DELVIS_INNVILGET", null, "MANUELL", saksbehandler, beslutter),
                listOf("AVSLUTTET", "DELVIS_INNVILGET", null, "MANUELL", saksbehandler, beslutter),
            ),
            meldinger.map {
                listOf(
                    it.behandlingStatus, it.behandlingResultat, it.resultatBegrunnelse,
                    it.behandlingMetode, it.saksbehandler, it.ansvarligBeslutter,
                )
            },
        )

        val forste = meldinger.first()
        assertEquals(soknad.id, forste.behandlingId)
        assertEquals(soknad.ansatt.fnr, forste.fnr)
        assertEquals("0315", forste.ansvarligEnhet)
        assertEquals(soknad.behovForBistand.startdato, forste.forventetOppstartTid)
        assertTrue(forste.mottattTid <= forste.registrertTid)
        assertEquals(forste.registrertTid, forste.hendelseTid)
        assertEquals(tid(6), meldinger.last().ferdigbehandletTid)
        assertEquals(meldinger.map { it.sekvensnummer }.sorted(), meldinger.map { it.sekvensnummer })
        assertEquals(1, meldinger.map { it.sakId }.distinct().size)

        assertEquals("EKSPEBIST", forste.tiltakskodeArena)
        assertEquals(soknad.virksomhet.virksomhetsnummer, forste.arrangorOrgnr)
        assertEquals(12, forste.estimertAntallTimer)
        assertEquals(9000, forste.estimertKostnad)
        assertEquals(
            listOf(null, null, 5000, 5000, null, 6000, 6000),
            meldinger.map { it.innvilgetBelop },
        )
        assertEquals(periodeFra, meldinger.last().tiltaksperiodeFra)
        assertEquals(periodeTil, meldinger.last().tiltaksperiodeTil)
        assertEquals(emptyList(), registrerte)
    }

    @Test
    fun `rad til BigQuery passer med skjemaet`() = medDb {
        val soknad = soknad()
        registrer(soknad, enhet = "0315")
        pollAlt()

        val verdier = meldinger(soknad).single().tilBigQuery()
        val felt = skjema.fields.associateBy { it.name }
        assertEquals(emptySet(), verdier.keys - felt.keys)
        assertEquals(
            emptyList(),
            felt.values.filter { it.mode == Field.Mode.REQUIRED && it.name !in verdier }.map { it.name },
        )
        assertEquals(soknad.behovForBistand.startdato.toString(), verdier["forventet_oppstart_tid"])
        assertTrue((verdier["hendelse_tid"] as String).endsWith("Z"))
        assertNull(verdier["saksbehandler"])
    }

    @Test
    fun `ekspertens kompetanse blir koder, og fritekst blir ANNET`() {
        val ekspert = TestEventData.sampleSoknad.ekspert.copy(
            godkjentUtdanningEllerAutorisasjon = listOf("Psykolog", "Egen fritekst", " psykolog ", "Mer fritekst"),
            relevantKompetanse = listOf("Konflikthåndtering", "Omstillingsarbeid (endring i arbeidslivet)"),
        )
        assertEquals("ANNET,PSYKOLOG", ekspert.utdanningKoder)
        assertEquals("KONFLIKTHANDTERING,OMSTILLINGSARBEID", ekspert.kompetanseKoder)

        val eldre = TestEventData.sampleSoknad.ekspert.copy(
            kompetanse = "Ekspertise",
            godkjentUtdanningEllerAutorisasjon = emptyList(),
            relevantKompetanse = emptyList(),
        )
        assertEquals("ANNET", eldre.utdanningKoder)
        assertNull(eldre.kompetanseKoder)
    }

    @Test
    fun `sak uten SakOpprettet, altsaa Arena-sak, gir ingen meldinger`() = medDb {
        val soknad = soknad()
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        publishAndFinalize(journalfoert(soknad, enhet = "0315"))
        publishAndFinalize(tildeltSaksbehandler(soknad, saksbehandler, tid(1)))
        pollAlt()

        assertEquals(0, meldinger(soknad).size)
    }

    @Test
    fun `hendelser handleren avviser gir ingen melding`() = medDb {
        val soknad = soknad()
        registrer(soknad, enhet = "0315")
        publishAndFinalize(tildeltSaksbehandler(soknad, saksbehandler, tid(1)))
        publishAndFinalize(tildeltSaksbehandler(soknad, saksbehandler, tid(2)))
        publishAndFinalize(frigjort(soknad, "Z999999", tid(3)))
        publishAndFinalize(forelopigVedtak(soknad, ForelopigUtfall.INNVILGE, tid(4)))
        publishAndFinalize(tildeltBeslutter(soknad, saksbehandler, tid(5)))
        publishAndFinalize(vedtak(soknad, versjon = 1, Saksstatus.INNVILGET, saksbehandler, tid(6)))
        publishAndFinalize(vedtak(soknad, versjon = 2, Saksstatus.INNVILGET, beslutter, tid(7)))
        pollAlt()

        assertEquals(
            listOf("REGISTRERT", "UNDER_BEHANDLING", "TIL_BESLUTNING"),
            meldinger(soknad).map { it.behandlingStatus },
        )
    }

    @Test
    fun `adressebeskyttelse i PDL skjermer geo-lokaliserende felter uansett enhet`() = medDb {
        val soknad = soknad(fnr = "02020254321").let { it.copy(ekspert = it.ekspert.copy(virksomhetOrgnr = "912345678")) }
        skjermede += soknad.ansatt.fnr
        registrer(soknad, enhet = "0315")
        publishAndFinalize(tildeltSaksbehandler(soknad, saksbehandler, tid(1)))
        pollAlt()

        val siste = meldinger(soknad).last()
        assertEquals(SaksbehandlingsstatistikkProjection.SKJERMET, siste.ansvarligEnhet)
        assertEquals(SaksbehandlingsstatistikkProjection.SKJERMET, siste.saksbehandler)
        assertEquals(SaksbehandlingsstatistikkProjection.SKJERMET, siste.opprettetAv)
        assertEquals(SaksbehandlingsstatistikkProjection.SKJERMET, siste.arrangorOrgnr)
        assertEquals(SaksbehandlingsstatistikkProjection.SKJERMET, siste.ekspertVirksomhetOrgnr)
        assertNull(siste.ansvarligBeslutter)
        assertEquals(soknad.ansatt.fnr, siste.fnr)
        assertEquals(setOf(soknad.id), registrerte.toSet())
    }

    @Test
    fun `adressebeskyttelse etter innsending registrerer behandlingen som skjermet`() = medDb {
        val soknad = soknad(fnr = "03030354321")
        registrer(soknad, enhet = "0315")
        pollAlt()
        skjermede += soknad.ansatt.fnr
        publishAndFinalize(tildeltSaksbehandler(soknad, saksbehandler, tid(1)))
        pollAlt()

        assertEquals(
            listOf("0315", SaksbehandlingsstatistikkProjection.SKJERMET),
            meldinger(soknad).map { it.ansvarligEnhet },
        )
        assertEquals(listOf(soknad.id), registrerte)
    }

    @Test
    fun `feil mot PDL gir ingen rad foer oppslaget lykkes`() {
        val soknad = soknad()
        medDb { registrer(soknad, enhet = "0315") }
        pdlFeil = IllegalStateException("PDL er nede")

        assertFailsWith<Exception> { projection.poll() }
        assertEquals(0, meldinger(soknad).size)

        pdlFeil = null
        pollAlt()
        assertEquals(1, meldinger(soknad).size)
    }

    @Test
    fun `ny levering gir samme rader med samme insertId`() = medDb {
        val soknad = soknad()
        registrer(soknad, enhet = "0315")
        publishAndFinalize(tildeltSaksbehandler(soknad, saksbehandler, tid(1)))
        pollAlt()
        val forste = meldinger(soknad)

        ProjectionBuilderState.update({ ProjectionBuilderState.builderName eq projection.name }) {
            it[position] = 0
        }
        pollAlt()
        val andre = meldinger(soknad).drop(forste.size)

        assertEquals(2, forste.size)
        assertEquals(forste.map { it.insertId }, andre.map { it.insertId })
        assertEquals(forste.map { it.copy(meldingTid = start) }, andre.map { it.copy(meldingTid = start) })
    }

    private fun JdbcTransaction.registrer(soknad: DTO.Soknad, enhet: String) {
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        publishAndFinalize(journalfoert(soknad, enhet))
        publishAndFinalize(EventData.SakOpprettet(sakId = UUID.randomUUID().toString(), soknad = soknad))
    }

    private fun medDb(block: JdbcTransaction.() -> Unit) {
        transaction(testDb.config.jdbcDatabase) { block() }
    }

    private fun pollAlt() {
        while (projection.poll()) Unit
    }

    private fun meldinger(soknad: DTO.Soknad) = rader.filter { it.behandlingId == soknad.id }

    private fun tid(minutter: Int): Instant = start + minutter.minutes

    private fun soknad(fnr: String = TestEventData.sampleSoknad.ansatt.fnr) = TestEventData.sampleSoknad.copy(
        id = UUID.randomUUID().toString(),
        ansatt = TestEventData.sampleSoknad.ansatt.copy(fnr = fnr),
    )

    private fun journalfoert(soknad: DTO.Soknad, enhet: String) = EventData.InnsendtSoknadJournalfoert(
        soknad = soknad,
        dokumentId = 1,
        journaldpostId = 2,
        behandlendeEnhetId = enhet,
    )

    private fun tildeltSaksbehandler(soknad: DTO.Soknad, ident: String, tidspunkt: Instant) =
        EventData.SakTildeltSaksbehandler(
            sakId = UUID.randomUUID().toString(),
            soknadId = soknad.id!!,
            saksbehandlerIdent = ident,
            saksbehandlerNavn = "Navn $ident",
            tidspunkt = tidspunkt,
        )

    private fun frigjort(soknad: DTO.Soknad, ident: String, tidspunkt: Instant) = EventData.SakFrigjort(
        sakId = UUID.randomUUID().toString(),
        soknadId = soknad.id!!,
        saksbehandlerIdent = ident,
        tidspunkt = tidspunkt,
    )

    private fun tildeltBeslutter(soknad: DTO.Soknad, ident: String, tidspunkt: Instant) =
        EventData.SakTildeltBeslutter(
            sakId = UUID.randomUUID().toString(),
            soknadId = soknad.id!!,
            beslutterIdent = ident,
            beslutterNavn = "Navn $ident",
            tidspunkt = tidspunkt,
        )

    private fun forelopigVedtak(
        soknad: DTO.Soknad,
        utfall: ForelopigUtfall,
        tidspunkt: Instant,
        belop: Int? = null,
    ) =
        EventData.ForelopigVedtakSendtTilBeslutter(
            sakId = UUID.randomUUID().toString(),
            soknadId = soknad.id!!,
            utfall = utfall,
            innvilgetBelop = belop,
            tiltaksperiodeFra = belop?.let { periodeFra },
            tiltaksperiodeTil = belop?.let { periodeTil },
            begrunnelseDeltaker = "begrunnelse",
            begrunnelseArbeidsgiver = null,
            innstiltAvIdent = saksbehandler,
            tidspunkt = tidspunkt,
        )

    private fun retur(soknad: DTO.Soknad, versjon: Int, tidspunkt: Instant) = EventData.ForelopigVedtakReturnert(
        sakId = UUID.randomUUID().toString(),
        soknadId = soknad.id!!,
        forelopigVedtakVersjon = versjon,
        aarsaker = setOf(ReturArsak.FEIL_REGELFORSTAELSE, ReturArsak.FEIL_FAKTA),
        forklaring = "forklaring",
        returnertAvIdent = beslutter,
        returnertAvNavn = "Navn $beslutter",
        tidspunkt = tidspunkt,
    )

    private fun vedtak(soknad: DTO.Soknad, versjon: Int, utfall: Saksstatus, ident: String, tidspunkt: Instant) =
        EventData.VedtakFattet(
            sakId = UUID.randomUUID().toString(),
            soknadId = soknad.id!!,
            forelopigVedtakVersjon = versjon,
            utfall = utfall,
            fattetAvIdent = ident,
            fattetAvNavn = "Navn $ident",
            tidspunkt = tidspunkt,
        )
}

private fun JdbcTransaction.publishAndFinalize(event: EventData) =
    publishEventQueue(event).also {
        EventQueue.finalize(it.id)
    }
