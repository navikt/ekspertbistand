package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.util.*

/** Leser `sakId` fra stien. Svarer 400 og returnerer null hvis den ikke er en gyldig UUID. */
internal suspend fun ApplicationCall.sakIdParameter(): UUID? {
    val sakId = parameters["sakId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (sakId == null) {
        respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig sakId"))
    }
    return sakId
}
