package no.nav.ekspertbistand.soknad

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.infrastruktur.UgyldigInputException
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ResultRow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class InnsendingValideringTest {

    private val idag = LocalDate(2026, 11, 17)

    private val komplett: Map<Column<*>, Any?> = mapOf(
        UtkastTable.virksomhetsnummer to "910456900",
        UtkastTable.virksomhetsnavn to "Testbedrift AS",
        UtkastTable.kontaktpersonNavn to "Kari Kontakt",
        UtkastTable.kontaktpersonEpost to "kari@testbedrift.no",
        UtkastTable.kontaktpersonTelefon to "12345678",
        UtkastTable.ansattFnr to "22420094160",
        UtkastTable.ansattNavn to "Mona Moonlight",
        UtkastTable.ansattBekreftetTidspunkt to Instant.parse("2026-11-17T08:00:00Z"),
        UtkastTable.ekspertNavn to "Ekspert Navn",
        UtkastTable.ekspertVirksomhet to "Ekspertselskap AS (912345678)",
        UtkastTable.ekspertVirksomhetNavn to "Ekspertselskap AS",
        UtkastTable.ekspertVirksomhetOrgnr to "912345678",
        UtkastTable.ekspertKompetanse to null,
        UtkastTable.ekspertGodkjentUtdanningEllerAutorisasjon to encodeStringList(listOf("Ergoterapeut")),
        UtkastTable.ekspertRelevantKompetanse to encodeStringList(listOf("Tilrettelegging")),
        UtkastTable.behovForBistandBegrunnelse to "Begrunnelse",
        UtkastTable.behovForBistand to "Behov",
        UtkastTable.behovForBistandEstimertKostnad to "9000",
        UtkastTable.behovForBistandTimer to "12",
        UtkastTable.behovForBistandTilrettelegging to "Tilrettelegging",
        UtkastTable.behovForBistandStartdato to idag,
        UtkastTable.navKontaktPerson to "Veileder Navn",
    )

    private fun rad(vararg endringer: Pair<Column<*>, Any?>): ResultRow =
        ResultRow.createAndFillValues((komplett + endringer).mapKeys { (kolonne, _) -> kolonne as Expression<*> })

    private fun assertFeil(feltsti: String, vararg endringer: Pair<Column<*>, Any?>) {
        val feil = assertThrows<UgyldigInputException> { validerKomplett(rad(*endringer), idag) }
        assertEquals(feltsti, feil.feltsti)
    }

    @Test
    fun `komplett utkast blir innsendingsklart`() {
        val soknad = validerKomplett(rad(), idag)

        assertEquals("910456900", soknad.virksomhetsnummer)
        assertEquals("Testbedrift AS", soknad.virksomhetsnavn)
        assertEquals("Mona Moonlight", soknad.ansattNavn)
        assertEquals("Ekspertselskap AS (912345678)", soknad.ekspertVirksomhet)
        assertEquals("Ekspertselskap AS", soknad.ekspertVirksomhetNavn)
        assertEquals("912345678", soknad.ekspertVirksomhetOrgnr)
        assertEquals("", soknad.ekspertKompetanse)
        assertEquals(listOf("Ergoterapeut"), soknad.ekspertGodkjentUtdanningEllerAutorisasjon)
        assertEquals(listOf("Tilrettelegging"), soknad.ekspertRelevantKompetanse)
        assertEquals(idag, soknad.behovForBistandStartdato)
    }

    @Test
    fun `virksomhetsnummer og virksomhetsnavn`() {
        assertFeil("virksomhet.virksomhetsnummer", UtkastTable.virksomhetsnummer to "12345678")
        assertFeil("virksomhet.virksomhetsnavn", UtkastTable.virksomhetsnavn to " ")
    }

    @Test
    fun `kontaktperson hos arbeidsgiver`() {
        assertFeil("virksomhet.kontaktperson.navn", UtkastTable.kontaktpersonNavn to null)
        assertFeil("virksomhet.kontaktperson.navn", UtkastTable.kontaktpersonNavn to "  ")
        assertFeil("virksomhet.kontaktperson.epost", UtkastTable.kontaktpersonEpost to null)
        assertFeil("virksomhet.kontaktperson.epost", UtkastTable.kontaktpersonEpost to "kari@testbedrift")
        assertFeil("virksomhet.kontaktperson.epost", UtkastTable.kontaktpersonEpost to "kari testbedrift.no")
        assertFeil("virksomhet.kontaktperson.telefonnummer", UtkastTable.kontaktpersonTelefon to null)
        assertFeil("virksomhet.kontaktperson.telefonnummer", UtkastTable.kontaktpersonTelefon to "1234567")
        assertFeil("virksomhet.kontaktperson.telefonnummer", UtkastTable.kontaktpersonTelefon to "1234567a")
    }

    @Test
    fun `ekspert og virksomheten til eksperten`() {
        assertFeil("ekspert.navn", UtkastTable.ekspertNavn to null)
        assertFeil("ekspert.virksomhet", UtkastTable.ekspertVirksomhet to "")
        assertFeil("ekspert.virksomhetNavn", UtkastTable.ekspertVirksomhetNavn to null)
        assertFeil("ekspert.virksomhetOrgnr", UtkastTable.ekspertVirksomhetOrgnr to null)
        assertFeil("ekspert.virksomhetOrgnr", UtkastTable.ekspertVirksomhetOrgnr to "91234567")
        assertFeil(
            "ekspert.godkjentUtdanningEllerAutorisasjon",
            UtkastTable.ekspertGodkjentUtdanningEllerAutorisasjon to null,
        )
        assertFeil("ekspert.relevantKompetanse", UtkastTable.ekspertRelevantKompetanse to "[]")
    }

    @Test
    fun `behov for bistand og timer`() {
        assertFeil("behovForBistand.begrunnelse", UtkastTable.behovForBistandBegrunnelse to null)
        assertFeil("behovForBistand.behov", UtkastTable.behovForBistand to "")
        assertFeil("behovForBistand.tilrettelegging", UtkastTable.behovForBistandTilrettelegging to " ")
        assertFeil("behovForBistand.timer", UtkastTable.behovForBistandTimer to null)
        assertFeil("behovForBistand.timer", UtkastTable.behovForBistandTimer to "0")
        assertFeil("behovForBistand.timer", UtkastTable.behovForBistandTimer to "1,5")
    }

    @Test
    fun `estimert kostnad må være positivt heltall og ikke over maks sats`() {
        assertFeil("behovForBistand.estimertKostnad", UtkastTable.behovForBistandEstimertKostnad to null)
        assertFeil("behovForBistand.estimertKostnad", UtkastTable.behovForBistandEstimertKostnad to "-1")
        assertFeil("behovForBistand.estimertKostnad", UtkastTable.behovForBistandEstimertKostnad to "100.5")
        assertFeil("behovForBistand.estimertKostnad", UtkastTable.behovForBistandEstimertKostnad to "24901")

        validerKomplett(rad(UtkastTable.behovForBistandEstimertKostnad to "24900"), idag)
    }

    @Test
    fun `startdato må være satt, ikke i fortiden, og ha en maks sats`() {
        assertFeil("behovForBistand.startdato", UtkastTable.behovForBistandStartdato to null)
        assertFeil("behovForBistand.startdato", UtkastTable.behovForBistandStartdato to LocalDate(2026, 11, 16))

        val utenSats = assertThrows<UgyldigInputException> {
            validerKomplett(rad(UtkastTable.behovForBistandStartdato to LocalDate(2025, 12, 31)), LocalDate(2025, 12, 1))
        }
        assertEquals("behovForBistand.startdato", utenSats.feltsti)
        assertEquals("ingen sats gjelder for startdato", utenSats.aarsak)
    }

    @Test
    fun `kontaktperson i Nav`() {
        assertFeil("nav.kontaktperson", UtkastTable.navKontaktPerson to null)
    }
}
