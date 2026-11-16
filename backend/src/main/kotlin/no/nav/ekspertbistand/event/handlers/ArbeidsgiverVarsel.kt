package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.event.domain.ArbeidsgiverVarselDomain
import no.nav.ekspertbistand.event.domain.tilDomain
import no.nav.ekspertbistand.event.domain.tilSaksStatus
import no.nav.ekspertbistand.notifikasjon.EksterntVarsel
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.merkelapp
import no.nav.ekspertbistand.notifikasjon.sendevindu
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.kvitteringsLenke
import no.nav.ekspertbistand.tilsagndata.concat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/*
 * Felles for handlerne som varsler arbeidsgiver i notifikasjonsplattformen. Varselet bygges som
 * [ArbeidsgiverVarselDomain] før vi sender, og vi sender fra det. Da er kvitteringen lik det som ble bestilt, og et
 * nytt forsøk sender det samme. Se `specifications/kvittering_varsel_arbeidsgiver_soknad.md`.
 */

/** Sak og beskjed om mottatt søknad. Likt for søknader som behandles i Arena og i ny løsning. */
internal fun arbeidsgiverVarselSoknadMottatt(soknad: DTO.Soknad): ArbeidsgiverVarselDomain {
    val soknadId = requireNotNull(soknad.id) { "Søknaden mangler id" }
    return ArbeidsgiverVarselDomain(
        merkelapp = merkelapp,
        mottaker = ArbeidsgiverVarselDomain.Mottaker(altinnRessursId = altinn3Ressursid),
        virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
        grupperingsid = soknadId,
        sak = ArbeidsgiverVarselDomain.Sak(
            tittel = "Ekspertbistand ${soknad.ansatt.navn} f. ${soknad.ansatt.fnr.tilFødselsdato()}",
            lenke = soknad.kvitteringsLenke,
        ),
        beskjed = ArbeidsgiverVarselDomain.Beskjed(
            eksternId = "$soknadId-soknad-mottatt",
            tekst = "Nav har mottatt deres søknad om ekspertbistand.",
            lenke = soknad.kvitteringsLenke,
            eksterntVarsel = null,
        ),
        saksstatus = null,
    )
}

/** Beskjed og ferdig sak når Arena har godkjent søknaden. Én per tilsagn. */
internal fun arbeidsgiverVarselSoknadGodkjent(
    soknad: DTO.Soknad,
    tilsagnNummer: TilsagnData.TilsagnNummer,
    godkjentFlereGanger: Boolean,
): ArbeidsgiverVarselDomain {
    val soknadId = requireNotNull(soknad.id) { "Søknaden mangler id" }
    val eksterntVarselTekst = "${soknad.virksomhet.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. " +
        "Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det."
    return ArbeidsgiverVarselDomain(
        merkelapp = merkelapp,
        mottaker = ArbeidsgiverVarselDomain.Mottaker(altinnRessursId = altinn3Ressursid),
        virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
        grupperingsid = soknadId,
        sak = null,
        beskjed = ArbeidsgiverVarselDomain.Beskjed(
            eksternId = "$soknadId-godkjent-${tilsagnNummer.concat()}",
            tekst = if (godkjentFlereGanger) {
                "Søknaden er godkjent på nytt, se oppdaterte opplysninger."
            } else {
                "Søknaden er godkjent og ekspertbistand kan nå tas i bruk."
            },
            lenke = soknad.kvitteringsLenke,
            eksterntVarsel = ArbeidsgiverVarselDomain.EksterntVarsel(
                epostTittel = "Nav – angående søknad om ekspertbistand",
                epostHtmlBody = eksterntVarselTekst,
                smsTekst = eksterntVarselTekst,
                sendevindu = sendevindu.tilDomain(),
            ),
        ),
        saksstatus = ArbeidsgiverVarselDomain.Saksstatus(
            status = ArbeidsgiverVarselDomain.Status.FERDIG,
            statusTekst = "Søknad godkjent",
            nyLenkeTilSak = null,
        ),
    )
}

