package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass

private const val publiserJournalpostEventSubtask = "journalpost_opprettet_event"
private const val tittel = "Tilskuddsbrev ekspertbistand"

/**
 * Når en søknad om ekspertbistand godkjennes i Arena blir det opprettet et tilsagn.
 * Dette tilsagnet blir lagt på kafka og plukkes opp i [no.nav.ekspertbistand.arena.ArenaTilsagnsbrevProcessor]
 * som produserer en [no.nav.ekspertbistand.event.EventData.TilskuddsbrevMottatt]-event.
 *
 * Denne handleren tar imot eventen, genererer et tilskuddsbrev i PDF-format og
 * journalfører dette i DokArkiv.
 * Etter journalføring publiseres en ny event [no.nav.ekspertbistand.event.EventData.TilskuddsbrevJournalfoert]
 * som inneholder informasjon om journalpostId og dokumentId.
 */
class JournalfoerTilskuddsbrev(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val database: Database,
) : EventHandler<EventData.TilskuddsbrevMottatt> {
    override val id: String = "Journalfoer Tilskuddsbrev"
    override val eventType: KClass<EventData.TilskuddsbrevMottatt> =
        EventData.TilskuddsbrevMottatt::class

    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.TilskuddsbrevMottatt>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, publiserJournalpostEventSubtask)) {
            return success()
        }

        val skjema = event.data.skjema
        val skjemaId = skjema.id ?: return unrecoverableError("Skjema mangler id")

        val tilsagnPdf = try {
            dokgenClient.genererTilskuddsbrevPdf(event.data.tilsagnData)
        } catch (e: Exception) {
            return transientError("Klarte ikke generere søknad-PDF", e)
        }

        val journalpostResponse = try {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = tittel,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                eksternReferanseId = "${event.data.tilsagnData.tilsagnNummer}-${skjemaId}", //TODO: midlertidig for å ikke få conflict
                dokumentPdfAsBytes = tilsagnPdf,
            )
        } catch (e: Exception) {
            return transientError(
                "Feil ved opprettelse av journalpost",
                e
            )
        }

        if (!journalpostResponse.journalpostferdigstilt) {
            return transientError("Journalpost ikke ferdigstilt")
        }

        val dokumentInfoId = journalpostResponse.dokumenter.firstOrNull()?.dokumentInfoId?.toIntOrNull()
            ?: return unrecoverableError("DokArkiv mangler dokumentInfoId")
        val journalpostId = journalpostResponse.journalpostId.toIntOrNull()
            ?: return unrecoverableError("DokArkiv mangler gyldig journalpostId")

        transaction(database) {
            QueuedEvents.insert {
                it[eventData] = EventData.TilskuddsbrevJournalfoert(
                    skjema = skjema,
                    dokumentId = dokumentInfoId,
                    journaldpostId = journalpostId,
                    tilsagnData = event.data.tilsagnData,
                )
            }
        }
        idempotencyGuard.guard(event, publiserJournalpostEventSubtask)
        return success()
    }
}
