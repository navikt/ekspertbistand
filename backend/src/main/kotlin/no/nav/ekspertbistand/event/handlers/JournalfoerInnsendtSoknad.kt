package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.JournalpostType
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.pdl.NotFound
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering
import no.nav.ekspertbistand.pdl.graphql.generated.enums.GtType
import no.nav.ekspertbistand.pdl.graphql.generated.hentgeografisktilknytning.GeografiskTilknytning
import no.nav.ekspertbistand.soknad.DTO
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass

private const val publiserJournalpostEventSubtask = "journalpost_opprettet_event"
private const val tittel = "Søknad om ekspertbistand"

/**
 * Når en søknad om ekspertbistand er innsendt, genereres en PDF og så journalføres dette i DokArkiv.
 *
 * Før journalføring hentes behandlende enhet basert på søker sin adressebeskyttelse og geografisk tilknytning.
 * Logikken for ruting til behandlende enhet er definert i [JournalfoerInnsendtSoknad.hentBehandlendeEnhet].
 *
 * Etter journalføring publiseres en ny event [no.nav.ekspertbistand.event.EventData.InnsendtSoknadJournalfoert]
 * som inneholder informasjon om journalpostId og dokumentId samt behandlendeEnhetId.
 */
class JournalfoerInnsendtSoknad(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val pdlApiKlient: PdlApiKlient,
    private val behandlendeEnhetService: BehandlendeEnhetService,
    private val eregClient: EregClient,
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

        val behandlendeEnhet = runCatching { hentBehandlendeEnhet(soknad) }
            .getOrElse { e ->
                return when (e) {
                    is MissingDataException -> transientError(
                        "Mangler data for å hente behandlende enhet", e
                    )

                    else -> transientError("Feil ved henting av behandlende enhet", e)
                }
            }

        val soknadPdf = runCatching { dokgenClient.genererSoknadPdf(soknad) }
            .getOrElse { e ->
                return transientError("Klarte ikke generere søknad-PDF", e)
            }

        val journalpostResponse = runCatching {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = tittel,
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                eksternReferanseId = soknadId,
                dokumentPdfAsBytes = soknadPdf,
                journalposttype = JournalpostType.INNGAAENDE,
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
            QueuedEvents.insert {
                it[eventData] = EventData.InnsendtSoknadJournalfoert(
                    soknad = soknad,
                    dokumentId = dokumentInfoId,
                    journaldpostId = journalpostId,
                    behandlendeEnhetId = behandlendeEnhet
                )
            }
        }

        idempotencyGuard.guard(event, publiserJournalpostEventSubtask)

        return success()
    }

    private suspend fun hentBehandlendeEnhet(soknad: DTO.Soknad): String =
        pdlApiKlient.hentAdressebeskyttelse(soknad.ansatt.fnr).fold(
            onSuccess = { person ->
                val gradering = person.adressebeskyttelse.map { it.gradering }.hoyesteGradering()
                val geografiskTilknytning = when (gradering) {
                    AdressebeskyttelseGradering.STRENGT_FORTROLIG,
                    AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> {
                        pdlApiKlient.hentGeografiskTilknytning(soknad.ansatt.fnr)
                            .fold(
                                onSuccess = {
                                    it.geografiskTilknytning() ?: BehandlendeEnhetService.NAV_VIKAFOSSEN
                                },
                                onFailure = { throw it }

                            )
                    }

                    AdressebeskyttelseGradering.FORTROLIG,
                    AdressebeskyttelseGradering.UGRADERT,
                    AdressebeskyttelseGradering.__UNKNOWN_VALUE -> {
                        val organisasjon = eregClient.hentOrganisasjon(soknad.virksomhet.virksomhetsnummer)
                        organisasjon.organisasjonDetaljer
                            ?.forretningsadresser
                            ?.firstNotNullOfOrNull { it.kommunenummer }
                            ?: throw MissingDataException("Fant ikke kommunenummer for virksomhet ${soknad.virksomhet.virksomhetsnummer}")
                    }
                }

                behandlendeEnhetService.hentBehandlendeEnhet(gradering, geografiskTilknytning)
            },

            onFailure = { error ->
                when (error) {
                    is NotFound -> {
                        val organisasjon = eregClient.hentOrganisasjon(soknad.virksomhet.virksomhetsnummer)
                        val geografiskTilknytning = organisasjon.organisasjonDetaljer
                            ?.forretningsadresser
                            ?.firstNotNullOfOrNull { it.kommunenummer }
                            ?: throw MissingDataException("Fant ikke kommunenummer for virksomhet ${soknad.virksomhet.virksomhetsnummer}")
                        behandlendeEnhetService.hentBehandlendeEnhet(
                            AdressebeskyttelseGradering.__UNKNOWN_VALUE,
                            geografiskTilknytning
                        )
                    }

                    else -> throw error
                }
            }
        )

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
}
