package no.nav.ekspertbistand.arena

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultJson


const val EKSPERTBISTAND_TILTAKSKODE = "EKSPEBIST"

/**
 * løsningsbeskrivelse:
 * https://confluence.adeo.no/spaces/TEAMARENA/pages/760709364/ARENA-11087+-+03+-+L%C3%B8sningsbeskrivelse
 *
 * openapi:
 * https://arena-api-q2.dev-fss-pub.nais.io/v3/api-docs
 *
 * swagger:
 * https://arena-api-q2.dev-fss-pub.nais.io/swagger-ui/index.html#/Tiltaksgjennomf%C3%B8ringer/opprettTiltaksgjennomfoering
 *
 * use arena pub fss ingress: https://<app>.<dev|prod>-fss-pub.nais.io
 * https://doc.nais.io/workloads/explanations/migrating-to-gcp/#how-do-i-reach-an-application-found-on-premises-from-my-application-in-gcp
 */
class ArenaClient(
    val tokenProvider: AzureAdTokenProvider,
    defaultHttpClient: HttpClient
) {
    companion object {
        val targetAudience = basedOnEnv(
            prod = "api://prod-fss.teamarenanais.arena-api/.default",
            dev = "api://dev-fss.teamarenanais.arena-api-q2/.default",
            other = "api://mock.arena-api/.default",
        )

        val ingress = basedOnEnv(
            prod = "https://arena-api.prod-fss-pub.nais.io",
            dev = "https://arena-api-q2.dev-fss-pub.nais.io",
            other = "http://arena-api.mock.svc.cluster.local",
        )

        const val API_PATH = "/api/v1/tiltaksgjennomfoering"
    }

    val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "arena-api.client"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    /**
     * Oppretter en tiltaksgjennomføring i Arena for Ekspertbistand
     * @return sakId for den opprettede tiltaksgjennomføringen
     */
    suspend fun opprettTiltaksgjennomfoering(data: OpprettEkspertbistand) =
        httpClient.post {
        url {
            takeFrom(ingress)
            path(API_PATH)
        }
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json)
        bearerAuth(
            tokenProvider.token(targetAudience).fold(
                { it.accessToken },
                { throw Exception("Failed to get token: ${it.error}") }
            )
        )

        setBody(data.arenaRequest)
    }.body<OpprettTiltaksgjennomfoeringResponse>()
}

data class OpprettEkspertbistand(
    val behandlendeEnhetId: String,
    val virksomhetsnummer: String,
    val ansattFnr: String,
    val periodeFom: LocalDate,
    val journalpostId: Int,
    val dokumentId: Int,
) {
    val arenaRequest
        get() = OpprettTiltaksgjennomfoeringRequest(
            bedriftsnummer = virksomhetsnummer,
            tiltaksgjennomfoering = Tiltaksgjennomfoering(
                behandlendeEnhetId = behandlendeEnhetId,
                gjennomfoeringsperiode = Gjennomforingsperiode(fom = periodeFom),
                person = Person(ident = ansattFnr),
            ),
            dokumentreferanse = Dokumentreferanse(
                journalpostId = journalpostId,
                dokumentId = dokumentId,
            )
        )
}

@Serializable
data class OpprettTiltaksgjennomfoeringRequest(
    val bedriftsnummer: String,
    val tiltaksgjennomfoering: Tiltaksgjennomfoering,
    val dokumentreferanse: Dokumentreferanse
)

@Serializable
data class Tiltaksgjennomfoering(
    val tiltaksvariant: String = EKSPERTBISTAND_TILTAKSKODE,
    val behandlendeEnhetId: String,
    val gjennomfoeringsperiode: Gjennomforingsperiode,
    val person: Person,
)

@Serializable
data class Gjennomforingsperiode(
    val fom: LocalDate,
    val tom: LocalDate? = null,
)

@Serializable
data class Person(
    val ident: String,
)

@Serializable
data class Dokumentreferanse(
    val journalpostId: Int,
    val dokumentId: Int,
)

@Serializable
data class OpprettTiltaksgjennomfoeringResponse(
    /**
     * saksnummer = aar + loepenrSak på formatet 'YYYY#', mao ingen padding
     * eks: 202542,20251012345 etc
     */
    val saksnummer: Saksnummer,

    /**
     * unik identifikator for tiltaksgjennomføringen i Arena
     */
    val tiltaksgjennomfoeringId: Int,
)

typealias Saksnummer = String

val Saksnummer.aar: Int
    get() = take(4).toInt()

val Saksnummer.loepenrSak: Int
    get() = removeRange(0..3).toInt()

fun asSaksnummer(aar: Int, loepenrSak: Int) : Saksnummer = "$aar$loepenrSak"