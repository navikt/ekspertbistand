package no.nav.ekspertbistand.event

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.arena.TiltakssakEndret
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import java.util.UUID

object TestEventData {


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
    )
}
