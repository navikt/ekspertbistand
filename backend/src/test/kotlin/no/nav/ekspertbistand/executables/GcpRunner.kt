package no.nav.fager.executable

import kotlinx.coroutines.runBlocking
import no.nav.ekspertbistand.infrastruktur.AuthClient
import no.nav.ekspertbistand.infrastruktur.AuthConfig
import no.nav.ekspertbistand.infrastruktur.AzureAdAuthClient
import no.nav.ekspertbistand.infrastruktur.IdentityProvider
import no.nav.ekspertbistand.infrastruktur.defaultHttpClient
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.fager.DevGcpEnv
import java.net.URI


fun main() = runBlocking {
    val gcpEnv = DevGcpEnv()
//    val gcpEnv = ProdGcpEnv()
    val texasEnv = gcpEnv.getEnvVars("NAIS_TOKEN_")
    URI(texasEnv["NAIS_TOKEN_ENDPOINT"]!!).let { uri ->
        try {
            uri.toURL().openConnection().connect()
            println("""texas is available at $uri""")
        } catch (e: Exception) {
            println("")

            println(
                """
        ######
        # Failed to connect to $uri - ${e.message}
        # 
        # Connecting to altinn 3 via devgcp requires port forwarding for texas.
        #
        # E.g: kubectl port-forward ${gcpEnv.getPods().first()} ${uri.port}
        ######
        
        An attempt at port forward will be made for you now:
        
            """.trimIndent()
            )

            gcpEnv.portForward(uri.port) {
                try {
                    uri.toURL().openConnection().connect()
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    val authClient = AzureAdAuthClient(
        AuthConfig(
            tokenEndpoint = texasEnv["NAIS_TOKEN_ENDPOINT"]!!,
            tokenExchangeEndpoint = texasEnv["NAIS_TOKEN_EXCHANGE_ENDPOINT"]!!,
            tokenIntrospectionEndpoint = texasEnv["NAIS_TOKEN_INTROSPECTION_ENDPOINT"]!!,
        ),
        defaultHttpClient()
    )
    val pdlKlient = PdlApiKlient(authClient, defaultHttpClient())

    pdlKlient.hentAdressebeskyttelse("12345612345")

    Unit
}

