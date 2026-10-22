package no.nav.ekspertbistand.norg

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class KommunenavnCacheTest {
    private class StyrtKlokke(var naa: Instant = Instant.parse("2026-01-01T00:00:00Z")) : Clock {
        override fun now() = naa
    }

    private val geografi =
        """[{"navn": "0301", "term": "Oslo"}, {"navn": "46", "term": "Vestland"}, {"navn": "030101", "term": "Gamle Oslo"}]"""

    @Test
    fun `gir bare kommuner, og spør Norg igjen først når ttl er ute`() = testApplication {
        var kall = 0
        mockGeografi { kall++; respondText(geografi, ContentType.Application.Json) }
        val klokke = StyrtKlokke()
        val cache = KommunenavnCache(NorgKlient(client), ttl = 1.days, clock = klokke)

        assertEquals(mapOf("0301" to "Oslo"), cache.hent())
        klokke.naa += 23.hours
        cache.hent()
        assertEquals(1, kall)

        klokke.naa += 2.hours
        cache.hent()
        assertEquals(2, kall)
    }

    @Test
    fun `beholder forrige svar når Norg feiler, og venter før neste forsøk`() = testApplication {
        var kall = 0
        var feiler = false
        mockGeografi {
            kall++
            if (feiler) respond(HttpStatusCode.InternalServerError) else respondText(geografi, ContentType.Application.Json)
        }
        val klokke = StyrtKlokke()
        val cache = KommunenavnCache(NorgKlient(client), ttl = 1.days, ventEtterFeil = 5.minutes, clock = klokke)
        cache.hent()

        feiler = true
        klokke.naa += 1.days
        assertEquals(mapOf("0301" to "Oslo"), cache.hent())
        klokke.naa += 4.minutes
        cache.hent()
        assertEquals(2, kall)

        feiler = false
        klokke.naa += 1.minutes
        cache.hent()
        assertEquals(3, kall)
    }

    @Test
    fun `gir tomt svar når Norg feiler før første vellykkede oppslag`() = testApplication {
        mockGeografi { respond(HttpStatusCode.InternalServerError) }

        assertEquals(emptyMap(), KommunenavnCache(NorgKlient(client)).hent())
    }

    private fun ApplicationTestBuilder.mockGeografi(svar: suspend RoutingCall.() -> Unit) {
        externalServices {
            hosts(NorgKlient.baseUrl) {
                routing {
                    get("/norg2/api/v1/kodeverk/geografi") { call.svar() }
                }
            }
        }
    }
}
