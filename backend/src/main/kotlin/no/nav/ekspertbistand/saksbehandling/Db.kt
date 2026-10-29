package no.nav.ekspertbistand.saksbehandling

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val dbLog = LoggerFactory.getLogger("SaksbehandlingDb")

/**
 * Navs behandling av en søknad. Se `specifications/sak-datamodell.md`.
 *
 * Speiler `sak` fra V12, med tildelingskolonnene fra V20 og V23. Fremmednøkler og CHECK-constraints
 * håndheves av databasen.
 */
@OptIn(ExperimentalTime::class)
object SakTable : Table("sak") {
    val sakId = javaUUID("sak_id").databaseGenerated()
    val soknadId = javaUUID("soknad_id")
    val status = text("status")
    val kildeTilBehandling = text("kilde_til_behandling")
    val behandlendeEnhet = text("behandlende_enhet").nullable()
    val kommunenummer = text("kommunenummer").nullable()
    val adressebeskyttelse = text("adressebeskyttelse").nullable()
    val skjermet = bool("skjermet").default(false)
    val saksbehandlerIdent = text("saksbehandler_ident").nullable()
    val saksbehandlerNavn = text("saksbehandler_navn").nullable()
    val beslutterIdent = text("beslutter_ident").nullable()
    val beslutterNavn = text("beslutter_navn").nullable()
    val foreslattUtfall = text("foreslatt_utfall").nullable()
    val arenaSakId = text("arena_sak_id").nullable()
    val saksnummer = text("saksnummer").nullable()
    val refusjonId = javaUUID("refusjon_id").nullable()
    val sluttrapportId = javaUUID("sluttrapport_id").nullable()
    val tildelingEventId = long("tildeling_event_id").nullable()
    val beslutterTildelingEventId = long("beslutter_tildeling_event_id").nullable()
    val opprettet = timestamp("opprettet").defaultExpression(CurrentTimestamp)
    val sistEndret = timestamp("sist_endret").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(sakId)
}

/**
 * Neste saksnummer, `<år>/<løpenummer>`, fra databasefunksjonen `neste_saksnummer()` (V29). Saksnummeret brukes mot
 * OeBS og settes bare for saker med kilde `EKSPERTBISTAND`.
 */
fun nesteSaksnummer(): Expression<String> = CustomFunction("neste_saksnummer", TextColumnType())

