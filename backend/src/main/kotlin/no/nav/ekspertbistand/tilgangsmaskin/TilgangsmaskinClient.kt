package no.nav.ekspertbistand.tilgangsmaskin

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenExchanger
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.NaisEnvironment
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultJson

/**
 * Klient mot Tilgangsmaskinen (populasjonstilgangskontroll) – person-nivå
 * tilgangskontroll: skjerming, adressebeskyttelse (kode 6/7) mv.
 *
 * https://github.com/navikt/populasjonstilgangskontroll
 * https://tilgangsmaskin.intern.dev.nav.no/swagger-ui/index.html
 *
 * Bruker On-Behalf-Of (OBO): saksbehandlerens innkommende Azure AD-token veksles
 * til et token for tilgangsmaskin. Saksbehandlerens `navIdent` ligger dermed i
 * selve tokenet – det kan IKKE overstyres via request. Kalleren må sende
 * `userToken` fra det autentiserte
 * [no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal.subjectToken],
 * aldri fra klient-input.
 *
 * Semantikk (fail-closed hos kaller):
 *  - 204 No Content -> [Tilgangsresultat.Innvilget]
 *  - 403 Forbidden  -> [Tilgangsresultat.Avvist] (application/problem+json)
 *  - 404, 400, 5xx ... -> [TilgangsmaskinException]
 *
 * Klienten er foreløpig ikke koblet inn i noen rute.
 */
class TilgangsmaskinClient(
    private val tokenExchanger: AzureAdTokenExchanger,
    defaultHttpClient: HttpClient,
) {
    companion object {
        val ingress = basedOnEnv(
            prod = "http://populasjonstilgangskontroll.tilgangsmaskin",
            dev = "http://populasjonstilgangskontroll.tilgangsmaskin",
            other = "http://tilgangsmaskin.mock.svc.cluster.local",
        )
    }

    // Token-exchange-target på formatet cluster:namespace:app (jf. AltinnTilgangerClient).
    private val target = "${NaisEnvironment.clusterName}:tilgangsmaskin:populasjonstilgangskontroll"

    val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "tilgangsmaskin.client"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    /**
     * Enkelt-sjekk: har den innloggede saksbehandleren (via [userToken]) tilgang
     * til [brukerIdent] etter [regelsett]?
     *
     * `POST /api/v1/{kjerne|komplett}` (OBO) med brukers ident som JSON-streng-body.
     */
    suspend fun evaluer(
        userToken: String,
        brukerIdent: String,
        regelsett: Regelsett = Regelsett.KOMPLETT,
    ): Tilgangsresultat {
        val response = httpClient.post {
            url {
                takeFrom(ingress)
                path("/api/v1/${regelsett.path}")
            }
            contentType(ContentType.Application.Json)
            // Endepunktet forventer brukers ident som en JSON-streng ("fnr").
            setBody(defaultJson.encodeToString(brukerIdent))
            accept(ContentType.Application.Json)
            bearerAuth(exchangeToken(userToken))
        }

        return when (response.status) {
            HttpStatusCode.NoContent -> Tilgangsresultat.Innvilget
            HttpStatusCode.Forbidden -> {
                // 403 leveres som application/problem+json; parses eksplisitt
                // for å unngå content-type-matching i ContentNegotiation.
                val avvisning = defaultJson.decodeFromString<TilgangsmaskinAvvisning>(response.bodyAsText())
                Tilgangsresultat.Avvist(
                    kode = avvisning.title,
                    begrunnelse = avvisning.begrunnelse,
                    kanOverstyres = avvisning.kanOverstyres ?: false,
                    detaljer = avvisning,
                )
            }

            else -> throw TilgangsmaskinException(
                "Uventet respons fra tilgangsmaskin: ${response.status}"
            )
        }
    }

    /**
     * Bulk-sjekk for et sett brukere – tiltenkt filtrering av lister (unngår
     * N+1 mot tilgangsmaskin). Maks 1000 identer per kall.
     *
     * `POST /api/v1/bulk/obo` med body `[{ "brukerId", "type" }]`.
     * Svarer 207 Multi-Status med [AggregertBulkRespons].
     */
    suspend fun evaluerBulk(
        userToken: String,
        brukerIdenter: Collection<String>,
        regelsett: Regelsett = Regelsett.KOMPLETT,
    ): AggregertBulkRespons {
        val specs = brukerIdenter
            .distinct()
            .map { BrukerIdOgRegelsett(brukerId = it, type = regelsett.regelType) }

        val response = httpClient.post {
            url {
                takeFrom(ingress)
                path("/api/v1/bulk/obo")
            }
            contentType(ContentType.Application.Json)
            setBody(specs)
            accept(ContentType.Application.Json)
            bearerAuth(exchangeToken(userToken))
        }

        if (!response.status.isSuccess()) {
            throw TilgangsmaskinException(
                "Uventet respons fra tilgangsmaskin bulk: ${response.status}"
            )
        }
        return response.body()
    }

    private suspend fun exchangeToken(userToken: String): String =
        tokenExchanger.exchange(target, userToken).fold(
            { it.accessToken },
            { throw TilgangsmaskinException("Klarte ikke veksle token (OBO): ${it.error}") },
        )
}

enum class Regelsett(val path: String, val regelType: String) {
    KJERNE(path = "kjerne", regelType = "KJERNE_REGELTYPE"),
    KOMPLETT(path = "komplett", regelType = "KOMPLETT_REGELTYPE"),
}

sealed interface Tilgangsresultat {
    data object Innvilget : Tilgangsresultat

    data class Avvist(
        val kode: String?,
        val begrunnelse: String?,
        val kanOverstyres: Boolean,
        val detaljer: TilgangsmaskinAvvisning,
    ) : Tilgangsresultat
}

/** application/problem+json ved 403 fra tilgangsmaskin. */
@Serializable
data class TilgangsmaskinAvvisning(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val instance: String? = null,
    val brukerIdent: String? = null,
    val navIdent: String? = null,
    val traceId: String? = null,
    val begrunnelse: String? = null,
    val kanOverstyres: Boolean? = null,
)

/** Request-element for bulk-oppslag. */
@Serializable
data class BrukerIdOgRegelsett(
    val brukerId: String,
    val type: String,
)

/** 207-respons fra bulk-oppslag. */
@Serializable
data class AggregertBulkRespons(
    val ansattId: String? = null,
    val resultater: List<EnkeltBulkRespons> = emptyList(),
) {
    val godkjente: List<EnkeltBulkRespons> get() = resultater.filter { it.status == 204 }
    val avviste: List<EnkeltBulkRespons> get() = resultater.filter { it.status == 403 }
    val ukjente: List<EnkeltBulkRespons> get() = resultater.filter { it.status == 404 }
}

@Serializable
data class EnkeltBulkRespons(
    val brukerId: String,
    val status: Int,
    val detaljer: TilgangsmaskinAvvisning? = null,
) {
    val innvilget: Boolean get() = status == 204
}

class TilgangsmaskinException(message: String) : RuntimeException(message)
