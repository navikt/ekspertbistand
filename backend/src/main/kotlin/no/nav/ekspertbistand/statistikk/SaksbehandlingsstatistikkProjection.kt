package no.nav.ekspertbistand.statistikk

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.EKSPERTBISTAND_TILTAKSKODE
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventLog
import no.nav.ekspertbistand.event.LoggedEvent.Companion.tilLoggedEvent
import no.nav.ekspertbistand.event.ProcessingStatus.COMPLETED
import no.nav.ekspertbistand.event.projections.EventLogProjectionBuilder
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.norg.BehandlendeEnhetService.Companion.NAV_VIKAFOSSEN
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.ReturArsak
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.SisteForelopigVedtakRef
import no.nav.ekspertbistand.saksbehandling.beslutningHindring
import no.nav.ekspertbistand.saksbehandling.saksstatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Bygger meldinger til felles saksbehandlingsstatistikk (Team Sak) og tiltaksstatistikk i datavarehuset
 * fra event-loggen, og skriver dem til BigQuery. Se `README.md` i samme pakke og
 * `specifications/saksbehandlingsstatistikk.md`.
 *
 * Behandlingen er søknadsbehandlingen, med søknad-id som `behandling_id`. Bare saker opprettet av
 * [no.nav.ekspertbistand.event.handlers.OpprettSak] (kilde EKSPERTBISTAND) rapporteres. Arena-saker
 * vedtas i Arena og får ingen meldinger.
 *
 * Tilstanden lagres ikke. Den bygges for hver event fra tidligere eventer i loggen med samme
 * aggregatrot, som er søknad-id-en. Hver event som endrer meldingsinnholdet gir én ny rad.
 *
 * Kode 6: før hver rad skrives slår vi opp gjeldende adressebeskyttelse i PDL. Behandlende enhet sier ikke
 * sikkert om personen har strengt fortrolig adresse, og graderingen kan endres etter innsending.
 *
 * Resending: bump versjonen i [name]. Projeksjonen leser loggen på nytt fra start og skriver nye rader
 * med samme `hendelse_tid` og `sekvensnummer`, og ny `melding_tid` og `projeksjon`.
 */
