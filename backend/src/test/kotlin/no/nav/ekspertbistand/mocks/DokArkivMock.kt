package no.nav.ekspertbistand.mocks

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostResponse

@Serializable
data class OpprettJournalpostRequest(
    val bruker: Ident,
    val avsenderMottaker: Ident,
    val eksternReferanseId: String,
    val journalfoerendeEnhet: String,
    val tittel: String,
    val dokumenter: List<Dokument>,
    val journalposttype: String,
    val kanal: String,
    val tema: String,
    val sak: Sak,
) {
    @Serializable
    data class Ident(
        val id: String,
        val idType: String,
    )

    @Serializable
    data class Dokument(
        val tittel: String,
        val brevkode: String,
        val dokumentvarianter: List<DokumentVariant>,
    )

    @Serializable
    data class DokumentVariant(
        val fysiskDokument: String,
        val filtype: String,
        val variantformat: String,
    )

    @Serializable
    data class Sak(
        val sakstype: String,
    )
}

fun ApplicationTestBuilder.mockDokArkiv(
    status: HttpStatusCode = HttpStatusCode.Created,
    responseProvider: (OpprettJournalpostRequest) -> OpprettJournalpostResponse,
) {
    externalServices {
        hosts(DokArkivClient.ingress) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post(DokArkivClient.API_PATH) {
                    val payload = call.receive<OpprettJournalpostRequest>()
                    val response = responseProvider(payload)
                    call.respond(status, response)
                }
            }
        }
    }
}
