package no.nav.ekspertbistand.infrastruktur

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.soknad.DTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals("inneholder < eller >", feil.aarsak)
        assertTrue(feil.message!!.contains("nav.kontaktperson"))
        assertTrue(feil.message!!.contains("inneholder < eller >"))
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
        val feil = assertFailsWith<UgyldigInputException> {
            valider(gyldigSoknad.copy(nav = DTO.Nav("ugyldig\u0000tegn")))
        }
        assertEquals("inneholder kontrolltegn", feil.aarsak)
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
    fun `avviser for lang tekst men tillater tekst paa grensen`() {
        valider(gyldigSoknad.copy(nav = DTO.Nav("a".repeat(MAKS_TEKST_LENGDE))))
        val feil = assertFailsWith<UgyldigInputException> {
            valider(gyldigSoknad.copy(nav = DTO.Nav("a".repeat(MAKS_TEKST_LENGDE + 1))))
        }
        assertEquals("nav.kontaktperson", feil.feltsti)
        assertTrue(feil.aarsak.contains("for lang"))
    }

    @Test
    fun `avviser usynlige unicode-formattegn`() {
        // U+202E RIGHT-TO-LEFT OVERRIDE (Trojan Source / spoofing)
        val feil = assertFailsWith<UgyldigInputException> {
            valider(gyldigSoknad.copy(nav = DTO.Nav("Nav\u202EtenoK")))
        }
        assertEquals("nav.kontaktperson", feil.feltsti)
        assertEquals("inneholder usynlige formatkontrolltegn", feil.aarsak)
    }

    @Test
    fun `avviser lister som er for lange`() {
        valider(
            gyldigSoknad.copy(
                ekspert = gyldigSoknad.ekspert.copy(
                    relevantKompetanse = List(MAKS_LISTE_STORRELSE) { "Kompetanse" },
                ),
            ),
        )
        val feil = assertFailsWith<UgyldigInputException> {
            valider(
                gyldigSoknad.copy(
                    ekspert = gyldigSoknad.ekspert.copy(
                        relevantKompetanse = List(MAKS_LISTE_STORRELSE + 1) { "Kompetanse" },
                    ),
                ),
            )
        }
        assertEquals("ekspert.relevantKompetanse", feil.feltsti)
        assertTrue(feil.aarsak.contains("for mange elementer"))
    }

    @Test
    fun `tekstsjekkene slaar inn paa forventede verdier`() {
        assertNull(IngenVinkelparenteser.sjekk("helt vanlig tekst"))
        assertNull(IngenKontrolltegn.sjekk("tekst med\nlinjeskift"))
    }

    @Test
    fun `godtar fodselsnummer med gyldige kontrollsiffer`() {
        assertTrue(erGyldigFodselsnummer(gyldigFnr("010190123")))
        assertTrue(erGyldigFodselsnummer(gyldigFnr("410190123")), "D-nummer")
        assertTrue(erGyldigFodselsnummer(gyldigFnr("018190123")), "syntetisk fra Tenor")
    }

    @Test
    fun `avviser feil lengde, ikke-siffer og feil kontrollsiffer`() {
        val gyldig = gyldigFnr("010190123")
        assertFalse(erGyldigFodselsnummer(gyldig.dropLast(1)))
        assertFalse(erGyldigFodselsnummer(gyldig + "0"))
        assertFalse(erGyldigFodselsnummer(gyldig.replaceRange(0, 1, "a")))
        assertFalse(erGyldigFodselsnummer(gyldig.dropLast(1) + ((gyldig.last() - '0' + 1) % 10)))
        assertFalse(erGyldigFodselsnummer("12345678910"))
        assertFalse(erGyldigFodselsnummer(""))
    }
}

/** Lager et fødselsnummer med gyldige kontrollsiffer fra de ni første sifrene, eller prøver neste individnummer. */
fun gyldigFnr(ni: String): String {
    var kandidat = ni.toLong()
    while (true) {
        val siffer = kandidat.toString().padStart(9, '0').map { it - '0' }
        val k1 = kontroll(siffer, intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2))
        val k2 = k1?.let { kontroll(siffer + it, intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)) }
        if (k1 != null && k2 != null) return siffer.joinToString("") + k1 + k2
        kandidat++
    }
}

private fun kontroll(siffer: List<Int>, vekter: IntArray): Int? =
    when (val k = 11 - vekter.indices.sumOf { vekter[it] * siffer[it] } % 11) {
        11 -> 0
        10 -> null
        else -> k
    }
