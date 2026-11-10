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
            val appnavn = appnavnFor(cluster)
            val azureAdApper = lesPreAuthorizedApps(preAuthorizedAppsJson).filter { it.name in appnavn.azureAd }
            loggTillatteKlienter(appnavn, azureAdApper)

            return TillatteKlienter(
                tokenX = appnavn.tokenX,
                azureAd = azureAdApper.map { it.clientId }.toSet(),
            )
        }

        private fun appnavnFor(cluster: String?): Appnavn =
            when (cluster) {
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

        /** Alle apper i `accessPolicy.inbound`. Feil format stopper oppstarten. */
        private fun lesPreAuthorizedApps(preAuthorizedAppsJson: String?): List<PreAuthorizedApp> {
            requireNotNull(preAuthorizedAppsJson) { "AZURE_APP_PRE_AUTHORIZED_APPS mangler" }
            return json.decodeFromString<List<PreAuthorizedApp>>(preAuthorizedAppsJson).onEach {
                require(it.clientId.isNotBlank()) { "AZURE_APP_PRE_AUTHORIZED_APPS har tom clientId for '${it.name}'" }
            }
        }

        private fun loggTillatteKlienter(appnavn: Appnavn, azureAdApper: List<PreAuthorizedApp>) {
            val funnetNavn = azureAdApper.map { it.name }.toSet()
            log.info(
                "Tillatte klienter: tokenX={}, azureAd={}, azureAd uten clientId={}",
                appnavn.tokenX,
                funnetNavn,
                appnavn.azureAd - funnetNavn,
            )
        }
    }

    private data class Appnavn(val tokenX: Set<String>, val azureAd: Set<String>)

    @Serializable
    private data class PreAuthorizedApp(val name: String, val clientId: String)
}