@OptIn(ExperimentalTime::class)
class SaksbehandlingsstatistikkProjection(
    database: Database,
    private val skriver: SaksbehandlingsstatistikkSkriver,
    private val adressebeskyttelse: AdressebeskyttelseOppslag,
) : EventLogProjectionBuilder(database) {
    override val name = "Saksbehandlingsstatistikk-v1"

    private val versjon = System.getenv("NAIS_APP_IMAGE")?.substringAfterLast(':') ?: "lokal"

    override fun handle(event: Event<out EventData>, eventTimestamp: Instant) {
        if (!erRelevant(event.data)) return
        val behandlingId = event.aggregateRootId

        val forrige = tilstandFor(behandlingId, event.id)
        val overgang = nesteTilstand(forrige, event.id, event.data, eventTimestamp) ?: return

        val melding = overgang.tilstand.meldingsinnhold() ?: return
        if (melding == forrige?.meldingsinnhold()) return

        val skjermet = melding.ansvarligEnhet == NAV_VIKAFOSSEN ||
            runBlocking { adressebeskyttelse.harStrengtFortroligAdresse(melding.fnr) }
        val utgaende = if (skjermet) melding.skjermet() else melding

        skriver.skriv(rad(behandlingId, utgaende, overgang.hendelseTid, event.id))
    }

    /** Tilstanden etter alle fullførte eventer for behandlingen med lavere id enn [eventId]. */
    private fun tilstandFor(behandlingId: String, eventId: Long): BehandlingTilstand? =
        EventLog.selectAll()
            .where {
                (EventLog.aggregateRootId eq behandlingId) and (EventLog.id less eventId) and
                    (EventLog.status eq COMPLETED)
            }
            .orderBy(EventLog.id)
            .map { it.tilLoggedEvent() }
            .filter { erRelevant(it.eventData) }
            .fold(null as BehandlingTilstand?) { tilstand, logget ->
                nesteTilstand(tilstand, logget.id, logget.eventData, logget.createdAt)?.tilstand ?: tilstand
            }

    private fun rad(behandlingId: String, melding: Meldingsinnhold, hendelseTid: Instant, eventId: Long) =
        SaksbehandlingsstatistikkRad(
            sekvensnummer = eventId,
            sakId = melding.sakId,
            sakNummer = melding.sakId,
            behandlingId = behandlingId,
            fnr = melding.fnr,
            hendelseTid = hendelseTid,
            meldingTid = Clock.System.now(),
            mottattTid = melding.mottattTid,
            registrertTid = melding.registrertTid,
            ferdigbehandletTid = melding.ferdigbehandletTid,
            forventetOppstartTid = melding.forventetOppstartTid,
            sakYtelse = SAK_YTELSE,
            sakUtland = SAK_UTLAND,
            behandlingType = BEHANDLING_TYPE,
            behandlingStatus = melding.status.name,
            behandlingResultat = melding.resultat?.name,
            resultatBegrunnelse = melding.resultatBegrunnelse,
            behandlingMetode = melding.metode.name,
            opprettetAv = melding.opprettetAv,
            saksbehandler = melding.saksbehandler,
            ansvarligBeslutter = melding.ansvarligBeslutter,
            ansvarligEnhet = melding.ansvarligEnhet,
            vedtaksloesningNavn = VEDTAKSLOESNING_NAVN,
            vedtaksloesningVersjon = versjon,
            tiltakskodeArena = EKSPERTBISTAND_TILTAKSKODE,
            arrangorOrgnr = melding.arrangorOrgnr,
            estimertAntallTimer = melding.estimertAntallTimer,
            ekspertVirksomhetOrgnr = melding.ekspertVirksomhetOrgnr,
            ekspertUtdanning = melding.ekspertUtdanning,
            ekspertKompetanse = melding.ekspertKompetanse,
            innvilgetBelop = melding.innvilgetBelop,
            tiltaksperiodeFra = melding.tiltaksperiodeFra,
            tiltaksperiodeTil = melding.tiltaksperiodeTil,
            projeksjon = name,
        )

    companion object {
        const val VEDTAKSLOESNING_NAVN = "EKSPERTBISTAND"
        const val SAK_YTELSE = "EKSPERTBISTAND"
        const val SAK_UTLAND = "NASJONAL"
        const val BEHANDLING_TYPE = "SOKNAD"

        /** Verdien Team Sak skal ha i geo-lokaliserende felter når søker har strengt fortrolig adresse (kode 6). */
        const val SKJERMET = "-5"

        private fun erRelevant(data: EventData) = when (data) {
            is EventData.SoknadInnsendt,
            is EventData.InnsendtSoknadJournalfoert,
            is EventData.SakOpprettet,
            is EventData.SakTildeltSaksbehandler,
            is EventData.SakFrigjort,
            is EventData.SakTildeltBeslutter,
            is EventData.ForelopigVedtakSendtTilBeslutter,
            is EventData.ForelopigVedtakReturnert,
            is EventData.VedtakFattet -> true

            else -> false
        }
    }
}

/** Ny tilstand, og tidspunktet for hendelsen som ga den. */
@OptIn(ExperimentalTime::class)
data class Overgang(val tilstand: BehandlingTilstand, val hendelseTid: Instant)

/**
 * Anvender eventen på tilstanden. Returnerer null når eventen ikke endrer behandlingen, enten fordi
 * den gjelder en sak vi ikke rapporterer, eller fordi handleren ville avvist den. Vaktene speiler
 * handlerne, slik at en event som ikke endret saken heller ikke gir melding.
 */
