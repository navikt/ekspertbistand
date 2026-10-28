package no.nav.ekspertbistand

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.ReturArsak
import no.nav.ekspertbistand.saksbehandling.Vilkar
import no.nav.ekspertbistand.saksbehandling.VilkarsvurderingRequest
import no.nav.ekspertbistand.saksbehandling.hentSakIdForSoknad
import no.nav.ekspertbistand.saksbehandling.saksstatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.tilSoknadDTO
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Fiktive virksomheter for lokal kjøring. Ereg-, Norg- og geografi-mockene i [main] slår opp her. */
internal data class LokalVirksomhet(
    val orgnr: String,
    val navn: String,
    val adresse: String,
    val postnummer: String,
    val poststed: String,
    val kommunenummer: String,
    val kommunenavn: String,
    val enhet: LokalEnhet = LokalEnhet.SYDPOLEN,
)

internal enum class LokalEnhet(val nummer: String, val navn: String) {
    SYDPOLEN("1234", "Nav arbeidslivssenter Sydpolen"),
    NORDPOLEN("5678", "Nav arbeidslivssenter Nordpolen"),
    VIKAFOSSEN("2103", "Nav Vikafossen"),
}

/** Beskyttelse på deltakeren. PDL- og Tilgangsmaskin-mockene i [main] slår opp her på fnr. */
internal enum class LokalBeskyttelse(val gradering: String?, val avvisningskode: String, val begrunnelse: String) {
    STRENGT_FORTROLIG(
        "STRENGT_FORTROLIG",
        "AVVIST_STRENGT_FORTROLIG_ADRESSE",
        "Du har ikke tilgang til brukere med strengt fortrolig adresse",
    ),
    FORTROLIG("FORTROLIG", "AVVIST_FORTROLIG_ADRESSE", "Du har ikke tilgang til brukere med fortrolig adresse"),
    SKJERMET(null, "AVVIST_SKJERMING", "Du har ikke tilgang til Nav-ansatte og andre skjermede brukere"),
}

internal val lokaleVirksomheter = listOf(
    LokalVirksomhet("123456780", "Eksempel Bedrift AS Avd. Oslo", "Testveien 1", "0557", "Oslo", "0301", "Oslo"),
    LokalVirksomhet(
        "310000001",
        "Bryggen Bølgeblikk AS",
        "Bryggen 1",
        "5003",
        "Bergen",
        "4601",
        "Bergen",
        LokalEnhet.NORDPOLEN
    ),
    LokalVirksomhet("310000002", "Nidaros Nagleverk AS", "Munkegata 2", "7011", "Trondheim", "5001", "Trondheim"),
    LokalVirksomhet("310000003", "Stavanger Sveis AS", "Kirkegata 3", "4006", "Stavanger", "1103", "Stavanger"),
    LokalVirksomhet(
        "310000004",
        "Ishavsbyen Isolasjon AS",
        "Storgata 4",
        "9008",
        "Tromsø",
        "5501",
        "Tromsø",
        LokalEnhet.NORDPOLEN
    ),
    LokalVirksomhet("310000005", "Lillestrøm Lager AS", "Torget 5", "2000", "Lillestrøm", "3205", "Lillestrøm"),
    LokalVirksomhet("310000006", "Sandvika Snekkeri AS", "Rådhustorget 6", "1338", "Sandvika", "3201", "Bærum"),
    LokalVirksomhet(
        "310000007",
        "Sørlandet Seilmakeri AS",
        "Markens gate 7",
        "4611",
        "Kristiansand",
        "4204",
        "Kristiansand"
    ),
    LokalVirksomhet("310000008", "Nav Lokalkontor Drammen", "Bragernes torg 8", "3017", "Drammen", "3301", "Drammen"),
)

internal fun lokalVirksomhet(orgnr: String) =
    lokaleVirksomheter.find { it.orgnr == orgnr } ?: lokaleVirksomheter.first()

internal fun lokalEnhetForKommune(kommunenummer: String?) =
    lokaleVirksomheter.find { it.kommunenummer == kommunenummer }?.enhet ?: LokalEnhet.SYDPOLEN

private enum class Beslutning { GODKJENN, RETURNER }

/**
 * Søknad som drives fram med de samme eventene som saksbehandlingen publiserer. Uten [saksbehandler] blir
 * saken stående ledig, uten [forslag] under behandling, og uten [beslutning] til beslutning.
 */
private data class LokalSak(
    val soknadId: UUID,
    val virksomhet: LokalVirksomhet,
    val ansattNavn: String,
    val ansattFnr: String,
    val estimertKostnad: Int,
    val saksbehandler: LokalAnsatt? = null,
    val forslag: ForelopigUtfall? = null,
    val beslutter: LokalAnsatt? = null,
    val beslutning: Beslutning? = null,
    val beskyttelse: LokalBeskyttelse? = null,
)

