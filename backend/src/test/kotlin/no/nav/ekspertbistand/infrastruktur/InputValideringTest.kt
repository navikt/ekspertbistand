package no.nav.ekspertbistand.infrastruktur

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.soknad.DTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InputValideringTest {

    private val gyldigSoknad = DTO.Soknad(
        virksomhet = DTO.Virksomhet(
            virksomhetsnummer = "123456789",
            virksomhetsnavn = "Testbedrift AS",
            kontaktperson = DTO.Kontaktperson(
                navn = "Kari Nordmann",
                epost = "kari.nordmann@test.no",
                telefonnummer = "99999999",
            ),
            beliggenhetsadresse = "Storgata 1, 0001 Oslo",
        ),
        ansatt = DTO.Ansatt(fnr = "12345678901", navn = "Ola Nordmann"),
        ekspert = DTO.Ekspert(
            navn = "Ekspert Ekspertsen",
            virksomhet = "Ekspertfirma AS",
            virksomhetNavn = "Ekspertfirma AS",
            virksomhetOrgnr = "987654321",
            kompetanse = "Ergonomi & tilrettelegging (arbeidsplass)",
            godkjentUtdanningEllerAutorisasjon = listOf("Fysioterapeut"),
            relevantKompetanse = listOf("Arbeidsplassvurdering", "Ergonomi"),
        ),
        behovForBistand = DTO.BehovForBistand(
            begrunnelse = "Behov for tilrettelegging på arbeidsplassen.",
            behov = "Ergonomisk gjennomgang",
            estimertKostnad = "50000",
            timer = "40",
            tilrettelegging = "Tilpasning av arbeidsstasjon",
            startdato = LocalDate(2026, 1, 1),
        ),
        nav = DTO.Nav(kontaktperson = "Nav Kontakt"),
    )

    @Test
    fun `gyldig soknad med legitim norsk input passerer`() {
        valider(gyldigSoknad)
    }

    @Test
    fun `avviser script-tag i toppnivaa-tekstfelt`() {
        val feil = assertFailsWith<UgyldigInputException> {
            valider(gyldigSoknad.copy(nav = DTO.Nav("<script>alert(1)</script>")))
        }
        assertEquals("nav.kontaktperson", feil.feltsti)
    }

    @Test
    fun `avviser gift i dypt noestet felt med riktig feltsti`() {
        val feil = assertFailsWith<UgyldigInputException> {
            valider(
                gyldigSoknad.copy(
                    virksomhet = gyldigSoknad.virksomhet.copy(
                        kontaktperson = gyldigSoknad.virksomhet.kontaktperson.copy(
                            epost = "kari@test.no<img src=x>",
                        ),
                    ),
                ),
            )
        }
        assertEquals("virksomhet.kontaktperson.epost", feil.feltsti)
    }

    @Test
    fun `avviser gift i listeelement med indeks i feltsti`() {
        val feil = assertFailsWith<UgyldigInputException> {
            valider(
                gyldigSoknad.copy(
                    ekspert = gyldigSoknad.ekspert.copy(
                        relevantKompetanse = listOf("Ergonomi", "<script>"),
                    ),
                ),
            )
        }
        assertEquals("ekspert.relevantKompetanse[1]", feil.feltsti)
    }

    @Test
    fun `avviser kontrolltegn men tillater vanlig whitespace`() {
        assertFailsWith<UgyldigInputException> {
            valider(gyldigSoknad.copy(nav = DTO.Nav("ugyldig\u0000tegn")))
        }
        valider(gyldigSoknad.copy(nav = DTO.Nav("linje1\nlinje2\ttabbet")))
    }

    @Test
    fun `utkast med delvis utfylte felt valideres uten aa feile paa null`() {
        valider(DTO.Utkast(nav = DTO.Nav("Gyldig kontakt")))
        assertFailsWith<UgyldigInputException> {
            valider(DTO.Utkast(nav = DTO.Nav("<script>")))
        }
    }

    /**
     * Statisk analyse: verifiserer at [valider] dekker nøyaktig alle tekstfelt i DTO-grafen.
     * Feiler automatisk hvis noen legger til et nytt String-/String-samlingsfelt eller en ny
     * nøstet DTO, slik at ingen tekstfelt slipper unna valideringen uten bevisst oppdatering.
     */
    @Test
    fun `refleksjon dekker alle tekstfelt i DTO-grafen`() {
        val forventet = setOf(
            "id",
            "opprettetAv",
            "opprettetTidspunkt",
            "virksomhet.virksomhetsnummer",
            "virksomhet.virksomhetsnavn",
            "virksomhet.beliggenhetsadresse",
            "virksomhet.kontaktperson.navn",
            "virksomhet.kontaktperson.epost",
            "virksomhet.kontaktperson.telefonnummer",
            "ansatt.fnr",
            "ansatt.navn",
            "ekspert.navn",
            "ekspert.virksomhet",
            "ekspert.virksomhetNavn",
            "ekspert.virksomhetOrgnr",
            "ekspert.kompetanse",
            "ekspert.godkjentUtdanningEllerAutorisasjon[]",
            "ekspert.relevantKompetanse[]",
            "behovForBistand.begrunnelse",
            "behovForBistand.behov",
            "behovForBistand.estimertKostnad",
            "behovForBistand.timer",
            "behovForBistand.tilrettelegging",
            "nav.kontaktperson",
        )
        assertEquals(forventet, tekstFeltStier(DTO.Soknad::class))
        assertEquals(forventet, tekstFeltStier(DTO.Utkast::class))
    }

    /**
     * Q2: regelen er komponerbar – en ekstra [TekstSjekk] slår automatisk inn på alle tekstfelt.
     */
    @Test
    fun `regelen er lett aa utvide med flere sjekker`() {
        val medEkstraSjekk = standardTekstSjekker + TekstSjekk { verdi ->
            if (verdi.contains("FORBUDT")) "forbudt ord" else null
        }

        // Standard-sjekkene alene tillater ordet
        valider(gyldigSoknad.copy(nav = DTO.Nav("FORBUDT verdi")))

        // Med ekstra sjekk avvises det, på hvilket som helst tekstfelt
        val feil = assertFailsWith<UgyldigInputException> {
            valider(gyldigSoknad.copy(nav = DTO.Nav("FORBUDT verdi")), medEkstraSjekk)
        }
        assertEquals("nav.kontaktperson", feil.feltsti)
    }

    @Test
    fun `tekstsjekkene slaar inn paa forventede verdier`() {
        assertNull(IngenVinkelparenteser.sjekk("helt vanlig tekst"))
        assertNull(IngenKontrolltegn.sjekk("tekst med\nlinjeskift"))
    }
}
