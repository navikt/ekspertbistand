package no.nav.ekspertbistand.event

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.arena.TiltakssakEndret
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret
import no.nav.ekspertbistand.event.domain.SakDomain
import no.nav.ekspertbistand.event.domain.SoknadDomainV2
import no.nav.ekspertbistand.event.domain.VedtakDomain
import no.nav.ekspertbistand.event.domain.tilSaksstatus
import no.nav.ekspertbistand.saksbehandling.Adressebeskyttelse
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.ReturArsak
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.Vilkar
import no.nav.ekspertbistand.saksbehandling.VilkarsvurderingRequest
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object TestEventData {

    @OptIn(ExperimentalTime::class)
    val sakOppdatert by lazy {
        EventData.SakOppdatert(
            sakId = UUID.randomUUID().toString(),
            soknadId = sampleSoknad.id!!,
            utfortAvRolle = AktorRolle.SAKSBEHANDLER,
            utfortAvIdent = "Z123456",
            notat = "Sak tildelt",
            tidspunkt = Instant.parse("2026-04-02T08:00:00Z"),
        )
    }

    @OptIn(ExperimentalTime::class)
    val sakTildeltSaksbehandler by lazy {
        EventData.SakTildeltSaksbehandler(
            sakId = UUID.randomUUID().toString(),
            soknadId = sampleSoknad.id!!,
            saksbehandlerIdent = "Z123456",
            saksbehandlerNavn = "Tore Tang",
            tidspunkt = Instant.parse("2026-04-02T08:00:00Z"),
        )
    }

    @OptIn(ExperimentalTime::class)
    val sakTildeltBeslutter by lazy {
        EventData.SakTildeltBeslutter(
            sakId = UUID.randomUUID().toString(),
            soknadId = sampleSoknad.id!!,
            beslutterIdent = "Z654321",
            beslutterNavn = "Berit Beslutter",
            tidspunkt = Instant.parse("2026-04-02T09:00:00Z"),
        )
    }

    @OptIn(ExperimentalTime::class)
    val sakFrigjort by lazy {
        EventData.SakFrigjort(
            sakId = UUID.randomUUID().toString(),
            soknadId = sampleSoknad.id!!,
            saksbehandlerIdent = "Z123456",
            tidspunkt = Instant.parse("2026-04-02T08:30:00Z"),
        )
    }

    @OptIn(ExperimentalTime::class)
    val vilkarsvurderingOppdatert by lazy {
        EventData.VilkarsvurderingOppdatert(
            sakId = UUID.randomUUID().toString(),
            soknadId = sampleSoknad.id!!,
            vurdering = VilkarsvurderingRequest(
                vilkar = Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD,
                godkjent = true,
                notat = "Bekreftet i Aa-registeret",
            ),
            vurdertAvIdent = "Z123456",
            tidspunkt = Instant.parse("2026-04-02T09:00:00Z"),
        )
    }


    @OptIn(ExperimentalTime::class)
    val forelopigVedtakSendtTilBeslutter by lazy {
        EventData.ForelopigVedtakSendtTilBeslutter(
            sakId = UUID.randomUUID().toString(),
            soknadId = sampleSoknad.id!!,
            utfall = ForelopigUtfall.INNVILGE,
            innvilgetBelop = 12000,
            tiltaksperiodeFra = LocalDate(2026, 4, 1),
            tiltaksperiodeTil = LocalDate(2026, 6, 30),
            begrunnelseDeltaker = "Vilkårene er oppfylt",
            begrunnelseArbeidsgiver = null,
            innstiltAvIdent = "Z123456",
            tidspunkt = Instant.parse("2026-04-03T09:00:00Z"),
        )
    }

    @OptIn(ExperimentalTime::class)
    val vedtakFattet by lazy {
        vedtakFattet(sakId = UUID.randomUUID().toString(), soknadId = sampleSoknad.id!!)
    }

    /** [EventData.VedtakFattet] med gyldige domain-felt. [utfall] gir også `utfall` på eventen. */
    @OptIn(ExperimentalTime::class)
    fun vedtakFattet(
        sakId: String,
        soknadId: String,
        utfall: VedtakDomain.Utfall = VedtakDomain.Utfall.INNVILGE,
        versjon: Int = 1,
        fattetAvIdent: String = "B123456",
        tidspunkt: Instant = Instant.parse("2026-04-04T09:00:00Z"),
        soknad: SoknadDomainV2 = sampleSoknadDomain.copy(id = soknadId),
        sak: SakDomain = sampleSakDomain.copy(sakId = sakId),
        vedtak: VedtakDomain = sampleVedtakDomain(utfall).copy(versjon = versjon),
    ) = EventData.VedtakFattet(
        sakId = sakId,
        soknadId = soknadId,
        forelopigVedtakVersjon = versjon,
        utfall = utfall.tilSaksstatus(),
        fattetAvIdent = fattetAvIdent,
        fattetAvNavn = "Beslutter Navn",
        tidspunkt = tidspunkt,
        soknad = soknad,
        sak = sak,
        vedtak = vedtak,
    )

    @OptIn(ExperimentalTime::class)
    val vedtaksbrevArbeidsgiverJournalfoert by lazy {
        EventData.VedtaksbrevArbeidsgiverJournalfoert(
            sakId = UUID.randomUUID().toString(),
            soknadId = sampleSoknad.id!!,
            utfall = VedtakDomain.Utfall.INNVILGE,
            virksomhetsnummer = "987654321",
            virksomhetsnavn = "Testbedrift AS",
            journalpostId = "123",
            dokumentInfoId = "456",
            tidspunkt = Instant.parse("2026-04-04T09:05:00Z"),
        )
    }

    @OptIn(ExperimentalTime::class)
    val sampleSoknadDomain = SoknadDomainV2(
        id = "00000000-0000-4000-8000-000000000001",
        virksomhet = SoknadDomainV2.Virksomhet(
            virksomhetsnummer = "987654321",
            virksomhetsnavn = "Testbedrift AS",
            kontaktperson = SoknadDomainV2.Kontaktperson("Kontakt Person", "kontakt@testbedrift.no", "12345678"),
            beliggenhetsadresse = "Testveien 1, 0557 Oslo",
        ),
        ansatt = SoknadDomainV2.Ansatt(fnr = "01010112345", navn = "Ansatt Navn"),
        ekspert = SoknadDomainV2.Ekspert(
            navn = "Ekspert Navn",
            virksomhet = "Ekspertselskap",
            virksomhetNavn = "Ekspertselskap AS",
            virksomhetOrgnr = "912345678",
            godkjentUtdanningEllerAutorisasjon = listOf("Ergoterapeut"),
            relevantKompetanse = listOf("Ergonomi"),
        ),
        behovForBistand = SoknadDomainV2.BehovForBistand(
            begrunnelse = "Behov begrunnelse",
            behov = "Behov",
            estimertKostnad = "9000",
            timer = "12",
            tilrettelegging = "Tilrettelegging tekst",
            startdato = LocalDate(2026, 4, 1),
        ),
        nav = SoknadDomainV2.Nav(kontaktperson = "Veileder Navn"),
        opprettetAv = "42",
        opprettetTidspunkt = Instant.parse("2026-03-01T09:00:00Z"),
        status = SoknadDomainV2.Status.innsendt,
    )

    @OptIn(ExperimentalTime::class)
    val sampleSakDomain = SakDomain(
        sakId = "00000000-0000-4000-8000-0000000000aa",
        status = SakDomain.Status.TIL_BESLUTNING,
        kildeTilBehandling = SakDomain.KildeTilBehandling.EKSPERTBISTAND,
        behandlendeEnhet = "0315",
        behandlendeEnhetNavn = "Nav arbeidslivssenter Oslo",
        saksbehandlerIdent = "Z123456",
        saksbehandlerNavn = "Tore Tang",
        beslutterIdent = "B123456",
        beslutterNavn = "Beslutter Navn",
        adressebeskyttelse = null,
        skjermet = false,
        vilkarsvurderinger = listOf(
            SakDomain.Vilkarsvurdering(
                vilkar = SakDomain.Vilkarsvurdering.Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD,
                godkjent = true,
                notat = null,
                vurdertAvIdent = "Z123456",
                vurdertTidspunkt = Instant.parse("2026-04-02T09:00:00Z"),
            ),
        ),
    )

    @OptIn(ExperimentalTime::class)
    fun sampleVedtakDomain(utfall: VedtakDomain.Utfall = VedtakDomain.Utfall.INNVILGE): VedtakDomain {
        val innvilget = utfall != VedtakDomain.Utfall.AVSLAA
        return VedtakDomain(
            versjon = 1,
            utfall = utfall,
            innvilgetBelop = 6000.takeIf { utfall == VedtakDomain.Utfall.INNVILGE_REDUSERT },
            tiltaksperiodeFra = LocalDate(2026, 4, 1).takeIf { innvilget },
            tiltaksperiodeTil = LocalDate(2026, 6, 30).takeIf { innvilget },
            begrunnelseDeltaker = "Begrunnelse til deltaker",
            begrunnelseArbeidsgiver = "Begrunnelse til arbeidsgiver".takeIf { utfall != VedtakDomain.Utfall.INNVILGE },
            innstiltAvIdent = "Z123456",
            sendtTidspunkt = Instant.parse("2026-04-03T09:00:00Z"),
        )
    }

    @OptIn(ExperimentalTime::class)
    val forelopigVedtakReturnert by lazy {
        EventData.ForelopigVedtakReturnert(
            sakId = UUID.randomUUID().toString(),
            soknadId = sampleSoknad.id!!,
            forelopigVedtakVersjon = 1,
            aarsaker = setOf(ReturArsak.FEIL_FAKTA),
            forklaring = "Sjekk perioden",
            returnertAvIdent = "B123456",
            returnertAvNavn = "Beslutter Navn",
            tidspunkt = Instant.parse("2026-04-04T09:00:00Z"),
        )
    }

    val sampleSoknad = DTO.Soknad(
        id = UUID.randomUUID().toString(),
        virksomhet = DTO.Virksomhet(
            virksomhetsnummer = "987654321",
            virksomhetsnavn = "Testbedrift AS",
            kontaktperson = DTO.Kontaktperson(
                navn = "Kontakt Person",
                epost = "kontakt@testbedrift.no",
                telefonnummer = "12345678",
            )
        ),
        ansatt = DTO.Ansatt(
            fnr = "01010112345",
            navn = "Ansatt Navn",
        ),
        ekspert = DTO.Ekspert(
            navn = "Ekspert Navn",
            virksomhet = "Ekspertselskap",
            kompetanse = "Ekspertise",
        ),
        behovForBistand = DTO.BehovForBistand(
            begrunnelse = "Behov begrunnelse",
            behov = "Behov",
            estimertKostnad = "9000",
            timer = "12",
            tilrettelegging = "Tilrettelegging tekst",
            startdato = LocalDate(2024, 12, 1),
        ),
        nav = DTO.Nav(
            kontaktperson = "Veileder Navn"
        ),
        status = SoknadStatus.innsendt,
    )

    val sampleTilsagnData = TilsagnData(
        tilsagnNummer = TilsagnData.TilsagnNummer(
            aar = 1337,
            loepenrSak = 42,
            loepenrTilsagn = 43,
        ),
        tilsagnDato = "01.01.2021",
        periode = TilsagnData.Periode(
            fraDato = "01.01.2021",
            tilDato = "01.02.2021"
        ),
        tiltakKode = "42",
        tiltakNavn = "Ekspertbistand",
        administrasjonKode = "etellerannet",
        refusjonfristDato = "10.01.2021",
        tiltakArrangor = TilsagnData.TiltakArrangor(
            arbgiverNavn = "Arrangøren",
            landKode = "1337",
            postAdresse = "et sted",
            postNummer = "1337",
            postSted = "hos naboen",
            orgNummerMorselskap = 7331,
            orgNummer = 1337,
            kontoNummer = "1234.12.12345",
            maalform = "norsk"
        ),
        totaltTilskuddbelop = 24000,
        valutaKode = "NOK",
        tilskuddListe = listOf(
            TilsagnData.Tilskudd(
                tilskuddType = "ekspertbistand",
                tilskuddBelop = 24000,
                visTilskuddProsent = false,
                tilskuddProsent = null
            )
        ),
        deltaker = TilsagnData.Deltaker(
            fodselsnr = "42",
            fornavn = "navn",
            etternavn = "navnesen",
            landKode = "NO",
            postAdresse = "et sted",
            postNummer = "1234",
            postSted = "hos den andre naboen",
        ),
        antallDeltakere = 1,
        antallTimeverk = 100,
        navEnhet = TilsagnData.NavEnhet(
            navKontorNavn = "kontor1",
            navKontor = "Kontor1",
            postAdresse = "hos den tredje",
            postNummer = "1234",
            postSted = "hos den tredje",
            telefon = "12341234",
            faks = null
        ),
        beslutter = TilsagnData.Person(
            fornavn = "Ole",
            etternavn = "Brum",
        ),
        saksbehandler = TilsagnData.Person(
            fornavn = "Nasse",
            etternavn = "Nøff",
        ),
        kommentar = "testdata"
    )

    val sampleTiltaksgjennomforingEndret = TiltaksgjennomforingEndret(
        tiltaksgjennomfoeringId = 1,
        tiltakKode = "EKSPEBIST",
        tiltakStatusKode = TiltaksgjennomforingEndret.TiltakStatusKode.AVLYST,
    )

    val sampleTiltakssakEndret = TiltakssakEndret(
        sakId = 13769058,
        sakskode = "TILT",
        aar = 2026,
        lopenrsak = 202,
        sakstatuskode = TiltakssakEndret.Sakstatuskode.AKTIV,
        brukeridAnsvarlig = "K123456",
        aetatenhetAnsvarlig = "1899",
    )

    val soknadInnsendt = EventData.SoknadInnsendt(
        soknad = sampleSoknad
    )

    val innsendtSoknadJournalfoert = EventData.InnsendtSoknadJournalfoert(
        soknad = sampleSoknad,
        dokumentId = 123456,
        journaldpostId = 654321,
        behandlendeEnhetId = "9876",
    )

    /**
     * Ett eksempel per [EventData]-subklasse, brukt av modelltestene til å verifisere at
     * `aggregateRootId` er implementert og gir riktig verdi for hver type.
     */
    val allEventSamples: List<EventData> = listOf(
        soknadInnsendt,
        innsendtSoknadJournalfoert,
        EventData.SakOpprettet(
            sakId = "11111111-1111-1111-1111-111111111111",
            soknad = sampleSoknad,
            adressebeskyttelse = Adressebeskyttelse.FORTROLIG,
            skjermet = true,
        ),
        EventData.TiltaksgjennomforingOpprettet(
            soknad = sampleSoknad,
            saksnummer = "2026202",
            tiltaksgjennomfoeringId = 1,
        ),
        EventData.TilskuddsbrevMottatt(
            soknad = sampleSoknad,
            tilsagnbrevId = 1,
            tilsagnData = sampleTilsagnData,
        ),
        EventData.TilskuddsbrevMottattKildeAltinn(
            tilsagnbrevId = 1,
            tilsagnData = sampleTilsagnData,
        ),
        EventData.TilskuddsbrevJournalfoert(
            soknad = sampleSoknad,
            dokumentId = 1,
            journaldpostId = 2,
            tilsagnData = sampleTilsagnData,
        ),
        EventData.TilskuddsbrevJournalfoertKildeAltinn(
            dokumentId = 1,
            journaldpostId = 2,
            tilsagnData = sampleTilsagnData,
        ),
        EventData.SoknadAvlystIArena(
            soknad = sampleSoknad,
            tiltaksgjennomforingEndret = sampleTiltaksgjennomforingEndret,
        ),
        EventData.SaksbehandlingStartetIArena(
            soknad = sampleSoknad,
            tiltakssakEndret = sampleTiltakssakEndret,
        ),
        EventData.TilsagnsdataLagret(
            soknad = sampleSoknad,
            tilsagnData = sampleTilsagnData,
        ),
        EventData.TilskuddsbrevVist(
            tilsagnNummer = "1337:42:43",
            soknad = sampleSoknad,
        ),
        EventData.TilskuddsbrevVist(
            tilsagnNummer = "1337:42:43",
            soknad = null,
        ),
        sakOppdatert,
        sakTildeltSaksbehandler,
        sakFrigjort,
        sakTildeltBeslutter,
        vilkarsvurderingOppdatert,
        forelopigVedtakSendtTilBeslutter,
        vedtakFattet,
        forelopigVedtakReturnert,
        vedtaksbrevArbeidsgiverJournalfoert,
    )
}
