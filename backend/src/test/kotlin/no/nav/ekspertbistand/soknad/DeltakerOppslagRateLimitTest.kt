package no.nav.ekspertbistand.soknad

import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DeltakerOppslagRateLimitTest {

    private val bruker = "12345678911"
    private val annenBruker = "12345678912"
    private val fnr = (1..6).map { "1234567890$it" }

    private class FlyttbarKlokke(var naa: Instant = Instant.parse("2026-10-30T10:00:00Z")) : Clock {
        override fun now() = naa
    }

    @Test
    fun `tillater fem ulike fnr og avviser det sjette`() = medRateLimit { rateLimit, _, _ ->
        fnr.take(5).forEach { assertTrue(rateLimit.registrer(bruker, it)) }
        assertFalse(rateLimit.registrer(bruker, fnr[5]))
    }

    @Test
    fun `fnr som allerede er slaatt opp i vinduet tillates selv om grensen er naadd`() =
        medRateLimit { rateLimit, _, _ ->
            fnr.take(5).forEach { assertTrue(rateLimit.registrer(bruker, it)) }
            repeat(10) { assertTrue(rateLimit.registrer(bruker, fnr[0])) }
            assertFalse(rateLimit.registrer(bruker, fnr[5]))
        }

    @Test
    fun `vinduet er rullerende`() = medRateLimit { rateLimit, klokke, _ ->
        assertTrue(rateLimit.registrer(bruker, fnr[0]))
        klokke.naa += 2.minutes
        fnr.subList(1, 5).forEach { assertTrue(rateLimit.registrer(bruker, it)) }
        assertFalse(rateLimit.registrer(bruker, fnr[5]))

        // Første oppslag faller ut av vinduet
        klokke.naa += 3.minutes + 1.seconds
        assertTrue(rateLimit.registrer(bruker, fnr[5]))
        assertFalse(rateLimit.registrer(bruker, fnr[0]))
    }

    @Test
    fun `grensen gjelder per innlogget bruker`() = medRateLimit { rateLimit, _, _ ->
        fnr.take(5).forEach { assertTrue(rateLimit.registrer(bruker, it)) }
        assertTrue(rateLimit.registrer(annenBruker, fnr[5]))
    }

    @Test
    fun `verken innlogget brukers eller deltakerens fnr lagres i klartekst`() = medRateLimit { rateLimit, _, db ->
        rateLimit.registrer(bruker, fnr[0])
        val rad = transaction(db) { DeltakerOppslagTable.selectAll().single() }
        val brukerHash = rad[DeltakerOppslagTable.innloggetBrukerHash]
        val fnrHash = rad[DeltakerOppslagTable.fnrHash]
        assertEquals(64, brukerHash.length)
        assertEquals(64, fnrHash.length)
        assertNotEquals(bruker, brukerHash)
        assertNotEquals(fnr[0], fnrHash)
    }

    @Test
    fun `sletter oppslag eldre enn en time`() = medRateLimit { rateLimit, klokke, db ->
        rateLimit.registrer(bruker, fnr[0])
        klokke.naa += 61.minutes
        rateLimit.registrer(bruker, fnr[1])

        rateLimit.slettGamleOppslag()

        assertEquals(1, transaction(db) { DeltakerOppslagTable.selectAll().count() })
    }

    private fun medRateLimit(block: (DeltakerOppslagRateLimit, FlyttbarKlokke, Database) -> Unit) =
        TestDatabase.isolert().use {
            val db = it.config.jdbcDatabase
            val klokke = FlyttbarKlokke()
            block(DeltakerOppslagRateLimit(db, clock = klokke), klokke, db)
        }
}
