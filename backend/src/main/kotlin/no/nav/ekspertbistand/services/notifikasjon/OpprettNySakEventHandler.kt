package no.nav.ekspertbistand.services.notifikasjon

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.runBlocking
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.defaultHttpClient
import no.nav.ekspertbistand.services.IdempotencyGuard
import no.nav.ekspertbistand.skjema.DTO
import java.util.*

private const val nySakSubTask = "notifikasjonsplatform_ny_sak"
private const val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"

class OpprettNySakEventHandler(
    private val produsentApiKlient: ProdusentApiKlient,
    private val idempotencyGuard: IdempotencyGuard
) : EventHandler<EventData.SkjemaInnsendt> {

    // DO NOT CHANGE THIS!
    override val id: String = "8642b600-2601-47e2-9798-5849bb362433"

    // må være suspending
    override fun handle(event: Event<EventData.SkjemaInnsendt>): EventHandledResult {
        return runBlocking {
            handle2(event)
        }
    }

    suspend fun handle2(event: Event<EventData.SkjemaInnsendt>): EventHandledResult {
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
                grupperingsid = UUID.randomUUID().toString(),
                merkelapp = "Ekspertbistand",
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tittel = "Søknad om ekspertbistand",
                lenke = "https://ekspertbistand.nav.no/skjema/",
            )
            Result.success("Opprettet beskjed for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }


    private suspend fun nyBeskjed(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                grupperingsid = UUID.randomUUID().toString(),
                merkelapp = "Ekspertbistand",
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tekst = "Skjema innsendt",
                lenke = "https://ekspertbistand.nav.no/skjema/"
            )
            Result.success("Opprettet beskjed for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
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
    val idempotencyGuard = dependencies.resolve<IdempotencyGuard>()
    val tokenProvider = dependencies.resolve<TokenProvider>()
    val produsentApiKlient = ProdusentApiKlient(tokenProvider, httpClient)

    dependencies.provide<OpprettNySakEventHandler> {
        OpprettNySakEventHandler(
            produsentApiKlient = produsentApiKlient,
            idempotencyGuard = idempotencyGuard
        )
    }
}