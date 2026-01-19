package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.arena.TilsagnData
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
import no.nav.ekspertbistand.skjema.DTO
import no.nav.ekspertbistand.skjema.kvitteringsLenke
import no.nav.ekspertbistand.tilsagndata.concat
import org.jetbrains.exposed.v1.jdbc.Database


/**
 * Når en søknad, innsendt via ekspertbistand på arbeidsgiver.nav.no,
 * er godkjent i arena så ender det med en event av typen [EventData.TilskuddsbrevJournalfoert]
 * Denne handleren oppretter da en sak og en beskjed i notifikasjonsplatformen for den godkjente søknaden.
 */
class VarsleArbeidsgiverSoknadGodkjent(
    private val produsentApiKlient: ProdusentApiKlient,
    database: Database
) : EventHandler<EventData.TilskuddsbrevJournalfoert> {
    override val id: String = "VarsleArbeidsgiverSoknadGodkjent"
    override val eventType = EventData.TilskuddsbrevJournalfoert::class

    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"
    private val nystatusSakSubTast = "notifikasjonsplatform_ny_status_sak"

    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.TilskuddsbrevJournalfoert>): EventHandledResult {
        val skjema = event.data.skjema
        if (skjema.id == null) {
            return unrecoverableError("Skjema mangler id")
        }

        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            nyBeskjed(skjema, event.data.tilsagnData).fold(
                onSuccess = { idempotencyGuard.guard(event, nyBeskjedSubTask) },
                onFailure = {
                    return transientError(
                        "Klarte ikke opprette ny beskjed i notifikasjonsplatform", it
                    )
                }
            )
        }

        if (!idempotencyGuard.isGuarded(event.id, nystatusSakSubTast)) {
            nyStatusSak(skjema).fold(
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

    private suspend fun nyBeskjed(skjema: DTO.Skjema, tilsagnData: TilsagnData): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                grupperingsid = skjema.id!!,
                eksternId = "${skjema.id}-godkjent-${tilsagnData.tilsagnNummer.concat()}",
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tekst = "Søknaden er godkjent og ekspertbistand kan nå tas i bruk.",
                lenke = skjema.kvitteringsLenke,
                eksternVarsel = EksterntVarsel(
                    epostTittel = "Nav – angående søknad om ekspertbistand",
                    epostHtmlBody = "${skjema.virksomhet.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det.",
                    smsTekst = "${skjema.virksomhet.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det.",
                )
            )
            Result.success("Opprettet beskjed for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private suspend fun nyStatusSak(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.nyStatusSak(
                grupperingsid = skjema.id!!,
                status = SaksStatus.FERDIG,
                statusTekst = "Søknad godkjent"
            )
            Result.success("Oppdaterte sakstatus for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}