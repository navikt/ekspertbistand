package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.dokarkiv.AvsenderMottaker
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.FagsakIdService
import no.nav.ekspertbistand.dokarkiv.JournalpostType
import no.nav.ekspertbistand.dokarkiv.Sak
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.norg.BehandlendeEnhetUtleder
import no.nav.ekspertbistand.norg.ManglerDataForBehandlendeEnhetException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass

private const val publiserJournalpostEventSubtask = "journalpost_opprettet_event"
private const val tittel = "Søknad om ekspertbistand"

/**
 * Når en søknad om ekspertbistand er innsendt, genereres en PDF og så journalføres dette i DokArkiv.
 *
 * Før journalføring utledes behandlende enhet med [BehandlendeEnhetUtleder], basert på søkers
 * adressebeskyttelse og geografiske tilknytning.
 *
 * Etter journalføring publiseres [EventData.InnsendtSoknadJournalfoert] med journalpostId, dokumentId og
 * behandlende enhet mappet til Arena-enhetsnummer.
 */
class JournalfoerInnsendtSoknad(
    private val dokumentService: DokumentService,
    private val dokArkivClient: DokArkivClient,
    private val behandlendeEnhetUtleder: BehandlendeEnhetUtleder,
    private val fagsakIdService: FagsakIdService,
    private val database: Database,
) : EventHandler<EventData.SoknadInnsendt> {
    override val id: String = "Journalfoer Innsendt Soknad"
    override val eventType: KClass<EventData.SoknadInnsendt> = EventData.SoknadInnsendt::class

    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.SoknadInnsendt>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, publiserJournalpostEventSubtask)) {
            return success()
        }

        val soknad = event.data.soknad
        val soknadId = soknad.id ?: return unrecoverableError("Søknad mangler id")

        val behandlendeEnhet = try {
            behandlendeEnhetUtleder.utled(soknad)
        } catch (e: ManglerDataForBehandlendeEnhetException) {
            return transientError("Mangler data for å hente behandlende enhet", e)
        } catch (e: Exception) {
            return transientError("Feil ved henting av behandlende enhet", e)
        }

        val soknadPdf = runCatching { dokumentService.genererSoknadPdf(soknad) }
            .getOrElse { e ->
                return transientError("Klarte ikke generere søknad-PDF", e)
            }

        val journalpostResponse = runCatching {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = tittel,
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                sak = Sak.FagSak(fagsakId = fagsakIdService.opprettEllerHentFagsakId(soknadId = soknadId)),
                eksternReferanseId = soknadId,
                dokumentPdfAsBytes = soknadPdf,
                journalposttype = JournalpostType.INNGAAENDE,
                avsenderMottaker = AvsenderMottaker.orgnr(soknad.virksomhet.virksomhetsnummer),
            )
        }.getOrElse { e ->
            return transientError("Feil ved opprettelse av journalpost", e)
        }

        if (!journalpostResponse.journalpostferdigstilt) {
            return transientError("Journalpost ikke ferdigstilt")
        }

        val dokumentInfoId = journalpostResponse.dokumenter.firstOrNull()?.dokumentInfoId?.toIntOrNull()
            ?: return unrecoverableError("DokArkiv mangler dokumentInfoId")
        val journalpostId = journalpostResponse.journalpostId.toIntOrNull()
            ?: return unrecoverableError("DokArkiv mangler gyldig journalpostId")

        transaction(database) {
            publishEventQueue(
                EventData.InnsendtSoknadJournalfoert(
                    soknad = soknad,
                    dokumentId = dokumentInfoId,
                    journaldpostId = journalpostId,
                    behandlendeEnhetId = behandlendeEnhet,
                ),
            )
        }

        idempotencyGuard.guard(event, publiserJournalpostEventSubtask)

        return success()
    }
}