@OptIn(ExperimentalTime::class)
fun nesteTilstand(
    forrige: BehandlingTilstand?,
    eventId: Long,
    data: EventData,
    eventTimestamp: Instant,
): Overgang? {
    if (data is EventData.SoknadInnsendt) {
        return Overgang(BehandlingTilstand(mottattTid = eventTimestamp), eventTimestamp)
    }
    val t = forrige ?: return null

    if (data is EventData.InnsendtSoknadJournalfoert) {
        val enhet = BehandlendeEnhetService.arenaTilNorgEnhetNr(data.behandlendeEnhetId)
        return Overgang(t.copy(ansvarligEnhet = enhet), eventTimestamp)
    }
    if (data is EventData.SakOpprettet) {
        if (t.sakId != null) return null
        return Overgang(
            t.copy(
                sakId = data.sakId,
                fnr = data.soknad.ansatt.fnr,
                registrertTid = eventTimestamp,
                forventetOppstartTid = data.soknad.behovForBistand.startdato,
                status = StatistikkStatus.REGISTRERT,
                arrangorOrgnr = data.soknad.virksomhet.virksomhetsnummer,
                estimertAntallTimer = data.soknad.behovForBistand.timer.trim().toIntOrNull(),
                ekspertVirksomhetOrgnr = data.soknad.ekspert.virksomhetOrgnr,
                ekspertUtdanning = data.soknad.ekspert.utdanningKoder,
                ekspertKompetanse = data.soknad.ekspert.kompetanseKoder,
            ),
            eventTimestamp,
        )
    }

    if (t.sakId == null || t.status == StatistikkStatus.AVSLUTTET) return null

    return when (data) {
        is EventData.SakTildeltSaksbehandler -> {
            if (!t.erNyereTildeling(eventId)) return null
            Overgang(
                t.copy(
                    saksbehandler = data.saksbehandlerIdent,
                    tildelingEventId = eventId,
                    status = if (t.status == StatistikkStatus.REGISTRERT) StatistikkStatus.UNDER_BEHANDLING else t.status,
                    manuell = true,
                ),
                data.tidspunkt,
            )
        }

        is EventData.SakFrigjort -> {
            if (!t.erNyereTildeling(eventId) || t.saksbehandler != data.saksbehandlerIdent) return null
            Overgang(t.copy(saksbehandler = null, tildelingEventId = eventId, manuell = true), data.tidspunkt)
        }

        is EventData.SakTildeltBeslutter -> {
            val nyere = t.beslutterTildelingEventId == null || t.beslutterTildelingEventId < eventId
            val habil = t.innstiltAvIdent != null && t.innstiltAvIdent != data.beslutterIdent
            if (t.status != StatistikkStatus.TIL_BESLUTNING || !nyere || !habil) return null
            Overgang(
                t.copy(beslutter = data.beslutterIdent, beslutterTildelingEventId = eventId, manuell = true),
                data.tidspunkt,
            )
        }

        is EventData.ForelopigVedtakSendtTilBeslutter -> {
            if (t.saksstatus != Saksstatus.UNDER_BEHANDLING) return null
            Overgang(
                t.copy(
                    status = StatistikkStatus.TIL_BESLUTNING,
                    forelopigVedtakVersjon = t.forelopigVedtakVersjon + 1,
                    forelopigUtfall = data.utfall,
                    innstiltAvIdent = data.innstiltAvIdent,
                    resultat = data.utfall.statistikkResultat,
                    resultatBegrunnelse = null,
                    innvilgetBelop = data.innvilgetBelop,
                    tiltaksperiodeFra = data.tiltaksperiodeFra,
                    tiltaksperiodeTil = data.tiltaksperiodeTil,
                    manuell = true,
                ),
                data.tidspunkt,
            )
        }

        is EventData.ForelopigVedtakReturnert -> {
            if (t.beslutningHindring(data.forelopigVedtakVersjon, fattetAvIdent = null) != null) return null
            Overgang(
                t.copy(
                    status = StatistikkStatus.UNDERKJENT_BESLUTTER,
                    resultat = null,
                    resultatBegrunnelse = data.aarsaker.map { it.statistikkBegrunnelse }.sorted().joinToString(","),
                    innvilgetBelop = null,
                    tiltaksperiodeFra = null,
                    tiltaksperiodeTil = null,
                    manuell = true,
                ),
                data.tidspunkt,
            )
        }

        is EventData.VedtakFattet -> {
            if (t.beslutningHindring(data.forelopigVedtakVersjon, data.fattetAvIdent) != null) return null
            val resultat = t.forelopigUtfall
                ?.takeIf { it.saksstatus == data.utfall }
                ?.statistikkResultat
                ?: StatistikkResultat.valueOf(data.utfall.name)
            Overgang(
                t.copy(
                    status = StatistikkStatus.AVSLUTTET,
                    resultat = resultat,
                    resultatBegrunnelse = null,
                    beslutter = data.fattetAvIdent,
                    ferdigbehandletTid = data.tidspunkt,
                    manuell = true,
                ),
                data.tidspunkt,
            )
        }

        else -> null
    }
}

