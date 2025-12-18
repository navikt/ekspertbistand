package no.nav.ekspertbistand.dokarkiv

import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering
import no.nav.ekspertbistand.pdl.graphql.generated.enums.GtType
import no.nav.ekspertbistand.pdl.graphql.generated.hentgeografisktilknytning.GeografiskTilknytning
import no.nav.ekspertbistand.skjema.DTO
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass

private const val publiserJournalpostEventSubtask = "journalpost_opprettet_event"
private const val tittel = "Søknad om ekspertbistand"

class SkjemaInnsendtHandler(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val pdlApiKlient: PdlApiKlient,
    private val behandlendeEnhetService: BehandlendeEnhetService,
    private val eregClient: EregClient,
    private val database: Database,
    private val idempotencyGuard: IdempotencyGuard,
) : EventHandler<EventData.SkjemaInnsendt> {
    override val id: String = "SkjemaInnsendtHandler"
    override val eventType: KClass<EventData.SkjemaInnsendt> = EventData.SkjemaInnsendt::class

    override suspend fun handle(event: Event<EventData.SkjemaInnsendt>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, publiserJournalpostEventSubtask)) {
            return EventHandledResult.Success()
        }

        val skjema = event.data.skjema
        val skjemaId = skjema.id ?: return EventHandledResult.UnrecoverableError("Skjema mangler id")

        val behandlendeEnhet = runCatching { hentBehandlendeEnhet(skjema) }
            .getOrElse { e ->
                return when (e) {
                    is MissingDataException -> EventHandledResult.UnrecoverableError(
                        e.message ?: "Mangler data for å hente behandlende enhet"
                    )

                    else -> EventHandledResult.TransientError("Feil ved henting av behandlende enhet: ${e.message}")
                }
            }

        val soknadPdf = runCatching { dokgenClient.genererSoknadPdf(skjema) }
            .getOrElse { e ->
                return EventHandledResult.TransientError("Klarte ikke generere søknad-PDF: ${e.message}")
            }

        val journalpostResponse = runCatching {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = tittel,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                eksternReferanseId = skjemaId,
                dokumentPdfAsBytes = soknadPdf,
            )
        }.getOrElse { e ->
            return EventHandledResult.TransientError("Feil ved opprettelse av journalpost: ${e.message}")
        }

        if (!journalpostResponse.journalpostferdigstilt) {
            return EventHandledResult.TransientError("Journalpost ikke ferdigstilt")
        }

        val dokumentInfoId = journalpostResponse.dokumenter.firstOrNull()?.dokumentInfoId?.toIntOrNull()
            ?: return EventHandledResult.UnrecoverableError("DokArkiv mangler dokumentInfoId")
        val journalpostId = journalpostResponse.journalpostId.toIntOrNull()
            ?: return EventHandledResult.UnrecoverableError("DokArkiv mangler gyldig journalpostId")

        transaction(database) {
            QueuedEvents.insert {
                it[eventData] = EventData.JournalpostOpprettet(
                    skjema = skjema,
                    dokumentId = dokumentInfoId,
                    journaldpostId = journalpostId,
                    behandlendeEnhetId = behandlendeEnhet
                )
            }
        }

        idempotencyGuard.guard(event, publiserJournalpostEventSubtask)

        return EventHandledResult.Success()
    }

    private suspend fun hentBehandlendeEnhet(skjema: DTO.Skjema): String {
        val adressebeskyttelser = pdlApiKlient.hentAdressebeskyttelse(skjema.ansatt.fnr).adressebeskyttelse
        val gradering = adressebeskyttelser.map { it.gradering }.hoyesteGradering()

        val geografiskTilknytning = when (gradering) {
            AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> {
                val tilknytning = pdlApiKlient.hentGeografiskTilknytning(skjema.ansatt.fnr)
                tilknytning.geografiskTilknytning() ?: BehandlendeEnhetService.NAV_VIKAFOSSEN
            }

            AdressebeskyttelseGradering.FORTROLIG,
            AdressebeskyttelseGradering.UGRADERT,
            AdressebeskyttelseGradering.__UNKNOWN_VALUE -> {
                val organisasjon = eregClient.hentOrganisasjon(skjema.virksomhet.virksomhetsnummer)
                organisasjon.organisasjonDetaljer
                    ?.forretningsadresser
                    ?.firstNotNullOfOrNull { it.kommunenummer }
                    ?: throw MissingDataException("Fant ikke kommunenummer for virksomhet ${skjema.virksomhet.virksomhetsnummer}")
            }
        }

        return behandlendeEnhetService.hentBehandlendeEnhet(gradering, geografiskTilknytning)
    }
}

private fun List<AdressebeskyttelseGradering>.hoyesteGradering(): AdressebeskyttelseGradering {
    return this.maxByOrNull { gradering ->
        when (gradering) {
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> 3
            AdressebeskyttelseGradering.STRENGT_FORTROLIG -> 2
            AdressebeskyttelseGradering.FORTROLIG -> 1
            AdressebeskyttelseGradering.UGRADERT -> 0
            AdressebeskyttelseGradering.__UNKNOWN_VALUE -> -1
        }
    } ?: AdressebeskyttelseGradering.UGRADERT
}

private fun GeografiskTilknytning.geografiskTilknytning(): String? {
    return when (gtType) {
        GtType.KOMMUNE -> gtKommune
        GtType.BYDEL -> gtBydel ?: gtKommune
        GtType.UTLAND -> gtLand
        GtType.UDEFINERT, GtType.__UNKNOWN_VALUE -> null
    }
}

private class MissingDataException(message: String) : Exception(message)
