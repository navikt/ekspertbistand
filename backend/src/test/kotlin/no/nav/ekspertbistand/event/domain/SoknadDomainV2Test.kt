package no.nav.ekspertbistand.event.domain

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.sak.TEST_ANSATT_FNR
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.findSoknadById
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SoknadDomainV2Test {

    private val innsendt = DTO.Soknad(
        id = "8f3f7a1e-4c55-4d0c-9a51-7c2b1d1f0e11",
        virksomhet = DTO.Virksomhet(
            virksomhetsnummer = "987654321",
            virksomhetsnavn = "Testbedrift AS",
            kontaktperson = DTO.Kontaktperson("Kontakt Person", "kontakt@testbedrift.no", "12345678"),
        ),
        ansatt = DTO.Ansatt(fnr = TEST_ANSATT_FNR, navn = "Ansatt Navn"),
        ekspert = DTO.Ekspert(
            navn = "Ekspert Navn",
            virksomhet = "Ekspertselskap",
            godkjentUtdanningEllerAutorisasjon = listOf("Ergoterapeut"),
            relevantKompetanse = listOf("Tilrettelegging"),
        ),
        behovForBistand = DTO.BehovForBistand(
            begrunnelse = "Begrunnelse",
            behov = "Behov",
            estimertKostnad = "9000",
            timer = "12",
            tilrettelegging = "Tilrettelegging",
            startdato = LocalDate(2026, 12, 1),
        ),
        nav = DTO.Nav(kontaktperson = "Veileder Navn"),
        opprettetAv = "42",
        opprettetTidspunkt = "2026-11-20T08:00:00Z",
    )

    @Test
    fun `innsendt søknad fra databasen blir SoknadDomainV2`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val lagret = lagreSoknadOgSak(database)

        val domain = transaction(database) { findSoknadById(lagret.soknadId, filterForArbeidsgiver = false) }!!
            .tilDomain()

        assertEquals(lagret.soknadId.toString(), domain.id)
        assertEquals("987654321", domain.virksomhet.virksomhetsnummer)
        assertEquals(TEST_ANSATT_FNR, domain.ansatt.fnr)
        assertEquals(listOf("Ergoterapeut"), domain.ekspert.godkjentUtdanningEllerAutorisasjon)
        assertEquals(listOf("Tilrettelegging"), domain.ekspert.relevantKompetanse)
        assertEquals("42", domain.opprettetAv)
        assertEquals(SoknadDomainV2.Status.innsendt, domain.status)
    }

    @Test
    fun `søknad uten id, opprettetAv eller opprettetTidspunkt feiler`() {
        assertThrows<IllegalStateException> { innsendt.copy(id = null).tilDomain() }
        assertThrows<IllegalStateException> { innsendt.copy(opprettetAv = null).tilDomain() }
        assertThrows<IllegalStateException> { innsendt.copy(opprettetTidspunkt = null).tilDomain() }
    }

    @Test
    fun `gammel søknad med fritekst i kompetanse i stedet for listene feiler`() {
        val gammel = innsendt.copy(
            ekspert = innsendt.ekspert.copy(
                kompetanse = "Fritekst",
                godkjentUtdanningEllerAutorisasjon = emptyList(),
                relevantKompetanse = emptyList(),
            ),
        )
        assertThrows<IllegalStateException> { gammel.tilDomain() }
        assertThrows<IllegalStateException> {
            innsendt.copy(ekspert = innsendt.ekspert.copy(relevantKompetanse = emptyList())).tilDomain()
        }
    }

    @Test
    fun `utkast kan ikke bli SoknadDomainV2`() {
        assertThrows<IllegalStateException> { SoknadStatus.utkast.tilDomain() }
    }
}
