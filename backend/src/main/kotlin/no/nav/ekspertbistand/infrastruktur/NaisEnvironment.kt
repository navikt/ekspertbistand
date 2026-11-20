package no.nav.ekspertbistand.infrastruktur

object NaisEnvironment {
    val clusterName: String = System.getenv("NAIS_CLUSTER_NAME") ?: ""

    /** TokenX `client_id` til søknadsløsningen. */
    val soknadFrontend = "$clusterName:fager:ekspertbistand-frontend"

    /** Entra `azp_name` til saksbehandlingsløsningen. */
    val saksbehandlingFrontend = "$clusterName:fager:ekspertbistand-saksbehandling"
}


suspend fun <T> basedOnEnvSuspending(
    other: suspend () -> T,
    prod: suspend () -> T = other,
    dev: suspend () -> T = other,
): T =
    when (NaisEnvironment.clusterName) {
        "prod-gcp" -> prod()
        "dev-gcp" -> dev()
        else -> other()
    }

fun <T> basedOnEnv(
    other: () -> T,
    prod: () -> T = other,
    dev: () -> T = other,
): T =
    when (NaisEnvironment.clusterName) {
        "prod-gcp" -> prod()
        "dev-gcp" -> dev()
        else -> other()
    }

fun <T> basedOnEnv(
    other: T,
    prod: T = other,
    dev: T = other,
): T =
    when (NaisEnvironment.clusterName) {
        "prod-gcp" -> prod
        "dev-gcp" -> dev
        else -> other
    }