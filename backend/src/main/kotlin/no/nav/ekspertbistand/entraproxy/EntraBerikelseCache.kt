package no.nav.ekspertbistand.entraproxy

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.saksbehandling.Role
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaDuration

val ENTRA_PROXY_CACHE_TTL: Duration = 5.minutes

/** Det `AZURE_AD_PROVIDER` henter fra entra-proxy om innlogget saksbehandler. */
@OptIn(ExperimentalTime::class)
data class EntraBerikelse(
    val groups: List<String>,
    val navn: String,
    val epost: String?,
    val gjeldendeEnhet: Enhet,
    val enheter: List<Enhet>,
    val updatedAt: Instant,
)

/** Entra-proxy svarte ikke. Gir 503, ikke 401, slik at brukeren ikke sendes til innlogging. */
class EntraProxyUtilgjengeligException(cause: Throwable) :
    RuntimeException("entra-proxy er ikke tilgjengelig", cause)

/**
 * Cacher [EntraBerikelse] per token i [ttl], slik at hver request ikke gir tre kall mot entra-proxy,
 * og slik at en kortvarig feil der bare treffer første request med et nytt token.
 *
 * Nøkkelen er SHA-256 av tokenet. Samtidige oppslag med samme token deler ett kall. Feil caches
 * ikke, så neste oppslag prøver på nytt.
 */
@OptIn(ExperimentalTime::class)
class EntraBerikelseCache(
    private val entraProxyClient: EntraProxyClient,
    ttl: Duration = ENTRA_PROXY_CACHE_TTL,
    ticker: Ticker = Ticker.systemTicker(),
    private val clock: Clock = Clock.System,
) {
    private val cache: AsyncCache<String, EntraBerikelse> = Caffeine.newBuilder()
        .expireAfterWrite(ttl.toJavaDuration())
        .maximumSize(10_000)
        .ticker(ticker)
        .recordStats()
        .buildAsync()

    // Oppslaget skal ikke avbrytes om requesten som startet det avbrytes, fordi andre venter på det.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        CaffeineCacheMetrics.monitor(Metrics.meterRegistry, cache, "entra_berikelse")
    }

    suspend fun hent(token: String, navIdent: String): EntraBerikelse {
        val key = sha256(token)
        val future = cache.get(key) { _, _ -> scope.future { hentFraEntraProxy(navIdent) } }
        return try {
            // copy(): await() avbryter futuren den venter på. Uten kopi ville en avbrutt request
            // avbrutt oppslaget for alle andre som venter på samme token.
            future.copy().await()
        } catch (e: Exception) {
            // Caffeine fjerner en feilet future asynkront. Vi fjerner den selv, så neste oppslag
            // ikke kan få den samme feilen.
            if (future.isCompletedExceptionally) cache.asMap().remove(key, future)
            throw e
        }
    }

    private suspend fun hentFraEntraProxy(navIdent: String): EntraBerikelse {
        val (groups, ansatt, enheter) = try {
            coroutineScope {
                val groups = async {
                    entraProxyClient.hentGrupper(navIdent)
                        .map { it.rolle }
                        .filter { rolle -> Role.entries.any { it.groupId == rolle } }
                }
                val ansatt = async { entraProxyClient.hentAnsatt(navIdent) }
                val enheter = async { entraProxyClient.hentEnheter(navIdent) }
                Triple(groups.await(), ansatt.await(), enheter.await())
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            throw EntraProxyUtilgjengeligException(e)
        }

        return EntraBerikelse(
            groups = groups,
            navn = checkNotNull(ansatt.visningsnavn()) { "entra-proxy ga ansatt uten navn" },
            epost = ansatt.epost,
            gjeldendeEnhet = ansatt.enhet,
            enheter = enheter,
            updatedAt = clock.now(),
        )
    }

    private fun sha256(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
