package no.nav.ekspertbistand.event

import kotlinx.datetime.LocalDate
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

    val soknadInnsendt = EventData.SoknadInnsendt(
        soknad = sampleSoknad
    )

    val innsendtSoknadJournalfoert = EventData.InnsendtSoknadJournalfoert(
        soknad = sampleSoknad,
        dokumentId = 123456,
        journaldpostId = 654321,
        behandlendeEnhetId = "9876",
    )
}