private val ola = lokaleAnsatte.single { it.navIdent == "A123456" }
private val kari = lokaleAnsatte.single { it.navIdent == "B654321" }
private val per = lokaleAnsatte.single { it.navIdent == "P123456" }

private val lokaleSaker = listOf(
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000001"),
        virksomhet = lokaleVirksomheter[1],
        ansattNavn = "Bjørg Bølge",
        ansattFnr = "01019012301",
        estimertKostnad = 18000,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000002"),
        virksomhet = lokaleVirksomheter[2],
        ansattNavn = "Nils Nagle",
        ansattFnr = "01019012302",
        estimertKostnad = 22000,
        saksbehandler = per,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000003"),
        virksomhet = lokaleVirksomheter[3],
        ansattNavn = "Siri Sveis",
        ansattFnr = "01019012303",
        estimertKostnad = 15000,
        saksbehandler = kari,
        forslag = ForelopigUtfall.INNVILGE,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000004"),
        virksomhet = lokaleVirksomheter[4],
        ansattNavn = "Isak Isolasjon",
        ansattFnr = "01019012304",
        estimertKostnad = 30000,
        saksbehandler = ola,
        forslag = ForelopigUtfall.AVSLAA,
        beslutter = kari,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000005"),
        virksomhet = lokaleVirksomheter[5],
        ansattNavn = "Live Lager",
        ansattFnr = "01019012305",
        estimertKostnad = 12000,
        saksbehandler = ola,
        forslag = ForelopigUtfall.INNVILGE,
        beslutter = kari,
        beslutning = Beslutning.GODKJENN,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000006"),
        virksomhet = lokaleVirksomheter[6],
        ansattNavn = "Snorre Snekker",
        ansattFnr = "01019012306",
        estimertKostnad = 40000,
        saksbehandler = kari,
        forslag = ForelopigUtfall.AVSLAA,
        beslutter = ola,
        beslutning = Beslutning.GODKJENN,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000007"),
        virksomhet = lokaleVirksomheter[2],
        ansattNavn = "Trine Tre",
        ansattFnr = "01019012307",
        estimertKostnad = 16000,
        saksbehandler = ola,
        forslag = ForelopigUtfall.INNVILGE,
        beslutter = kari,
        beslutning = Beslutning.RETURNER,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000008"),
        virksomhet = lokaleVirksomheter[0],
        ansattNavn = "Oskar Oslo",
        ansattFnr = "01019012308",
        estimertKostnad = 9000,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000009"),
        virksomhet = lokaleVirksomheter[3],
        ansattNavn = "Stine Strengt",
        ansattFnr = "01019012309",
        estimertKostnad = 14000,
        beskyttelse = LokalBeskyttelse.STRENGT_FORTROLIG,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000010"),
        virksomhet = lokaleVirksomheter[7],
        ansattNavn = "Frode Fortrolig",
        ansattFnr = "01019012310",
        estimertKostnad = 19000,
        saksbehandler = kari,
        beskyttelse = LokalBeskyttelse.FORTROLIG,
    ),
    LokalSak(
        soknadId = UUID.fromString("a1000000-0000-4000-8000-000000000011"),
        virksomhet = lokaleVirksomheter[8],
        ansattNavn = "Sigrid Skjermet",
        ansattFnr = "01019012311",
        estimertKostnad = 11000,
        beskyttelse = LokalBeskyttelse.SKJERMET,
    ),
)

internal fun lokalBeskyttelse(fnr: String?) = lokaleSaker.find { it.ansattFnr == fnr }?.beskyttelse

internal fun lagreLokaleSoknader(database: Database) {
    transaction(database) {
        lokaleSaker.forEach { sak ->
            SoknadTable.insertReturning {
                it[id] = sak.soknadId
                it[virksomhetsnummer] = sak.virksomhet.orgnr
                it[virksomhetsnavn] = sak.virksomhet.navn
                it[beliggenhetsadresse] = with(sak.virksomhet) { "$adresse, $postnummer $poststed" }
                it[opprettetAv] = "42"

                it[kontaktpersonNavn] = "Kontaktperson NN"
                it[kontaktpersonEpost] = "kontaktperson@bedrift.no"
                it[kontaktpersonTelefon] = "415199999"
                it[ansattFnr] = sak.ansattFnr
                it[ansattNavn] = sak.ansattNavn
                it[ekspertNavn] = "Ekspert NN"
                it[ekspertVirksomhet] = "ErgoConsult AS"
                it[ekspertKompetanse] = "Ergoterapeut, autorisasjon HPR 1337"
                it[behovForBistand] = "Arbeidsplassvurdering og ergonomisk veiledning"
                it[behovForBistandBegrunnelse] = "Langvarig skulderplage med 50% sykefravær"
                it[behovForBistandEstimertKostnad] = sak.estimertKostnad.toString()
                it[behovForBistandTimer] = "16"
                it[behovForBistandTilrettelegging] = "Høydejustert bord testet, noe bedring"
                it[behovForBistandStartdato] = CurrentDate

                it[navKontaktPerson] = "Navkontaktperson NN"
                it[status] = "innsendt"
            }.single().tilSoknadDTO().also {
                publishEventQueue(EventData.SoknadInnsendt(it))
            }
        }
    }
}

