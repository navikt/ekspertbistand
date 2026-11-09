package no.nav.ekspertbistand.infrastruktur

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Appene som får kalle backend, per token-type. Backend avviser gyldige tokens fra andre apper,
 * slik at søknadsløsningen og saksbehandlingsløsningen ikke kan bruke hverandres API-er.
 *
 * Sammenligningen er alltid eksakt. Prefiks-treff ville sluppet inn f.eks. `ekspertbistand-frontend-e2e-mock`.
 */
data class TillatteKlienter(
    /** TokenX-claimet `client_id`, på formen `<cluster>:<namespace>:<app>`. */
    val tokenX: Set<String>,
    /** Entra ID-claimet `azp`: klient-ID (UUID) til appen som ba om tokenet. Aldri `azp_name`. */
    val azureAd: Set<String>,
) {
    companion object {
        private val log = logger()

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Kalles i main() før serveren starter, slik at feil konfigurasjon stopper deployen
         * i stedet for å gi feil på første request.
         */
        fun nais(): TillatteKlienter = fra(
            cluster = System.getenv("NAIS_CLUSTER_NAME"),
            preAuthorizedAppsJson = System.getenv("AZURE_APP_PRE_AUTHORIZED_APPS"),
        )

        /**
         * Entra-UUID-ene slås opp på eksakt appnavn i [preAuthorizedAppsJson], som Nais fyller ut fra
         * `accessPolicy.inbound`. En app som ikke står i inbound, får dermed ingen UUID og blir avvist.
         */
        fun fra(cluster: String?, preAuthorizedAppsJson: String?): TillatteKlienter {
            val appnavn = when (cluster) {
                "dev-gcp" -> Appnavn(
                    tokenX = setOf("dev-gcp:fager:ekspertbistand-frontend"),
                    azureAd = setOf(
                        "dev-gcp:fager:ekspertbistand-saksbehandling",
                        // Manuell testing i dev. Står bare i dev-inbound.
                        "dev-gcp:nais:azure-token-generator",
                    ),
                )

                "prod-gcp" -> Appnavn(
                    tokenX = setOf("prod-gcp:fager:ekspertbistand-frontend"),
                    azureAd = setOf("prod-gcp:fager:ekspertbistand-saksbehandling"),
                )

                else -> error("Ukjent cluster '$cluster'. Kan ikke avgjøre hvilke klienter som får kalle backend.")
            }

            requireNotNull(preAuthorizedAppsJson) { "AZURE_APP_PRE_AUTHORIZED_APPS mangler" }
            val preAuthorized = json.decodeFromString<List<PreAuthorizedApp>>(preAuthorizedAppsJson)
            preAuthorized.forEach {
                require(it.clientId.isNotBlank()) { "AZURE_APP_PRE_AUTHORIZED_APPS har tom clientId for '${it.name}'" }
            }

            val funnet = preAuthorized.filter { it.name in appnavn.azureAd }
            val funnetNavn = funnet.map { it.name }.toSet()
            log.info(
                "Tillatte klienter: tokenX={}, azureAd={}, azureAd uten clientId={}",
                appnavn.tokenX,
                funnetNavn,
                appnavn.azureAd - funnetNavn,
            )

            return TillatteKlienter(
                tokenX = appnavn.tokenX,
                azureAd = funnet.map { it.clientId }.toSet(),
            )
        }
    }

    private data class Appnavn(val tokenX: Set<String>, val azureAd: Set<String>)

    @Serializable
    private data class PreAuthorizedApp(val name: String, val clientId: String)
}
