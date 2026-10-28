package no.nav.ekspertbistand

import no.nav.ekspertbistand.infrastruktur.DbConfig

/**
 * Backend for `ekspertbistand-saksbehandling-mock` i dev-gcp. Kjører samme oppsett som [main] i LocalApplication,
 * men mot Cloud SQL fra Nais. Databasen tømmes og fylles med testdata ved hver oppstart.
 * Se specifications/saksbehandling_mock_mot_mocka_backend.md.
 */
fun main() {
    val dbConfig = DbConfig.nais().apply {
        flywayConfig.cleanDisabled(false)
    }
    dbConfig.flywayAction {
        clean()
        migrate()
    }
    startLokalApplikasjon(dbConfig)
}
