package no.nav.ekspertbistand.services.produsent

import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class ProdusentClient {

    val client = HttpClient()

    suspend fun opprettSak(sak: NySakInput): Result<HttpStatusCode> {
        client
    }
}