package no.nav.ekspertbistand.services.notifikasjon

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.infrastruktur.IdentityProvider
import no.nav.ekspertbistand.infrastruktur.TexasAuthConfig
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.defaultHttpClient
import no.nav.ekspertbistand.services.IdempotencyGuard
import no.nav.ekspertbistand.skjema.DTO

private const val nySakSubTask = "notifikasjonsplatform_ny_sak"
private const val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"

class OpprettNySakEventHandler(
    private val produsentApiKlient: ProdusentApiKlient,
    private val idempotencyGuard: IdempotencyGuard
) : EventHandler<EventData.SkjemaInnsendt> {

    // DO NOT CHANGE THIS!
    override val id: String = "8642b600-2601-47e2-9798-5849bb362433"

    override suspend fun handle(event: Event<EventData.SkjemaInnsendt>): EventHandledResult {
        if (!idempotencyGuard.isGuarded(event.id, nySakSubTask)) {
            nySak(event.data.skjema).fold(
                onSuccess = { idempotencyGuard.guard(event, nySakSubTask) },
                onFailure = { return EventHandledResult.TransientError(it.message!!) }
            )
        }
        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            nyBeskjed(event.data.skjema).fold(
                onSuccess = { idempotencyGuard.guard(event, nyBeskjedSubTask) },
                onFailure = { return EventHandledResult.TransientError(it.message!!) }
            )
        }

        return EventHandledResult.Success()
    }

    private suspend fun nySak(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.opprettNySak(
                grupperingsid = skjema.id!!,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tittel = "Ekspertbistand ${skjema.ansatt.navn} f. ${skjema.ansatt.fnr.tilFødselsdato()}",
                lenke = ""
            )
            Result.success("Opprettet sak for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }


    private suspend fun nyBeskjed(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                grupperingsid = skjema.id!!,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tekst = "Nav har mottatt deres søknad om ekspertbistand.",
                lenke = "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand/skjema/:id" //TODO: håndter// produksjonslink når prod er klart
            )
            Result.success("Opprettet beskjed for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}

private fun String.tilFødselsdato(): String {
    if (length != 11) throw IllegalArgumentException("Fødselsnummer må være eksakt 11 tegn langt")
    return "${substring(0, 2)}.${substring(2, 4)}.${substring(4, 6)}"
}

suspend fun Application.configureOpprettNySakEventHandler(
    httpClient: HttpClient = defaultHttpClient(customizeMetrics = {
        clientName = "notifikasjon.produsent.api.klient"
    }) {
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
        }
    }
) {
    val authConfig = TexasAuthConfig.nais()
    val tokenProvider = authConfig.authClient(IdentityProvider.AZURE_AD)
    val idempotencyGuard = dependencies.resolve<IdempotencyGuard>()
    val produsentApiKlient = ProdusentApiKlient(tokenProvider, httpClient)

    dependencies.provide<OpprettNySakEventHandler> {
        OpprettNySakEventHandler(
            produsentApiKlient = produsentApiKlient,
            idempotencyGuard = idempotencyGuard
        )
    }
}