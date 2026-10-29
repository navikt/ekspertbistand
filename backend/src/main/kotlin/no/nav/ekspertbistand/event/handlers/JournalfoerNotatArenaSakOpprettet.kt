package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.dokarkiv.AvsenderMottaker
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.FagsakIdService
import no.nav.ekspertbistand.dokarkiv.JournalpostType
import no.nav.ekspertbistand.dokarkiv.Sak
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.KvitteringPublisert
import no.nav.ekspertbistand.event.publishEventQueue
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


/**
 * Når vi mottar en søknad så oppretter vi en tiltaksgjennomføring i Arena.
 * Dette resulterer i eventen [no.nav.ekspertbistand.event.EventData.TiltaksgjennomforingOpprettet]
 *
 * Denne handleren oppretter da et notat i gosys med hvilket saksnummer som er opprettet i Arena
 * Når alt har lyktes, publiserer handleren [EventData.ArenaNotatJournalfoert] som kvittering,
 * men bare hvis [no.nav.ekspertbistand.event.KvitteringPublisert] ikke har nøkkelen fra før.
 */
class JournalfoerNotatArenaSakOpprettet(
    private val dokumentService: DokumentService,
    private val dokArkivClient: DokArkivClient,
    private val fagsakIdService: FagsakIdService,
    private val database: Database,
) : EventHandler<EventData.TiltaksgjennomforingOpprettet> {

    override val id: String = "JournalfoerNotatArenaSakOpprettet"
    override val eventType = EventData.TiltaksgjennomforingOpprettet::class


    override suspend fun handle(event: Event<EventData.TiltaksgjennomforingOpprettet>): EventHandledResult {
        val soknad = event.data.soknad
        if (soknad.id == null) {
            return unrecoverableError("Søknad mangler id")
        }

        val notatPdf = try {
            dokumentService.genererArenaNotatPdf(event.data.saksnummer, event.data.tiltaksgjennomfoeringId.toString())
        } catch (e: Exception) {
            return transientError("Klarte ikke generere notat-PDF", e)
        }

        val journalpostResponse = try {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = "Saken er opprettet i Arena under saksnummer: ${event.data.saksnummer}",
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                sak = Sak.FagSak(fagsakId = fagsakIdService.opprettEllerHentFagsakId(soknadId = soknad.id)),
                eksternReferanseId = KvitteringPublisert.arenaNotatNokkel(
                    event.data.saksnummer,
                    event.data.tiltaksgjennomfoeringId
                ),
                dokumentPdfAsBytes = notatPdf,
                journalposttype = JournalpostType.NOTAT,
                avsenderMottaker = null,
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

        transaction(database) {
            val nyKvittering = KvitteringPublisert.registrer(
                nokkel = KvitteringPublisert.arenaNotatNokkel(event.data.saksnummer, event.data.tiltaksgjennomfoeringId),
                soknadId = soknad.id,
                type = KvitteringPublisert.Type.ARENA_NOTAT_JOURNALFOERT,
            )
            if (nyKvittering) {
                publishEventQueue(
                    EventData.ArenaNotatJournalfoert(
                        soknadId = soknad.id,
                        saksnummer = event.data.saksnummer,
                        journalpostId = journalpostResponse.journalpostId
                    )
                )
            }
        }
        return success()
    }
}