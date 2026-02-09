package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.notifikasjon.EksterntVarsel
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.SaksStatus
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.kvitteringsLenke
import org.jetbrains.exposed.v1.jdbc.Database


/**
 * Når en søknad er godkjent i arena så ender det med en event av typen [EventData.TilskuddsbrevJournalfoert]
 * Denne handleren oppretter da en sak og en beskjed i notifikasjonsplatformen for den godkjente søknaden.
 */
class VarsleArbeidsgiverSoknadAvlyst(
    private val produsentApiKlient: ProdusentApiKlient,
    database: Database
) : EventHandler<EventData.SoknadAvlystIArena> {
    override val id: String = "Varsle Arbeidsgiver Soknad Avlyst"
    override val eventType = EventData.SoknadAvlystIArena::class

    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"
    private val nystatusSakSubTast = "notifikasjonsplatform_ny_status_sak"

    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.SoknadAvlystIArena>): EventHandledResult {
        val soknad = event.data.soknad
        if (soknad.id == null) {
            return unrecoverableError("soknad.id kan ikke være null")
        }

        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            nyBeskjed(soknad).fold(
                onSuccess = { idempotencyGuard.guard(event, nyBeskjedSubTask) },
                onFailure = {
                    return transientError(
                        "Klarte ikke opprette ny beskjed i notifikasjonsplatform", it
                    )
                }
            )
        }

        if (!idempotencyGuard.isGuarded(event.id, nystatusSakSubTast)) {
            nyStatusSak(soknad).fold(
                onSuccess = { idempotencyGuard.guard(event, nystatusSakSubTast) },
                onFailure = {
                    return transientError(
                        "Klarte ikke oppdatere saksstatus i notifikasjonsplatform", it
                    )
                }
            )
        }

        return success()
    }

    private suspend fun nyBeskjed(soknad: DTO.Soknad): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                grupperingsid = soknad.id!!,
                eksternId = "${soknad.id}-soknad-avlyst",
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                tekst = "Søknaden om ekspertbistand trukket eller avslått.",
                lenke = soknad.kvitteringsLenke,
                eksternVarsel = EksterntVarsel(
                    epostTittel = "Nav – angående søknad om ekspertbistand",
                    epostHtmlBody = "${soknad.virksomhet.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det.",
                    smsTekst = "${soknad.virksomhet.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det.",
                )
            )
            Result.success("Opprettet beskjed for soknad ${soknad.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private suspend fun nyStatusSak(soknad: DTO.Soknad): Result<String> {
        return try {
            produsentApiKlient.nyStatusSak(
                grupperingsid = soknad.id!!,
                status = SaksStatus.FERDIG,
                statusTekst = "Søknad trukket eller avslått"
            )
            Result.success("Oppdaterte sakstatus for soknad ${soknad.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}