enum class StatistikkStatus {
    REGISTRERT,
    UNDER_BEHANDLING,
    TIL_BESLUTNING,
    UNDERKJENT_BESLUTTER,
    AVSLUTTET,
}

enum class StatistikkResultat {
    INNVILGET,
    DELVIS_INNVILGET,
    AVSLATT,
}

enum class BehandlingMetode {
    AUTOMATISK,
    MANUELL,
}

private val ForelopigUtfall.statistikkResultat: StatistikkResultat
    get() = when (this) {
        ForelopigUtfall.INNVILGE -> StatistikkResultat.INNVILGET
        ForelopigUtfall.INNVILGE_REDUSERT -> StatistikkResultat.DELVIS_INNVILGET
        ForelopigUtfall.AVSLAA -> StatistikkResultat.AVSLATT
    }

private val ReturArsak.statistikkBegrunnelse: String
    get() = when (this) {
        ReturArsak.FEIL_REGELFORSTAELSE -> "FEIL_LOVANVENDELSE"
        ReturArsak.FEIL_FAKTA -> "FEIL_FAKTUM"
        ReturArsak.ANNET -> "ANNET"
    }

/**
 * Det projeksjonen vet om én behandling. Tildelings-event-id-ene, versjonen og innstilt-av finnes
 * bare for å speile vaktene i handlerne.
 */
