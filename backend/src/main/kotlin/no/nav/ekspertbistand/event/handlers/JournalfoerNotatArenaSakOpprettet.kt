package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.dokarkiv.AvsenderMottaker
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.FagsakIdService
import no.nav.ekspertbistand.dokarkiv.JournalpostType
import no.nav.ekspertbistand.dokarkiv.Sak
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler


/**
 * Når vi mottar en søknad så oppretter vi en tiltaksgjennomføring i Arena.
 * Dette resulterer i eventen [no.nav.ekspertbistand.event.EventData.TiltaksgjennomforingOpprettet]
 *
 * Denne handleren oppretter da et notat i gosys med hvilket saksnummer som er opprettet i Arena
 */
class JournalfoerNotatArenaSakOpprettet(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val fagsakIdService: FagsakIdService,
) : EventHandler<EventData.TiltaksgjennomforingOpprettet> {

    override val id: String = "JournalfoerNotatArenaSakOpprettet"
    override val eventType = EventData.TiltaksgjennomforingOpprettet::class


    override suspend fun handle(event: Event<EventData.TiltaksgjennomforingOpprettet>): EventHandledResult {
        val soknad = event.data.soknad
        if (soknad.id == null) {
            return unrecoverableError("Søknad mangler id")
        }

        val notatPdf = try {
            dokgenClient.genererArenaNotatPdf(event.data.saksnummer, event.data.tiltaksgjennomfoeringId.toString())
        } catch (e: Exception) {
            return transientError("Klarte ikke generere notat-PDF", e)
        }

        val journalpostResponse = try {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = "Saken er opprettet i Arena under saksnummer: ${event.data.saksnummer}",
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                sak = Sak.FagSak(fagsakId = fagsakIdService.opprettEllerHentFagsakId(soknadId = soknad.id)),
                eksternReferanseId = "notat-${event.data.saksnummer}-${event.data.tiltaksgjennomfoeringId}",
                dokumentPdfAsBytes = notatPdf,
                journalposttype = JournalpostType.NOTAT,
                avsenderMottaker = AvsenderMottaker.orgnr(soknad.virksomhet.virksomhetsnummer),
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

        return success()
    }
}