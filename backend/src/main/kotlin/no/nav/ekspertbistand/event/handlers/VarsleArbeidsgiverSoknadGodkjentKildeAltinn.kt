package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.notifikasjon.EksterntVarsel
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.SaksStatus
import no.nav.ekspertbistand.tilsagndata.concat
import org.jetbrains.exposed.v1.jdbc.Database


/**
 * Når en søknad, innsendt via altinn 2 skjema,
 * er godkjent i arena så ender det med en event av typen [EventData.TilskuddsbrevJournalfoertKildeAltinn]
 * Denne handleren oppretter da en sak og en beskjed i notifikasjonsplatformen for den godkjente søknaden.
 */
class VarsleArbeidsgiverSoknadGodkjentKildeAltinn(
    private val produsentApiKlient: ProdusentApiKlient,
    database: Database
) : EventHandler<EventData.TilskuddsbrevJournalfoertKildeAltinn> {
    override val id: String = "VarsleArbeidsgiverSoknadGodkjentKildeAltinn"
    override val eventType = EventData.TilskuddsbrevJournalfoertKildeAltinn::class

    private val nySakSubTask = "notifikasjonsplatform_ny_sak"
    private val nyStatusSakSubTask = "notifikasjonsplatform_ny_status_sak"
    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"

    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.TilskuddsbrevJournalfoertKildeAltinn>): EventHandledResult {
        val tilsagnData = event.data.tilsagnData
        if (!idempotencyGuard.isGuarded(event.id, nySakSubTask)) {
            nySak(tilsagnData).fold(
                onSuccess = { idempotencyGuard.guard(event, nySakSubTask) },
                onFailure = { return transientError("Feil ved opprettelse av sak i notifikasjonsplatform", it) }
            )
        }
        if (!idempotencyGuard.isGuarded(event.id, nyStatusSakSubTask)) {
            nyStatusSak(tilsagnData).fold(
                onSuccess = { idempotencyGuard.guard(event, nyStatusSakSubTask) },
                onFailure = {
                    return transientError(
                        "Klarte ikke oppdatere saksstatus i notifikasjonsplatform", it
                    )
                }
            )
        }
        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            nyBeskjed(tilsagnData).fold(
                onSuccess = { idempotencyGuard.guard(event, nyBeskjedSubTask) },
                onFailure = { return transientError("Feil ved opprettelse av beskjed i notifikasjonsplatform", it) }
            )
        }

        return success()
    }

    private suspend fun nySak(tilsagnData: TilsagnData): Result<String> {
        return try {
            produsentApiKlient.opprettNySak(
                grupperingsid = tilsagnData.tilsagnNummer.concat(),
                virksomhetsnummer = tilsagnData.virksomhetsnummer,
                tittel = "Ekspertbistand ${tilsagnData.ansattNavn} f. ${tilsagnData.ansattFoedselsdato}",
                lenke = tilsagnData.kvitteringsLenke,
            )
            Result.success("Opprettet sak for tilsagn ${tilsagnData.tilsagnNummer.concat()}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private suspend fun nyStatusSak(tilsagnData: TilsagnData): Result<String> {
        return try {
            produsentApiKlient.nyStatusSak(
                grupperingsid = tilsagnData.tilsagnNummer.concat(),
                status = SaksStatus.FERDIG,
                statusTekst = "Søknad godkjent"
            )
            Result.success("Oppdaterte sakstatus for tilsagn ${tilsagnData.tilsagnNummer.concat()}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private suspend fun nyBeskjed(tilsagnData: TilsagnData): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                grupperingsid = tilsagnData.tilsagnNummer.concat(),
                eksternId = "${tilsagnData.tilsagnNummer.concat()}-soknad-godkjent",
                virksomhetsnummer = tilsagnData.virksomhetsnummer,
                tekst = "Søknaden er godkjent og ekspertbistand kan nå tas i bruk.",
                lenke = tilsagnData.kvitteringsLenke,
                eksternVarsel = EksterntVarsel(
                    epostTittel = "Nav – angående søknad om ekspertbistand",
                    epostHtmlBody = "${tilsagnData.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det.",
                    smsTekst = "${tilsagnData.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det.",
                )
            )
            Result.success("Opprettet beskjed for tilsagn ${tilsagnData.tilsagnNummer.concat()}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}

val TilsagnData.virksomhetsnummer: String
    get() = tiltakArrangor.orgNummer.toString()

val TilsagnData.virksomhetsnavn: String
    get() = tiltakArrangor.arbgiverNavn

private val TilsagnData.paaKrevdDeltaker: TilsagnData.Deltaker
    get() = requireNotNull(deltaker) { "TilsagnData mangler deltaker for tilsagn ${tilsagnNummer.concat()}" }

val TilsagnData.ansattNavn: String
    get() = "${paaKrevdDeltaker.fornavn} ${paaKrevdDeltaker.etternavn}"

val TilsagnData.ansattFoedselsdato: String
    get() = paaKrevdDeltaker.fodselsnr.tilFødselsdato()

val TilsagnData.kvitteringsLenke: String
    get() = basedOnEnv(
        prod = "https://arbeidsgiver.nav.no",
        dev = "https://arbeidsgiver.intern.dev.nav.no",
        other = "https://arbeidsgiver.intern.dev.nav.no",
    ).let { domain -> "$domain/ekspertbistand/tilskuddsbrev/${tilsagnNummer.concat()}" }
