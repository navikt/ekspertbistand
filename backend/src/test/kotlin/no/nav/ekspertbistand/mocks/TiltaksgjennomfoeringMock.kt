package no.nav.ekspertbistand.mocks

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.arena.OpprettTiltaksgjennomfoeringResponse

/**
 * midlertidig mock laget basert på SOAP spesifikasjonen for opprettTiltaksgjennomfoering
 * https://github.com/navikt/tjenestespesifikasjoner/blob/master/nav-behandletiltaksgjennomfoering-v1-tjenestespesifikasjon/src/main/wsdl/no/nav/tjeneste/virksomhet/behandleTiltaksgjennomfoering/v1/BehandleTiltaksgjennomfoeringV1.wsdl
 */
@Serializable
data class OpprettTiltaksgjennomfoeringRequest(
    val bedriftsnummer: String,
    val tiltakskode: String,
    val behandlendeEnhetId: String,
    val gjennomfoeringsperiode: Periode,
    val personIdent: String,
    val dokumentreferanse: Dokumentreferanse
) {

    @Serializable
    data class Dokumentreferanse(
        val journalpostId: Int,
        val dokumentId: Int
    )

    @Serializable
    data class Periode(
        val fom: LocalDate, // ISO8601 date
        val tom: LocalDate? = null // ISO8601 date
    )
}


/**
 * https://github.com/navikt/arena-api/blob/main/src/main/java/no/nav/arena/restapi/api/ApiError.java
 */
@Serializable
data class ApiError(
    // yyyy-MM-dd'T'HH:mm:ss.SSSZ
    val timestamp: String,
    val status: Int,
    val message: String,
    val method: String,
    val path: String,
    val correlationId: String
)



fun ApplicationTestBuilder.mockTiltaksgjennomfoering(
    responseProvider: (OpprettTiltaksgjennomfoeringRequest) -> OpprettTiltaksgjennomfoeringResponse
) {
    externalServices {
        hosts(ArenaClient.ingress) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post(ArenaClient.API_PATH) {
                    val payload = call.receive<OpprettTiltaksgjennomfoeringRequest>()
                    val response = responseProvider(payload)
                    call.respond(response)
                }
            }
        }
    }
}

// language=JSON
val sampleRequestJson = """
{
    "bedriftsnummer": "987654321",
    "tiltakskode": "EKSPEBIST",
    "behandlendeEnhetId": "ENHET123",
    "gjennomfoeringsperiode": {
      "fom": "2025-11-10",
      "tom": "2025-12-10"
    },
    "personIdent": "12345678901",
    "dokumentreferanse": {
      "journalpostId": "12345",
      "dokumentId": "67890"
    }
}
"""

// language=JSON
val sampleResponseJson = """
{
  "saksnummer": "202542"
}
"""