/** Beskjed og ferdig sak når søknaden er avlyst i Arena. */
internal fun arbeidsgiverVarselSoknadAvlyst(soknad: DTO.Soknad): ArbeidsgiverVarselDomain {
    val soknadId = requireNotNull(soknad.id) { "Søknaden mangler id" }
    val eksterntVarselTekst = "${soknad.virksomhet.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. " +
        "Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det."
    return ArbeidsgiverVarselDomain(
        merkelapp = merkelapp,
        mottaker = ArbeidsgiverVarselDomain.Mottaker(altinnRessursId = altinn3Ressursid),
        virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
        grupperingsid = soknadId,
        sak = null,
        beskjed = ArbeidsgiverVarselDomain.Beskjed(
            eksternId = "$soknadId-soknad-avlyst",
            tekst = "Søknaden om ekspertbistand trukket eller avslått.",
            lenke = soknad.kvitteringsLenke,
            eksterntVarsel = ArbeidsgiverVarselDomain.EksterntVarsel(
                epostTittel = "Nav – angående søknad om ekspertbistand",
                epostHtmlBody = eksterntVarselTekst,
                smsTekst = eksterntVarselTekst,
                sendevindu = sendevindu.tilDomain(),
            ),
        ),
        saksstatus = ArbeidsgiverVarselDomain.Saksstatus(
            status = ArbeidsgiverVarselDomain.Status.FERDIG,
            statusTekst = "Søknad trukket eller avslått",
            nyLenkeTilSak = null,
        ),
    )
}

internal suspend fun ProdusentApiKlient.bestillSak(varsel: ArbeidsgiverVarselDomain) {
    val sak = checkNotNull(varsel.sak) { "Varselet for ${varsel.grupperingsid} har ingen sak" }
    opprettNySak(
        grupperingsid = varsel.grupperingsid,
        virksomhetsnummer = varsel.virksomhetsnummer,
        tittel = sak.tittel,
        lenke = sak.lenke,
    )
}

internal suspend fun ProdusentApiKlient.bestillBeskjed(varsel: ArbeidsgiverVarselDomain) {
    opprettNyBeskjed(
        grupperingsid = varsel.grupperingsid,
        eksternId = varsel.beskjed.eksternId,
        virksomhetsnummer = varsel.virksomhetsnummer,
        tekst = varsel.beskjed.tekst,
        lenke = varsel.beskjed.lenke,
        eksternVarsel = varsel.beskjed.eksterntVarsel?.let {
            EksterntVarsel(
                epostTittel = it.epostTittel,
                epostHtmlBody = it.epostHtmlBody,
                smsTekst = it.smsTekst,
            )
        },
    )
}

internal suspend fun ProdusentApiKlient.bestillSaksstatus(varsel: ArbeidsgiverVarselDomain) {
    val saksstatus = checkNotNull(varsel.saksstatus) { "Varselet for ${varsel.grupperingsid} har ingen saksstatus" }
    nyStatusSak(
        grupperingsid = varsel.grupperingsid,
        status = saksstatus.status.tilSaksStatus(),
        statusTekst = saksstatus.statusTekst,
        nyLenkeTilSak = saksstatus.nyLenkeTilSak,
    )
}

/**
 * Kjører [publiser] og guarder [subTask] i én transaksjon, når varselet er bestilt. Er [subTask] guardet fra før,
 * publiseres ingenting.
 */
internal inline fun <reified H : EventHandler<T>, T : EventData> H.publiserKvittering(
    idempotencyGuard: IdempotencyGuard,
    database: Database,
    event: Event<T>,
    subTask: String,
    crossinline publiser: JdbcTransaction.() -> Unit,
): EventHandledResult {
    if (idempotencyGuard.isGuarded(event.id, subTask)) {
        return success()
    }
    return transaction(database) {
        try {
            publiser()
            idempotencyGuard.guard(event, subTask)
            success()
        } catch (e: Exception) {
            rollback()
            transientError("Feil ved publisering av kvittering for varsel til arbeidsgiver", e)
        }
    }
}
