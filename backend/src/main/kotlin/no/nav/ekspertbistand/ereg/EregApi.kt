package no.nav.ekspertbistand.ereg

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.skjema.subjectToken

@Serializable
data class AdresseResponse(val adresse: String)

private val orgnrRegex = Regex("^\\d{9}$")

suspend fun Application.configureEregApiV1() {
    val altinnTilgangerClient = dependencies.resolve<AltinnTilgangerClient>()
    val eregClient = dependencies.resolve<EregClient>()
    val log = logger()

    routing {
        authenticate(TOKENX_PROVIDER) {
            get("/api/ereg/{orgnr}/adresse") {
                val orgnr = call.parameters["orgnr"]
                if (orgnr == null || !orgnrRegex.matches(orgnr)) {
                    call.respond(HttpStatusCode.BadRequest, "ugyldig orgnr")
                    return@get
                }

                val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
                if (!tilganger.harTilgang(orgnr)) {
                    call.respond(
                        status = HttpStatusCode.Forbidden,
                        message = "bruker har ikke tilgang til organisasjon"
                    )
                    return@get
                }

                val adresse = runCatching {
                    eregClient.hentForretningsadresse(orgnr).firstNotNullOfOrNull { it.toSingleLine() }
                }.onFailure { feil ->
                    log.warn("Klarte ikke hente forretningsadresse fra EREG for {}", orgnr, feil)
                }.getOrNull()

                if (adresse == null) {
                    call.respond(HttpStatusCode.NotFound, "adresse ikke funnet")
                    return@get
                }

                call.respond(AdresseResponse(adresse))
            }
        }
    }
}

private fun Postadresse.toSingleLine(): String? = adresseTilSingleLine(
    adresselinje1,
    adresselinje2,
    adresselinje3,
    postnummer,
    poststed
)

private fun Forretningsadresse.toSingleLine(): String? = adresseTilSingleLine(
    adresselinje1,
    adresselinje2,
    adresselinje3,
    postnummer,
    poststed
)

private fun adresseTilSingleLine(
    linje1: String?,
    linje2: String?,
    linje3: String?,
    postnummer: String?,
    poststed: String?
): String? {
    val poststedDel = listOfNotNull(postnummer, poststed)
        .joinToString(" ")
        .trim()
        .ifBlank { null }

    val deler = buildList {
        listOf(linje1, linje2, linje3)
            .mapNotNull { it?.trim() }
            .filter { it.isNotBlank() }
            .forEach { add(it) }
        poststedDel?.let { add(it) }
    }

    return deler.joinToString(", ").ifBlank { null }
}
