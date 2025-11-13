package no.nav.ekspertbistand.arena

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultHttpClient


const val EKSPERTBISTAND_TILTAKSKODE = "EKSPERTBIST"

/**
 * use arena pub fss ingress: https://<app>.<dev|prod>-fss-pub.nais.io
 * https://doc.nais.io/workloads/explanations/migrating-to-gcp/#how-do-i-reach-an-application-found-on-premises-from-my-application-in-gcp
 */
class ArenaClient(
    val authClient: TokenProvider,
    val httpClient: HttpClient = defaultHttpClient({
        clientName = "arena-api.client"
    }) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }
) {
    companion object {
        val targetAudience = basedOnEnv(
            prod = "api://prod-fss.teamarenanais.arena-api/.default",
            dev = "api://dev-fss.teamarenanais.arena-api-q2/.default", //TODO: hvilket q miljø skal vi bruke?
            other = "api://mock.arena-api/.default",
        )

        val ingress = basedOnEnv(
            prod = "https://arena-api.prod-fss-pub.nais.io",
            dev = "https://arena-api-q2.dev-fss-pub.nais.io",
            other = "http://arena-api.mock.svc.cluster.local",
        )

        /**
         * TODO: get correct path when API is defined by team arena
         */
        const val API_PATH = "/api/v1/tiltaksgjennomfoering/opprett"
    }

    /**
     * Oppretter en tiltaksgjennomføring i Arena for Ekspertbistand
     * @return sakId for den opprettede tiltaksgjennomføringen
     * TODO: håndtere feil fra Arena API
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
                authClient.token(targetAudience).fold(
                    { it.accessToken },
                    { throw Exception("Failed to get token: ${it.error}") }
                )
            )

            setBody(data.arenaRequest)
        }.body<OpprettTiltaksgjennomfoeringResponse>().let {
            Saksnummer(it.saksnummer)
        }
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
            behandlendeEnhetId = behandlendeEnhetId,
            gjennomfoeringsperiode = Gjennomforingsperiode(periodeFom),
            personIdent = ansattFnr,
            dokumentreferanse = Dokumentreferanse(
                journalpostId = journalpostId,
                dokumentId = dokumentId,
            )
        )
}

@Serializable
data class OpprettTiltaksgjennomfoeringRequest(
    val bedriftsnummer: String,
    val tiltakskode : String = EKSPERTBISTAND_TILTAKSKODE,
    val behandlendeEnhetId : String,
    val gjennomfoeringsperiode : Gjennomforingsperiode,
    val personIdent : String,
    val dokumentreferanse : Dokumentreferanse
)
@Serializable
data class Dokumentreferanse(
    val journalpostId: Int,
    val dokumentId: Int,
)
@Serializable
data class Gjennomforingsperiode(
    val fom: LocalDate,
    val tom: LocalDate? = null,
)

@Serializable
data class OpprettTiltaksgjennomfoeringResponse(
    /**
     * saksnummer = aar + loepenrSak på formatet 'YYYY#', mao ingen padding
     * eks: 202542,20251012345 etc
     */
    val saksnummer: String
)

data class Saksnummer(
    val saksnummer: String,
    val aar: Int = saksnummer.take(4).toInt(),
    val loepenrSak: Int = saksnummer.removeRange(0..3).toInt(),
)