/**
 * Venter til saken er opprettet av eventkjeden fra [lagreLokaleSoknader], og publiserer så eventene som
 * fører den fram. Køen behandles i rekkefølge, så eventene for én sak kan publiseres samlet.
 */
@OptIn(ExperimentalTime::class)
internal fun CoroutineScope.behandleLokaleSaker(database: Database) = launch {
    val log = logger()
    lokaleSaker.filter { it.saksbehandler != null }.forEach { sak ->
        val sakId = withTimeoutOrNull(60.seconds) {
            var sakId: UUID? = null
            while (sakId == null) {
                sakId = transaction(database) { hentSakIdForSoknad(sak.soknadId) }
                if (sakId == null) delay(200)
            }
            sakId
        }
        if (sakId == null) {
            log.warn("Fant ikke sak for lokal søknad {}, behandles ikke", sak.soknadId)
            return@forEach
        }
        transaction(database) {
            behandlingseventer(sak, sakId.toString()).forEach { publishEventQueue(it) }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun behandlingseventer(sak: LokalSak, sakId: String): List<EventData> {
    val saksbehandler = sak.saksbehandler ?: return emptyList()
    val soknadId = sak.soknadId.toString()
    var tidspunkt: Instant = Clock.System.now() - 2.hours
    fun neste() = tidspunkt.also { tidspunkt += 1.minutes }

    val eventer = mutableListOf<EventData>(
        EventData.SakTildeltSaksbehandler(sakId, soknadId, saksbehandler.navIdent, saksbehandler.navn, neste()),
    )
    val forslag = sak.forslag ?: return eventer

    eventer += Vilkar.entries.map { vilkar ->
        val godkjent = forslag != ForelopigUtfall.AVSLAA || vilkar != Vilkar.ARBEIDSGIVER_HAR_PROVD_TILRETTELEGGING
        EventData.VilkarsvurderingOppdatert(
            sakId = sakId,
            soknadId = soknadId,
            vurdering = VilkarsvurderingRequest(
                vilkar,
                godkjent,
                if (godkjent) null else "Ingen tilrettelegging er prøvd"
            ),
            vurdertAvIdent = saksbehandler.navIdent,
            tidspunkt = neste(),
        )
    }
    val idag = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val innvilget = forslag != ForelopigUtfall.AVSLAA
    eventer += EventData.ForelopigVedtakSendtTilBeslutter(
        sakId = sakId,
        soknadId = soknadId,
        utfall = forslag,
        innvilgetBelop = sak.estimertKostnad.takeIf { innvilget },
        tiltaksperiodeFra = idag.takeIf { innvilget },
        tiltaksperiodeTil = LocalDate(idag.year, 12, 31).takeIf { innvilget },
        begrunnelseDeltaker = if (innvilget) "Vilkårene er oppfylt" else "Arbeidsgiver har ikke prøvd tilrettelegging",
        begrunnelseArbeidsgiver = if (innvilget) null else "Prøv tilrettelegging først, og søk på nytt om behovet består",
        innstiltAvIdent = saksbehandler.navIdent,
        tidspunkt = neste(),
    )
    val beslutter = sak.beslutter ?: return eventer

    eventer += EventData.SakTildeltBeslutter(sakId, soknadId, beslutter.navIdent, beslutter.navn, neste())
    eventer += when (sak.beslutning ?: return eventer) {
        Beslutning.GODKJENN -> EventData.VedtakFattet(
            sakId = sakId,
            soknadId = soknadId,
            forelopigVedtakVersjon = 1,
            utfall = forslag.saksstatus,
            fattetAvIdent = beslutter.navIdent,
            fattetAvNavn = beslutter.navn,
            tidspunkt = neste(),
        )

        Beslutning.RETURNER -> EventData.ForelopigVedtakReturnert(
            sakId = sakId,
            soknadId = soknadId,
            forelopigVedtakVersjon = 1,
            aarsaker = setOf(ReturArsak.FEIL_FAKTA),
            forklaring = "Tiltaksperioden stemmer ikke med søknaden",
            returnertAvIdent = beslutter.navIdent,
            returnertAvNavn = beslutter.navn,
            tidspunkt = neste(),
        )
    }
    return eventer
}