fun JdbcTransaction.hentGodkjentSakIdForUpdate(soknadId: UUID): UUID {
    val soknadStatus = SoknadTable
        .select(SoknadTable.status)
        .where { SoknadTable.id eq soknadId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?.get(SoknadTable.status)
        ?: throw SoknadIkkeFunnetException()

    if (soknadStatus != SoknadStatus.godkjent.name) {
        throw SoknadIkkeGodkjentException()
    }

    return SakTable
        .selectAll()
        .where { SakTable.soknadId eq soknadId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?.get(SakTable.sakId)
        ?: throw SakIkkeFunnetException()
}

/** Må kalles i en transaksjon. Returnerer null hvis søknaden ikke har sak. */
fun hentSakIdForSoknad(soknadId: UUID): UUID? =
    SakTable.select(SakTable.sakId)
        .where { SakTable.soknadId eq soknadId }
        .singleOrNull()
        ?.get(SakTable.sakId)

/**
 * Tildeler saken til saksbehandleren. Brukes av både handleren og [no.nav.ekspertbistand.event.projections.SakProjection],
 * så live og replay gir samme resultat. Må kalles i en pågående transaksjon.
 *
 * Saken finnes via `soknad_id`, fordi `sak_id` genereres av databasen og blir ny ved replay.
 * [eventId] lagres i `tildeling_event_id`, og en eldre event skriver aldri over en nyere.
 * Status går bare fra `OPPRETTET` til `UNDER_BEHANDLING`. Returnerer true når saken ble oppdatert.
 */
@OptIn(ExperimentalTime::class)
fun tildelSak(soknadId: UUID, ident: String, navn: String, eventId: Long, tidspunkt: Instant): Boolean =
    SakTable.update(
        where = { (SakTable.soknadId eq soknadId) and nyereTildelingEnn(eventId) }
    ) {
        it[saksbehandlerIdent] = ident
        it[saksbehandlerNavn] = navn
        it[status] = case()
            .When(status eq Saksstatus.OPPRETTET.name, stringLiteral(Saksstatus.UNDER_BEHANDLING.name))
            .Else(status)
        it[tildelingEventId] = eventId
        it[sistEndret] = tidspunkt
    } > 0

/**
 * Frigjør saken, men bare hvis [ident] fortsatt har den. Har en kollega tatt saken i mellomtiden,
 * står kollegaens tildeling. Må kalles i en pågående transaksjon. Returnerer true når saken ble
 * oppdatert. Se [tildelSak] for `soknad_id` og [eventId].
 *
 * Status endres ikke: behandlingen er startet, så en frigjort sak blir stående som
 * `UNDER_BEHANDLING` uten saksbehandler. Vi går ikke tilbake til `OPPRETTET`.
 */
@OptIn(ExperimentalTime::class)
fun frigjoerSak(soknadId: UUID, ident: String, eventId: Long, tidspunkt: Instant): Boolean =
    SakTable.update(
        where = {
            (SakTable.soknadId eq soknadId) and
                    (SakTable.saksbehandlerIdent eq ident) and
                    nyereTildelingEnn(eventId)
        }
    ) {
        it[saksbehandlerIdent] = null
        it[saksbehandlerNavn] = null
        it[tildelingEventId] = eventId
        it[sistEndret] = tidspunkt
    } > 0

private fun nyereTildelingEnn(eventId: Long): Op<Boolean> =
    SakTable.tildelingEventId.isNull() or (SakTable.tildelingEventId less eventId)

/**
 * Tildeler saken til beslutteren. Brukes bare av [no.nav.ekspertbistand.event.handlers.TildelBeslutter].
 * Må kalles i en pågående transaksjon. Returnerer true når saken ble oppdatert.
 *
 * Oppdaterer bare når saken er `TIL_BESLUTNING`, eventen er nyere enn `beslutter_tildeling_event_id`
 * og [ident] ikke sendte siste foreløpige vedtak. Habiliteten sjekkes her også, fordi handleren ikke
 * skal stole på at API-et har sjekket den. Se `specifications/listevisning.md`.
 */
@OptIn(ExperimentalTime::class)
fun tildelBeslutter(soknadId: UUID, ident: String, navn: String, eventId: Long, tidspunkt: Instant): Boolean {
    val sakId = hentSakIdForSoknad(soknadId) ?: return false
    val innstiltAv = hentSisteForelopigVedtakSammendrag(listOf(sakId))[sakId]?.innstiltAvIdent
    if (innstiltAv == null || innstiltAv == ident) return false

    return SakTable.update(
        where = {
            (SakTable.sakId eq sakId) and
                    (SakTable.status eq Saksstatus.TIL_BESLUTNING.name) and
                    (SakTable.beslutterTildelingEventId.isNull() or (SakTable.beslutterTildelingEventId less eventId))
        }
    ) {
        it[beslutterIdent] = ident
        it[beslutterNavn] = navn
        it[beslutterTildelingEventId] = eventId
        it[sistEndret] = tidspunkt
    } > 0
}

/** Lagres i `forelopig_vedtak.utfall` med [name]. */
@Serializable
enum class ForelopigUtfall {
    INNVILGE,
    INNVILGE_REDUSERT,
    AVSLAA,
}

/** Statusen saken får når vedtaket fattes med dette utfallet. */
val ForelopigUtfall.saksstatus: Saksstatus
    get() = when (this) {
        ForelopigUtfall.INNVILGE, ForelopigUtfall.INNVILGE_REDUSERT -> Saksstatus.INNVILGET
        ForelopigUtfall.AVSLAA -> Saksstatus.AVSLATT
    }

/**
 * Saksbehandlers foreløpige vedtak til beslutter, én rad per innsending. Speiler `forelopig_vedtak`
 * fra V21. Se `specifications/forelopig_vedtak.md`.
 */
@OptIn(ExperimentalTime::class)
object ForelopigVedtakTable : Table("forelopig_vedtak") {
    val id = long("id").autoIncrement()
    val sakId = javaUUID("sak_id")
    val versjon = integer("versjon")
    val eventId = long("event_id")
    val utfall = text("utfall")
    val innvilgetBelop = integer("innvilget_belop").nullable()
    val tiltaksperiodeFra = date("tiltaksperiode_fra").nullable()
    val tiltaksperiodeTil = date("tiltaksperiode_til").nullable()
    val begrunnelseDeltaker = text("begrunnelse_deltaker")
    val begrunnelseArbeidsgiver = text("begrunnelse_arbeidsgiver").nullable()
    val innstiltAvIdent = text("innstilt_av_ident")
    val sendtTidspunkt = timestamp("sendt_tidspunkt")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Setter saken til `TIL_BESLUTNING` med `foreslatt_utfall`, og lagrer det foreløpige vedtaket som
 * neste versjon. Gjør ingenting hvis saken ikke er `UNDER_BEHANDLING`. `event_id UNIQUE` stopper
 * duplikater. Brukes bare av [no.nav.ekspertbistand.event.handlers.LagreForelopigVedtak].
 * Må kalles i en pågående transaksjon. Returnerer true når saken ble oppdatert.
 */
@OptIn(ExperimentalTime::class)
fun lagreForelopigVedtak(data: EventData.ForelopigVedtakSendtTilBeslutter, eventId: Long): Boolean {
    val soknadId = UUID.fromString(data.soknadId)
    val oppdatert = SakTable.update(
        where = { (SakTable.soknadId eq soknadId) and (SakTable.status eq Saksstatus.UNDER_BEHANDLING.name) }
    ) {
        it[status] = Saksstatus.TIL_BESLUTNING.name
        it[foreslattUtfall] = data.utfall.saksstatus.name
        it[sistEndret] = data.tidspunkt
    } > 0
    if (!oppdatert) return false

    val sakId = hentSakIdForSoknad(soknadId) ?: return false
    val forrigeVersjon = ForelopigVedtakTable
        .select(ForelopigVedtakTable.versjon.max())
        .where { ForelopigVedtakTable.sakId eq sakId }
        .singleOrNull()
        ?.get(ForelopigVedtakTable.versjon.max())
        ?: 0

    ForelopigVedtakTable.insertIgnore {
        it[ForelopigVedtakTable.sakId] = sakId
        it[versjon] = forrigeVersjon + 1
        it[ForelopigVedtakTable.eventId] = eventId
        it[utfall] = data.utfall.name
        it[innvilgetBelop] = data.innvilgetBelop
        it[tiltaksperiodeFra] = data.tiltaksperiodeFra
        it[tiltaksperiodeTil] = data.tiltaksperiodeTil
        it[begrunnelseDeltaker] = data.begrunnelseDeltaker
        it[begrunnelseArbeidsgiver] = data.begrunnelseArbeidsgiver
        it[innstiltAvIdent] = data.innstiltAvIdent
        it[sendtTidspunkt] = data.tidspunkt
    }
    return true
}

/** Siste versjon av det foreløpige vedtaket, eller null. Må kalles i en pågående transaksjon. */
@OptIn(ExperimentalTime::class)
fun hentSisteForelopigVedtak(sakId: UUID): ForelopigVedtakDTO? =
    ForelopigVedtakTable.selectAll()
        .where { ForelopigVedtakTable.sakId eq sakId }
        .orderBy(ForelopigVedtakTable.versjon, SortOrder.DESC)
        .limit(1)
        .singleOrNull()
        ?.let {
            ForelopigVedtakDTO(
                versjon = it[ForelopigVedtakTable.versjon],
                utfall = ForelopigUtfall.valueOf(it[ForelopigVedtakTable.utfall]),
                innvilgetBelop = it[ForelopigVedtakTable.innvilgetBelop],
                tiltaksperiodeFra = it[ForelopigVedtakTable.tiltaksperiodeFra],
                tiltaksperiodeTil = it[ForelopigVedtakTable.tiltaksperiodeTil],
                begrunnelseDeltaker = it[ForelopigVedtakTable.begrunnelseDeltaker],
                begrunnelseArbeidsgiver = it[ForelopigVedtakTable.begrunnelseArbeidsgiver],
                innstiltAvIdent = it[ForelopigVedtakTable.innstiltAvIdent],
                sendtTidspunkt = it[ForelopigVedtakTable.sendtTidspunkt],
            )
        }

/** Det listen og habilitetssjekken trenger fra siste foreløpige vedtak. */
data class ForelopigVedtakSammendrag(
    val innstiltAvIdent: String,
    val tiltaksperiodeFra: LocalDate?,
    val tiltaksperiodeTil: LocalDate?,
)

/**
 * Siste versjon av det foreløpige vedtaket for hver av [sakIder], med én spørring. Saker uten
 * foreløpig vedtak er ikke med. Må kalles i en pågående transaksjon.
 */
fun hentSisteForelopigVedtakSammendrag(sakIder: Collection<UUID>): Map<UUID, ForelopigVedtakSammendrag> {
    if (sakIder.isEmpty()) return emptyMap()
    return ForelopigVedtakTable
        .select(
            ForelopigVedtakTable.sakId,
            ForelopigVedtakTable.versjon,
            ForelopigVedtakTable.innstiltAvIdent,
            ForelopigVedtakTable.tiltaksperiodeFra,
            ForelopigVedtakTable.tiltaksperiodeTil,
        )
        .where { ForelopigVedtakTable.sakId inList sakIder }
        .groupBy { it[ForelopigVedtakTable.sakId] }
        .mapValues { (_, rader) ->
            rader.maxBy { it[ForelopigVedtakTable.versjon] }.let {
                ForelopigVedtakSammendrag(
                    innstiltAvIdent = it[ForelopigVedtakTable.innstiltAvIdent],
                    tiltaksperiodeFra = it[ForelopigVedtakTable.tiltaksperiodeFra],
                    tiltaksperiodeTil = it[ForelopigVedtakTable.tiltaksperiodeTil],
                )
            }
        }
}

@OptIn(ExperimentalTime::class)
@Serializable
data class ForelopigVedtakDTO(
    val versjon: Int,
    val utfall: ForelopigUtfall,
    val innvilgetBelop: Int?,
    val tiltaksperiodeFra: LocalDate?,
    val tiltaksperiodeTil: LocalDate?,
    val begrunnelseDeltaker: String,
    val begrunnelseArbeidsgiver: String?,
    val innstiltAvIdent: String,
    val sendtTidspunkt: Instant,
)

/** Lagres i `sak_retur.aarsaker` med [name]. Speiles av CHECK-en i V24. */
@Serializable
enum class ReturArsak {
    FEIL_REGELFORSTAELSE,
    FEIL_FAKTA,
    ANNET,
}

/** Beslutters vedtak på søknaden, én rad per sak. Speiler `vedtak` fra V24. Se `specifications/beslutter_vedtak.md`. */
@OptIn(ExperimentalTime::class)
object VedtakTable : Table("vedtak") {
    val id = long("id").autoIncrement()
    val sakId = javaUUID("sak_id")
    val forelopigVedtakId = long("forelopig_vedtak_id")
    val utfall = text("utfall")
    val fattetAvIdent = text("fattet_av_ident")
    val fattetAvNavn = text("fattet_av_navn")
    val fattetTidspunkt = timestamp("fattet_tidspunkt")
    val eventId = long("event_id")

    override val primaryKey = PrimaryKey(id)
}

/** Beslutters retur av et foreløpig vedtak, én rad per retur. Speiler `sak_retur` fra V12 og V24. */
@OptIn(ExperimentalTime::class)
object SakReturTable : Table("sak_retur") {
    val returId = javaUUID("retur_id").databaseGenerated()
    val sakId = javaUUID("sak_id")
    val forelopigVedtakId = long("forelopig_vedtak_id")
    val returnertAvIdent = text("returnert_av_ident")
    val returnertAvNavn = text("returnert_av_navn")
    val tilSaksbehandlerIdent = text("til_saksbehandler_ident").nullable()
    val aarsaker = array<String>("aarsaker")
    val forklaring = text("forklaring")
    val eventId = long("event_id")
    val opprettet = timestamp("opprettet")

    override val primaryKey = PrimaryKey(returId)
}

/** Det beslutningen trenger fra siste versjon av det foreløpige vedtaket. */
data class SisteForelopigVedtakRef(
    val id: Long,
    val versjon: Int,
    val utfall: ForelopigUtfall,
    val innstiltAvIdent: String,
)

/** Må kalles i en pågående transaksjon. */
fun hentSisteForelopigVedtakRef(sakId: UUID): SisteForelopigVedtakRef? =
    ForelopigVedtakTable
        .select(
            ForelopigVedtakTable.id,
            ForelopigVedtakTable.versjon,
            ForelopigVedtakTable.utfall,
            ForelopigVedtakTable.innstiltAvIdent,
        )
        .where { ForelopigVedtakTable.sakId eq sakId }
        .orderBy(ForelopigVedtakTable.versjon, SortOrder.DESC)
        .limit(1)
        .singleOrNull()
        ?.let {
            SisteForelopigVedtakRef(
                id = it[ForelopigVedtakTable.id],
                versjon = it[ForelopigVedtakTable.versjon],
                utfall = ForelopigUtfall.valueOf(it[ForelopigVedtakTable.utfall]),
                innstiltAvIdent = it[ForelopigVedtakTable.innstiltAvIdent],
            )
        }

/** Hvorfor beslutteren ikke kan fatte vedtak eller returnere nå. */
enum class BeslutningHindring {
    UGYLDIG_STATUS,
    FORELOPIG_VEDTAK_ENDRET,
    INHABIL,
}

/**
 * Den ene regelen for om en beslutning kan tas på saken. Brukes av API-et, handlerne og
 * [no.nav.ekspertbistand.event.handlers.SettSoknadStatusVedtakFattet], så alle svarer likt.
 * [fattetAvIdent] er null ved retur, som ikke sjekker habilitet. Mangler foreløpig vedtak, avvises
 * beslutningen (fail-closed). Returnerer null når beslutningen kan tas.
 */
fun beslutningHindring(
    status: Saksstatus,
    siste: SisteForelopigVedtakRef?,
    forelopigVedtakVersjon: Int,
    fattetAvIdent: String?,
): BeslutningHindring? = when {
    status != Saksstatus.TIL_BESLUTNING -> BeslutningHindring.UGYLDIG_STATUS
    siste == null || siste.versjon != forelopigVedtakVersjon -> BeslutningHindring.FORELOPIG_VEDTAK_ENDRET
    fattetAvIdent != null && siste.innstiltAvIdent == fattetAvIdent -> BeslutningHindring.INHABIL
    else -> null
}

private data class LastSak(val sakId: UUID, val status: Saksstatus, val saksbehandlerIdent: String?)

private fun lastSakForSoknad(soknadId: UUID): LastSak? =
    SakTable
        .select(SakTable.sakId, SakTable.status, SakTable.saksbehandlerIdent)
        .where { SakTable.soknadId eq soknadId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?.let {
            LastSak(
                sakId = it[SakTable.sakId],
                status = Saksstatus.valueOf(it[SakTable.status]),
                saksbehandlerIdent = it[SakTable.saksbehandlerIdent],
            )
        }

/**
 * Lagrer vedtaket og setter saken til [EventData.VedtakFattet.utfall]. Låser saken og sjekker
 * [beslutningHindring] på nytt, med habilitet, fordi en ny innstilling kan ha kommet etter at
 * API-et sjekket. Brukes bare av [no.nav.ekspertbistand.event.handlers.FattVedtak].
 * Må kalles i en pågående transaksjon. Returnerer true når vedtaket ble lagret.
 */
@OptIn(ExperimentalTime::class)
fun fattVedtak(data: EventData.VedtakFattet, eventId: Long): Boolean {
    val sak = lastSakForSoknad(UUID.fromString(data.soknadId)) ?: return false
    val siste = hentSisteForelopigVedtakRef(sak.sakId)
    when (beslutningHindring(sak.status, siste, data.forelopigVedtakVersjon, data.fattetAvIdent)) {
        null -> Unit
        BeslutningHindring.INHABIL -> {
            dbLog.error("Vedtak for sak {} ble ikke lagret: beslutter er inhabil", sak.sakId)
            return false
        }
        else -> return false
    }
    val forelopig = siste ?: return false

    val lagret = VedtakTable.insertIgnore {
        it[sakId] = sak.sakId
        it[forelopigVedtakId] = forelopig.id
        it[utfall] = data.utfall.name
        it[fattetAvIdent] = data.fattetAvIdent
        it[fattetAvNavn] = data.fattetAvNavn
        it[fattetTidspunkt] = data.tidspunkt
        it[VedtakTable.eventId] = eventId
    }.insertedCount > 0
    if (!lagret) return false

    SakTable.update(where = { SakTable.sakId eq sak.sakId }) {
        it[status] = data.utfall.name
        it[sistEndret] = data.tidspunkt
    }
    return true
}

/**
 * Lagrer returen og setter saken tilbake til `UNDER_BEHANDLING` uten `foreslatt_utfall`.
 * Saksbehandler og beslutter beholdes. Brukes bare av
 * [no.nav.ekspertbistand.event.handlers.ReturnerForelopigVedtak]. Må kalles i en pågående
 * transaksjon. Returnerer true når returen ble lagret.
 */
@OptIn(ExperimentalTime::class)
fun returnerForelopigVedtak(data: EventData.ForelopigVedtakReturnert, eventId: Long): Boolean {
    val sak = lastSakForSoknad(UUID.fromString(data.soknadId)) ?: return false
    val siste = hentSisteForelopigVedtakRef(sak.sakId)
    if (beslutningHindring(sak.status, siste, data.forelopigVedtakVersjon, fattetAvIdent = null) != null) {
        return false
    }
    val forelopig = siste ?: return false

    val lagret = SakReturTable.insertIgnore {
        it[sakId] = sak.sakId
        it[forelopigVedtakId] = forelopig.id
        it[returnertAvIdent] = data.returnertAvIdent
        it[returnertAvNavn] = data.returnertAvNavn
        it[tilSaksbehandlerIdent] = sak.saksbehandlerIdent
        it[aarsaker] = data.aarsaker.sorted().map { arsak -> arsak.name }
        it[forklaring] = data.forklaring
        it[SakReturTable.eventId] = eventId
        it[opprettet] = data.tidspunkt
    }.insertedCount > 0
    if (!lagret) return false

    SakTable.update(where = { SakTable.sakId eq sak.sakId }) {
        it[status] = Saksstatus.UNDER_BEHANDLING.name
        it[foreslattUtfall] = null
        it[sistEndret] = data.tidspunkt
    }
    return true
}

/** Utfallet i vedtaket som ble lagret for eventen, eller null. Må kalles i en pågående transaksjon. */
fun hentVedtakUtfallForEvent(eventId: Long): Saksstatus? =
    VedtakTable.select(VedtakTable.utfall)
        .where { VedtakTable.eventId eq eventId }
        .singleOrNull()
        ?.let { Saksstatus.valueOf(it[VedtakTable.utfall]) }

/**
 * Om [EventData.VedtakFattet] fortsatt kan lagres av [fattVedtak], slik saken står nå.
 * Må kalles i en pågående transaksjon.
 */
fun vedtakKanFattes(data: EventData.VedtakFattet): Boolean {
    val soknadId = UUID.fromString(data.soknadId)
    val sak = SakTable.select(SakTable.sakId, SakTable.status)
        .where { SakTable.soknadId eq soknadId }
        .singleOrNull()
        ?: return false
    val siste = hentSisteForelopigVedtakRef(sak[SakTable.sakId])
    return beslutningHindring(
        Saksstatus.valueOf(sak[SakTable.status]), siste, data.forelopigVedtakVersjon, data.fattetAvIdent,
    ) == null
}

/** Sakene blant [sakIder] som har minst én retur. Må kalles i en pågående transaksjon. */
fun hentSakerMedRetur(sakIder: Collection<UUID>): Set<UUID> {
    if (sakIder.isEmpty()) return emptySet()
    return SakReturTable.select(SakReturTable.sakId)
        .where { SakReturTable.sakId inList sakIder }
        .withDistinct()
        .map { it[SakReturTable.sakId] }
        .toSet()
}

/** Nyeste retur for saken, eller null. Må kalles i en pågående transaksjon. */
@OptIn(ExperimentalTime::class)
fun hentSisteRetur(sakId: UUID): ReturDTO? =
    SakReturTable
        .join(ForelopigVedtakTable, JoinType.INNER, SakReturTable.forelopigVedtakId, ForelopigVedtakTable.id)
        .select(
            ForelopigVedtakTable.versjon,
            SakReturTable.aarsaker,
            SakReturTable.forklaring,
            SakReturTable.returnertAvNavn,
            SakReturTable.opprettet,
        )
        .where { SakReturTable.sakId eq sakId }
        .orderBy(SakReturTable.opprettet to SortOrder.DESC, SakReturTable.eventId to SortOrder.DESC)
        .limit(1)
        .singleOrNull()
        ?.let {
            ReturDTO(
                forelopigVedtakVersjon = it[ForelopigVedtakTable.versjon],
                aarsaker = it[SakReturTable.aarsaker].map(ReturArsak::valueOf),
                forklaring = it[SakReturTable.forklaring],
                returnertAvNavn = it[SakReturTable.returnertAvNavn],
                tidspunkt = it[SakReturTable.opprettet],
            )
        }

/** Vedtaket på saken, eller null før det er fattet. Må kalles i en pågående transaksjon. */
@OptIn(ExperimentalTime::class)
fun hentVedtak(sakId: UUID): VedtakDTO? =
    VedtakTable
        .join(ForelopigVedtakTable, JoinType.INNER, VedtakTable.forelopigVedtakId, ForelopigVedtakTable.id)
        .select(
            VedtakTable.utfall,
            ForelopigVedtakTable.versjon,
            VedtakTable.fattetAvNavn,
            VedtakTable.fattetTidspunkt,
        )
        .where { VedtakTable.sakId eq sakId }
        .singleOrNull()
        ?.let {
            VedtakDTO(
                utfall = Saksstatus.valueOf(it[VedtakTable.utfall]),
                forelopigVedtakVersjon = it[ForelopigVedtakTable.versjon],
                fattetAvNavn = it[VedtakTable.fattetAvNavn],
                fattetTidspunkt = it[VedtakTable.fattetTidspunkt],
            )
        }

@OptIn(ExperimentalTime::class)
@Serializable
data class ReturDTO(
    val forelopigVedtakVersjon: Int,
    val aarsaker: List<ReturArsak>,
    val forklaring: String,
    val returnertAvNavn: String,
    val tidspunkt: Instant,
)

@OptIn(ExperimentalTime::class)
@Serializable
data class VedtakDTO(
    val utfall: Saksstatus,
    val forelopigVedtakVersjon: Int,
    val fattetAvNavn: String,
    val fattetTidspunkt: Instant,
)

/**
 * Vilkårsvurderingen for en sak, én rad per [Vilkar]. Se `specifications/sak-datamodell.md`.
 *
 * Speiler `saksvilkar` fra V19. `godkjent = null` betyr «ikke vurdert ennå».
 */
@OptIn(ExperimentalTime::class)
object SaksvilkarTable : Table("saksvilkar") {
    val sakId = javaUUID("sak_id")
    val vilkarId = text("vilkar_id")
    val godkjent = bool("godkjent").nullable()
    val notat = text("notat").nullable()
    val vurdertTidspunkt = timestamp("vurdert_tidspunkt").nullable()
    val vurdertAvIdent = text("vurdert_av_ident").nullable()

    override val primaryKey = PrimaryKey(sakId, vilkarId)
}

/** Om saken har en rad for [vilkar] i `saksvilkar`. Må kalles i en pågående transaksjon. */
fun vilkarFinnes(sakId: UUID, vilkar: Vilkar): Boolean =
    !SaksvilkarTable.select(SaksvilkarTable.vilkarId)
        .where { (SaksvilkarTable.sakId eq sakId) and (SaksvilkarTable.vilkarId eq vilkar.name) }
        .empty()

/** Lagres i `saksvilkar.vilkar_id` med [name]. Nye verdier krever re-kjøring av SakProjection for eksisterende saker. */
@Serializable
enum class Vilkar {
    DELTAKER_HAR_ARBEIDSFORHOLD,
    FYLLES_UT_I_SAMRAD_GODKJENT,
    ARBEIDSGIVER_HAR_PROVD_TILRETTELEGGING,
    DELTAKER_HAR_SYKEFRAVAERSHISTORIKK,
    EKSPERT_HAR_KOMPETANSE,
}

class SoknadIkkeFunnetException : RuntimeException()
class SoknadIkkeGodkjentException : RuntimeException()
class SakIkkeFunnetException : RuntimeException()

/**
 * Hendelseslogg for en sak. Se `specifications/sakslogg.md`.
 *
 * Speiler `sakslogg` fra V12, med `utfort_av_type` fjernet i V17.
 */
@OptIn(ExperimentalTime::class)
object SaksloggTable : Table("sakslogg") {
    val saksloggId = javaUUID("sakslogg_id").databaseGenerated()
    val sakId = javaUUID("sak_id")
    val utfortAvRolle = text("utfort_av_rolle")
    val utfortAvIdent = text("utfort_av_ident").nullable()
    val notat = text("notat").nullable()
    val utfortAt = timestamp("utfort_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(saksloggId)
}

enum class Saksstatus {
    OPPRETTET,
    UNDER_BEHANDLING,
    TIL_BESLUTNING,
    INNVILGET,
    AVSLATT,
    AVSLUTTET, //Refundert?
}

enum class KildeTilBehandling {
    EKSPERTBISTAND,
    ARENA,
}

/** Rollen aktøren hadde da handlingen i saksloggen ble utført. `SYSTEM` har ingen ident. */
@Serializable
enum class AktorRolle {
    SAKSBEHANDLER,
    BESLUTTER,
    SYSTEM,
}
