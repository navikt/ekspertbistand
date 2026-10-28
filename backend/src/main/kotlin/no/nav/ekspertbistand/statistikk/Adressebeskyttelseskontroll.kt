package no.nav.ekspertbistand.statistikk

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.ekspertbistand.infrastruktur.NaisEnvironment
import no.nav.ekspertbistand.infrastruktur.basedOnEnvSuspending
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import kotlin.time.Duration.Companion.hours

/**
 * Projeksjonen sjekker PDL bare når en hendelse kommer. Denne kontrollen fanger deltakere som får
 * adressebeskyttelse senere, også etter at saken er avsluttet, og holder alle behandlingene deres utenfor viewet.
 */
class Adressebeskyttelseskontroll(
    private val adressebeskyttede: AdressebeskyttedeBehandlinger,
    private val adressebeskyttelse: AdressebeskyttelseOppslag,
) {
    private val log = logger()

    /** Registrerer det den finner, også når enkelte oppslag feiler. Feiler så, slik at feilen blir synlig. */
    suspend fun kjor() {
        var feilede = 0
        val nye = adressebeskyttede.utenAdressebeskyttelse().entries
            .groupBy({ it.value }, { it.key })
            .filter { (fnr, _) ->
                try {
                    adressebeskyttelse.harAdressebeskyttelse(fnr)
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    feilede++
                    false
                }
            }
            .values.flatten()

        adressebeskyttede.registrer(nye)
        log.info("Adressebeskyttelseskontroll registrerte ${nye.size} nye adressebeskyttede behandlinger")
        check(feilede == 0) { "Oppslag mot PDL feilet for $feilede personer i adressebeskyttelseskontrollen" }
    }

    companion object {
        val INTERVALL = 1.hours
    }
}

suspend fun Application.configureAdressebeskyttelseskontroll() {
    basedOnEnvSuspending(
        // Projeksjonen kjører ikke i prod ennå, se configureProjectionBuilders.
        prod = {},
        other = {
            val kontroll = dependencies.create(Adressebeskyttelseskontroll::class)
            launch {
                while (true) {
                    try {
                        kontroll.kjor()
                    } catch (e: Exception) {
                        e.rethrowIfCancellation()
                        logger().error("Adressebeskyttelseskontrollen feilet", e)
                    }
                    delay(Adressebeskyttelseskontroll.INTERVALL)
                }
            }
        },
    )
}