@OptIn(ExperimentalTime::class)
data class BehandlingTilstand(
    val mottattTid: Instant,
    val ansvarligEnhet: String? = null,
    val sakId: String? = null,
    val fnr: String? = null,
    val registrertTid: Instant? = null,
    val forventetOppstartTid: LocalDate? = null,
    val ferdigbehandletTid: Instant? = null,
    val status: StatistikkStatus? = null,
    val resultat: StatistikkResultat? = null,
    val resultatBegrunnelse: String? = null,
    val manuell: Boolean = false,
    val saksbehandler: String? = null,
    val tildelingEventId: Long? = null,
    val beslutter: String? = null,
    val beslutterTildelingEventId: Long? = null,
    val forelopigVedtakVersjon: Int = 0,
    val forelopigUtfall: ForelopigUtfall? = null,
    val innstiltAvIdent: String? = null,
    val arrangorOrgnr: String? = null,
    val estimertAntallTimer: Int? = null,
    val ekspertVirksomhetOrgnr: String? = null,
    val ekspertUtdanning: String? = null,
    val ekspertKompetanse: String? = null,
    val innvilgetBelop: Int? = null,
    val tiltaksperiodeFra: LocalDate? = null,
    val tiltaksperiodeTil: LocalDate? = null,
) {
    /** Statusen på `sak.status` som handlerne ser. */
    val saksstatus: Saksstatus?
        get() = when (status) {
            null -> null
            StatistikkStatus.REGISTRERT -> Saksstatus.OPPRETTET
            StatistikkStatus.UNDER_BEHANDLING, StatistikkStatus.UNDERKJENT_BESLUTTER -> Saksstatus.UNDER_BEHANDLING
            StatistikkStatus.TIL_BESLUTNING -> Saksstatus.TIL_BESLUTNING
            StatistikkStatus.AVSLUTTET -> resultat?.let {
                if (it == StatistikkResultat.AVSLATT) Saksstatus.AVSLATT else Saksstatus.INNVILGET
            }
        }

    fun erNyereTildeling(eventId: Long) = tildelingEventId == null || tildelingEventId < eventId

    fun beslutningHindring(forelopigVedtakVersjon: Int, fattetAvIdent: String?) = beslutningHindring(
        status = saksstatus ?: Saksstatus.OPPRETTET,
        siste = if (this.forelopigVedtakVersjon > 0 && forelopigUtfall != null && innstiltAvIdent != null) {
            SisteForelopigVedtakRef(
                id = this.forelopigVedtakVersjon.toLong(),
                versjon = this.forelopigVedtakVersjon,
                utfall = forelopigUtfall,
                innstiltAvIdent = innstiltAvIdent,
            )
        } else null,
        forelopigVedtakVersjon = forelopigVedtakVersjon,
        fattetAvIdent = fattetAvIdent,
    )

    /** Feltene i meldingen som avgjør om hendelsen er relevant, uten skjerming. Null før saken er registrert. */
    fun meldingsinnhold(): Meldingsinnhold? {
        val sakId = sakId ?: return null
        val fnr = fnr ?: return null
        val registrertTid = registrertTid ?: return null
        val status = status ?: return null

        return Meldingsinnhold(
            sakId = sakId,
            fnr = fnr,
            mottattTid = mottattTid,
            registrertTid = registrertTid,
            ferdigbehandletTid = ferdigbehandletTid,
            forventetOppstartTid = forventetOppstartTid,
            status = status,
            resultat = resultat,
            resultatBegrunnelse = resultatBegrunnelse,
            metode = if (manuell) BehandlingMetode.MANUELL else BehandlingMetode.AUTOMATISK,
            opprettetAv = SaksbehandlingsstatistikkProjection.VEDTAKSLOESNING_NAVN,
            saksbehandler = saksbehandler,
            ansvarligBeslutter = beslutter,
            ansvarligEnhet = ansvarligEnhet,
            arrangorOrgnr = arrangorOrgnr,
            estimertAntallTimer = estimertAntallTimer,
            ekspertVirksomhetOrgnr = ekspertVirksomhetOrgnr,
            ekspertUtdanning = ekspertUtdanning,
            ekspertKompetanse = ekspertKompetanse,
            innvilgetBelop = innvilgetBelop,
            tiltaksperiodeFra = tiltaksperiodeFra,
            tiltaksperiodeTil = tiltaksperiodeTil,
        )
    }
}

@OptIn(ExperimentalTime::class)
data class Meldingsinnhold(
    val sakId: String,
    val fnr: String,
    val mottattTid: Instant,
    val registrertTid: Instant,
    val ferdigbehandletTid: Instant?,
    val forventetOppstartTid: LocalDate?,
    val status: StatistikkStatus,
    val resultat: StatistikkResultat?,
    val resultatBegrunnelse: String?,
    val metode: BehandlingMetode,
    val opprettetAv: String,
    val saksbehandler: String?,
    val ansvarligBeslutter: String?,
    val ansvarligEnhet: String?,
    val arrangorOrgnr: String?,
    val estimertAntallTimer: Int?,
    val ekspertVirksomhetOrgnr: String?,
    val ekspertUtdanning: String?,
    val ekspertKompetanse: String?,
    val innvilgetBelop: Int?,
    val tiltaksperiodeFra: LocalDate?,
    val tiltaksperiodeTil: LocalDate?,
) {
    /** Meldingen ved strengt fortrolig adresse (kode 6): felt som kan avsløre hvor personen bor, blir `-5`. */
    fun skjermet(): Meldingsinnhold {
        fun skjerm(verdi: String?) = verdi?.let { SaksbehandlingsstatistikkProjection.SKJERMET }
        return copy(
            opprettetAv = SaksbehandlingsstatistikkProjection.SKJERMET,
            saksbehandler = skjerm(saksbehandler),
            ansvarligBeslutter = skjerm(ansvarligBeslutter),
            ansvarligEnhet = skjerm(ansvarligEnhet),
            arrangorOrgnr = skjerm(arrangorOrgnr),
            ekspertVirksomhetOrgnr = skjerm(ekspertVirksomhetOrgnr),
        )
    }
}
