package no.nav.ekspertbistand.skjerming

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.NaisEnvironment
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultJson

/** Om personen er skjermet (egen ansatt i Nav) nå. */
fun interface SkjermingOppslag {
    suspend fun erSkjermet(fnr: String): Boolean
}

/**
 * Klient mot `skjermede-personer-pip` (team nom), som svarer på oppslag i skjermingsregisteret.
 *
 * https://github.com/navikt/skjerming
 *
 * Bruker client credentials. Pip-en sjekker bare issuer, audience, signatur og utløp, så tokenet godtas når vi står i
 * inbound-reglene deres. `POST /skjermet` svarer `false` for identer som ikke finnes i registeret. Alle andre svar
 * enn 200 med `true` eller `false` kastes som [SkjermingException].
 */
class SkjermedePersonerPipKlient(
    private val tokenProvider: AzureAdTokenProvider,
    defaultHttpClient: HttpClient,
) : SkjermingOppslag {
    companion object {
        val ingress = basedOnEnv(
            prod = "http://skjermede-personer-pip.nom",
            dev = "http://skjermede-personer-pip.nom",
            other = "http://skjermede-personer-pip.mock.svc.cluster.local",
        )
    }

    private val target = "${NaisEnvironment.clusterName}:nom:skjermede-personer-pip"

    val httpClient = defaultHttpClient.config {
        expectSuccess = false
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "skjermede-personer-pip.client"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    override suspend fun erSkjermet(fnr: String): Boolean {
        val response = httpClient.post {
            url {
                takeFrom(ingress)
                path("/skjermet")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SkjermetRequest(personident = fnr))
            bearerAuth(token())
        }
        if (response.status != HttpStatusCode.OK) {
            throw SkjermingException("Uventet respons fra skjermede-personer-pip: ${response.status}")
        }
        return when (response.bodyAsText().trim()) {
            "true" -> true
            "false" -> false
            else -> throw SkjermingException("Uventet svar fra skjermede-personer-pip")
        }
    }

    private suspend fun token(): String =
        tokenProvider.token(target).fold(
            { it.accessToken },
            { throw SkjermingException("Klarte ikke hente token for skjermede-personer-pip: ${it.error}") },
        )
}

@Serializable
internal data class SkjermetRequest(val personident: String)

class SkjermingException(message: String) : RuntimeException(message)
