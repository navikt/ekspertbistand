package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.dokarkiv.AvsenderMottaker
import no.nav.ekspertbistand.dokarkiv.Bruker
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
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass

private const val publiserJournalpostEventSubtask = "soknad_kopi_deltaker_journalfoert_event"
private const val tittel = "Kopi av søknad om tilskudd til ekspertbistand"

/**
 * Når en søknad om ekspertbistand er innsendt, journalføres en kopi av søknaden med et infoskriv på
 * personmappen til deltakeren, som en utgående og ferdigstilt journalpost.
 *
 * Kopien skjules ikke, heller ikke når deltakeren har adressebeskyttelse, fordi den bare går til
 * deltakeren selv. Journalposten distribueres ikke her. Etter journalføring publiseres
 * [EventData.SoknadKopiDeltakerJournalfoert].
 *
 * Handleren er uavhengig av [JournalfoerInnsendtSoknad], slik at de prøves på nytt hver for seg.
 * `eksternReferanseId` skiller seg fra journalposten på arbeidsgivermappen, slik at dokarkivs
 * duplikatkontroll ikke slår dem sammen.
 */
class JournalfoerSoknadKopiDeltaker(
    private val dokumentService: DokumentService,
    private val dokArkivClient: DokArkivClient,
    private val fagsakIdService: FagsakIdService,
    private val database: Database,
) : EventHandler<EventData.SoknadInnsendt> {
    override val id: String = "Journalfoer Soknad Kopi Deltaker"
    override val eventType: KClass<EventData.SoknadInnsendt> = EventData.SoknadInnsendt::class

    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.SoknadInnsendt>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, publiserJournalpostEventSubtask)) {
            return success()
        }

        val soknad = event.data.soknad
        val soknadId = soknad.id ?: return unrecoverableError("Søknad mangler id")

        val pdf = runCatching { dokumentService.genererSoknadKopiDeltakerPdf(soknad) }
            .getOrElse { e ->
                return transientError("Klarte ikke generere PDF med kopi av søknad til deltaker", e)
            }

        val journalpostResponse = runCatching {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = tittel,
                bruker = Bruker.fnr(soknad.ansatt.fnr),
                sak = Sak.FagSak(fagsakId = fagsakIdService.opprettEllerHentFagsakId(soknadId = soknadId)),
                eksternReferanseId = "$soknadId-kopi-deltaker",
                dokumentPdfAsBytes = pdf,
                journalposttype = JournalpostType.UTGAAENDE,
                avsenderMottaker = AvsenderMottaker.fnr(soknad.ansatt.fnr),
            )
        }.getOrElse { e ->
            return transientError("Feil ved opprettelse av journalpost for kopi av søknad til deltaker", e)
        }

        if (!journalpostResponse.journalpostferdigstilt) {
            return transientError("Journalpost for kopi av søknad til deltaker ikke ferdigstilt")
        }

        val journalpostId = journalpostResponse.journalpostId.toIntOrNull()
            ?: return unrecoverableError("DokArkiv mangler gyldig journalpostId")

        transaction(database) {
            publishEventQueue(
                EventData.SoknadKopiDeltakerJournalfoert(
                    soknad = soknad,
                    journalpostId = journalpostId,
                ),
            )
        }

        idempotencyGuard.guard(event, publiserJournalpostEventSubtask)

        return success()
    }
}
