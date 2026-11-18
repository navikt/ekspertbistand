package no.nav.ekspertbistand.event.domain

import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.sak.TEST_ANSATT_FNR
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.findSoknadDomainV2ById
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class SoknadDomainV2Test {

    @Test
    fun `innsendt søknad fra databasen blir SoknadDomainV2`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val lagret = lagreSoknadOgSak(database)

        val domain = transaction(database) { findSoknadDomainV2ById(lagret.soknadId, filterForArbeidsgiver = false) }!!

        assertEquals(lagret.soknadId.toString(), domain.id)
        assertEquals("987654321", domain.virksomhet.virksomhetsnummer)
        assertEquals("Testbedrift AS", domain.virksomhet.virksomhetsnavn)
        assertEquals(TEST_ANSATT_FNR, domain.ansatt.fnr)
        assertEquals(listOf("Ergoterapeut"), domain.ekspert.godkjentUtdanningEllerAutorisasjon)
        assertEquals(listOf("Tilrettelegging"), domain.ekspert.relevantKompetanse)
        assertEquals("42", domain.opprettetAv)
        assertEquals(SoknadDomainV2.Status.innsendt, domain.status)
    }

    @Test
    fun `gammel søknad med fritekst i kompetanse i stedet for listene feiler`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val gammel = lagreSoknadOgSak(database)
        val utenRelevantKompetanse = lagreSoknadOgSak(database)
        transaction(database) {
            SoknadTable.update({ SoknadTable.id eq gammel.soknadId }) { rad ->
                rad[ekspertKompetanse] = "Fritekst"
                rad[ekspertGodkjentUtdanningEllerAutorisasjon] = null
                rad[ekspertRelevantKompetanse] = null
            }
            SoknadTable.update({ SoknadTable.id eq utenRelevantKompetanse.soknadId }) { rad ->
                rad[ekspertRelevantKompetanse] = "[]"
            }
        }

        transaction(database) {
            assertThrows<IllegalStateException> {
                findSoknadDomainV2ById(gammel.soknadId, filterForArbeidsgiver = false)
            }
            assertThrows<IllegalStateException> {
                findSoknadDomainV2ById(utenRelevantKompetanse.soknadId, filterForArbeidsgiver = false)
            }
        }
    }

    @Test
    fun `utkast kan ikke bli SoknadDomainV2`() {
        assertThrows<IllegalStateException> { SoknadStatus.utkast.tilDomain() }
    }
}
