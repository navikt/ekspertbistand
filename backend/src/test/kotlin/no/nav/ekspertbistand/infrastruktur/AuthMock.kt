package no.nav.ekspertbistand.infrastruktur

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer


fun Application.mockTokenXAuthentication(tokenToPidMapping: Map<String, TokenXPrincipal>) {
    install(Authentication) {
        bearer(TOKENX_PROVIDER) {
            authenticate { credentials ->
                tokenToPidMapping[credentials.token]
            }
        }
    }
}

val mockTokenXPrincipal = TokenXPrincipal(
    clientId = "test",
    pid = "0",
    subjectToken = "dummy_token",
)