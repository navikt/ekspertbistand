package no.nav.ekspertbistand.entraproxy

import com.github.benmanes.caffeine.cache.Ticker
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.mocks.testAnsattJson
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class EntraBerikelseCacheTest {

    private val start = Instant.parse("2026-04-02T08:00:00Z")

    private class Oppsett(
        val cache: EntraBerikelseCache,
        val kall: Map<String, AtomicInteger>,
        val tid: Tid,
    ) {
        fun antallKall(path: String) = kall[path]?.get() ?: 0
    }

    private class Tid(var nanos: Long, var now: Instant) : Clock {
        override fun now() = now
        fun gaa(varighet: Duration) {
            nanos += varighet.inWholeNanoseconds
            now += varighet
        }
    }

    private fun oppsett(
        ansatt: () -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to testAnsattJson("A123456") },
        foerSvar: suspend () -> Unit = {},
    ): Oppsett {
        val kall = ConcurrentHashMap<String, AtomicInteger>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            kall.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            foerSvar()
            val (status, body) = when {
                path.startsWith(EntraProxyClient.GRUPPER_API_PATH) ->
                    HttpStatusCode.OK to """[{ "rolle": "0000-CA-Ekspertbistand_Saksbehandler" }, { "rolle": "ukjent" }]"""
                path.startsWith(EntraProxyClient.API_PATH) ->
                    HttpStatusCode.OK to """[{ "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" }]"""
                else -> ansatt()
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val tid = Tid(0, start)
        val cache = EntraBerikelseCache(
            entraProxyClient = EntraProxyClient(successAzureAdTokenProvider, HttpClient(engine)),
            ttl = 5.minutes,
            ticker = Ticker { tid.nanos },
            clock = tid,
        )
        return Oppsett(cache, kall, tid)
    }

    private val ansattPath = "${EntraProxyClient.ANSATT_API_PATH}/A123456"

    @Test
    fun `henter grupper, navn, epost og enheter`(): Unit = runBlocking {
        val berikelse = oppsett().cache.hent("token", "A123456")

        assertEquals(listOf("0000-CA-Ekspertbistand_Saksbehandler"), berikelse.groups)
        assertEquals("Tore Tang", berikelse.navn)
        assertEquals("a123456@nav.no", berikelse.epost)
        assertEquals("1234", berikelse.gjeldendeEnhet.enhetnummer)
        assertEquals(listOf("1234"), berikelse.enheter.map { it.enhetnummer })
        assertEquals(start, berikelse.updatedAt)
    }

    @Test
    fun `to oppslag med samme token gir ett sett kall`(): Unit = runBlocking {
        val oppsett = oppsett()

        oppsett.cache.hent("token", "A123456")
        oppsett.cache.hent("token", "A123456")

        assertEquals(3, oppsett.kall.size)
        oppsett.kall.values.forEach { assertEquals(1, it.get()) }
    }

    @Test
    fun `samtidige oppslag med samme token gir ett sett kall`(): Unit = runBlocking {
        val slipp = CompletableDeferred<Unit>()
        val oppsett = oppsett(foerSvar = { slipp.await() })

        val oppslag = (1..5).map { async { oppsett.cache.hent("token", "A123456") } }
        withTimeout(5.seconds) { while (oppsett.kall.size < 3) delay(10) }
        slipp.complete(Unit)
        val resultater = oppslag.awaitAll()

        assertEquals(1, resultater.toSet().size)
        oppsett.kall.values.forEach { assertEquals(1, it.get()) }
    }

    @Test
    fun `ulike tokener gir hvert sitt oppslag`(): Unit = runBlocking {
        val oppsett = oppsett()

        oppsett.cache.hent("token-1", "A123456")
        oppsett.cache.hent("token-2", "A123456")

        assertEquals(2, oppsett.antallKall(ansattPath))
    }

    @Test
    fun `henter på nytt etter ttl, men ikke før`(): Unit = runBlocking {
        val oppsett = oppsett()

        val foerste = oppsett.cache.hent("token", "A123456")
        oppsett.tid.gaa(4.minutes)
        val cachetreff = oppsett.cache.hent("token", "A123456")
        assertEquals(1, oppsett.antallKall(ansattPath))
        assertEquals(start, cachetreff.updatedAt)

        oppsett.tid.gaa(1.minutes + 1.seconds)
        val ny = oppsett.cache.hent("token", "A123456")

        assertEquals(2, oppsett.antallKall(ansattPath))
        assertEquals(start, foerste.updatedAt)
        assertEquals(start + 5.minutes + 1.seconds, ny.updatedAt)
    }

    @Test
    fun `feil caches ikke`(): Unit = runBlocking {
        var feil = true
        val oppsett = oppsett(ansatt = {
            if (feil) HttpStatusCode.InternalServerError to "nede" else HttpStatusCode.OK to testAnsattJson("A123456")
        })

        assertIs<EntraProxyUtilgjengeligException>(runCatching { oppsett.cache.hent("token", "A123456") }.exceptionOrNull())
        feil = false
        val berikelse = oppsett.cache.hent("token", "A123456")

        assertEquals("Tore Tang", berikelse.navn)
        assertEquals(2, oppsett.antallKall(ansattPath))
    }

    @Test
    fun `ansatt uten navn gir IllegalStateException, ikke 503`(): Unit = runBlocking {
        val oppsett = oppsett(ansatt = {
            HttpStatusCode.OK to """{ "navIdent": "A123456", "enhet": { "enhetnummer": "1234", "navn": "X" }, "tIdent": "T123456" }"""
        })

        assertIs<IllegalStateException>(runCatching { oppsett.cache.hent("token", "A123456") }.exceptionOrNull())
    }
}
