package no.nav.ekspertbistand.vedlegg

import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.domain.VedleggDomain
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.saksbehandling.hentGodkjentSakIdForUpdate
import no.nav.ekspertbistand.saksbehandling.hentSakIdForSoknad
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

@OptIn(kotlin.time.ExperimentalTime::class)
object VedleggTable : UUIDTable("vedlegg") {
    val type = text("type")
    val filnavn = text("filnavn")
    val innhold = binary("innhold")
    val storrelse = integer("storrelse")
    val lastetOpp = timestamp("lastet_opp").defaultExpression(CurrentTimestamp)
    val refusjonskravId = javaUUID("refusjonskrav_id").nullable()
    val sluttrapportId = javaUUID("sluttrapport_id").nullable()
}

@OptIn(kotlin.time.ExperimentalTime::class)
object SluttrapportTable : Table("sluttrapport") {
    val id = javaUUID("sluttrapport_id").databaseGenerated()
    val sakId = javaUUID("sak_id")
    val status = text("status")
    val opprettet = timestamp("opprettet").defaultExpression(CurrentTimestamp)
    val vurdertAvIdent = text("vurdert_av_ident").nullable()
    val vurdertTidspunkt = timestamp("vurdert_tidspunkt").nullable()
    val begrunnelse = text("begrunnelse").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** Status på én sluttrapport-innsending. `IKKE_MOTTATT` lagres ikke: saken har da ingen rad. */
enum class SluttrapportStatus { MOTTATT, BEKREFTET, NY_INNSENDING_KREVES }

class VedleggDb(private val database: Database) {

    fun lagreSluttrapport(
        soknadId: UUID,
        virksomhetsnummer: String,
        innsendtAv: String,
        filnavn: String,
        innhold: ByteArray,
    ): UUID = transaction(database) {
        val sakId = hentGodkjentSakIdForUpdate(soknadId)
        registrerSluttrapport(sakId, soknadId, virksomhetsnummer, innsendtAv, filnavn, innhold)
    }

    /**
     * Henter kun metadata for sluttrapporten (filnavn, størrelse og tidspunkt) – aldri selve
     * filinnholdet, som kan være personsensitivt. `innhold`-kolonnen leses bevisst ikke.
     */
    fun finnSluttrapportMetadata(soknadId: UUID): SluttrapportMetadata? {
        val sakId = transaction(database) { hentSakIdForSoknad(soknadId) } ?: return null
        return finnSluttrapportMetadataForSak(sakId)
    }

    /** Henter metadata for sluttrapporten til nyeste parent for saken, uten å lese filinnholdet. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun finnSluttrapportMetadataForSak(sakId: UUID): SluttrapportMetadata? = transaction(database) {
        val sluttrapport = SluttrapportTable
            .select(
                SluttrapportTable.id,
                SluttrapportTable.status,
                SluttrapportTable.vurdertTidspunkt,
                SluttrapportTable.begrunnelse,
            )
            .where { SluttrapportTable.sakId eq sakId }
            .orderBy(
                SluttrapportTable.opprettet to SortOrder.DESC,
                SluttrapportTable.id to SortOrder.DESC,
            )
            .limit(1)
            .singleOrNull()
            ?: return@transaction null
        val sluttrapportId = sluttrapport[SluttrapportTable.id]

        VedleggTable
            .select(VedleggTable.filnavn, VedleggTable.storrelse, VedleggTable.lastetOpp)
            .where {
                (VedleggTable.sluttrapportId eq sluttrapportId) and
                    (VedleggTable.type eq VedleggType.SLUTTRAPPORT.name)
            }
            .orderBy(
                VedleggTable.lastetOpp to SortOrder.ASC,
                VedleggTable.id to SortOrder.ASC,
            )
            .limit(1)
            .singleOrNull()
            ?.let {
                SluttrapportMetadata(
                    sluttrapportId = sluttrapportId,
                    filnavn = it[VedleggTable.filnavn],
                    storrelse = it[VedleggTable.storrelse],
                    lastetOpp = it[VedleggTable.lastetOpp].toString(),
                    status = SluttrapportStatus.valueOf(sluttrapport[SluttrapportTable.status]),
                    vurdertTidspunkt = sluttrapport[SluttrapportTable.vurdertTidspunkt]?.toString(),
                    begrunnelse = sluttrapport[SluttrapportTable.begrunnelse],
                )
            }
    }

    /** Henter filinnhold bare når det er knyttet til nyeste sluttrapport for saken. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun hentNyesteSluttrapportForSak(sakId: UUID): SluttrapportInnhold? = transaction(database) {
        val sluttrapportId = SluttrapportTable
            .select(SluttrapportTable.id)
            .where { SluttrapportTable.sakId eq sakId }
            .orderBy(
                SluttrapportTable.opprettet to SortOrder.DESC,
                SluttrapportTable.id to SortOrder.DESC,
            )
            .limit(1)
            .singleOrNull()
            ?.get(SluttrapportTable.id)
            ?: return@transaction null

        VedleggTable
            .select(VedleggTable.filnavn, VedleggTable.innhold)
            .where {
                (VedleggTable.sluttrapportId eq sluttrapportId) and
                    (VedleggTable.type eq VedleggType.SLUTTRAPPORT.name)
            }
            .orderBy(
                VedleggTable.lastetOpp to SortOrder.ASC,
                VedleggTable.id to SortOrder.ASC,
            )
            .limit(1)
            .singleOrNull()
            ?.let {
                SluttrapportInnhold(
                    filnavn = it[VedleggTable.filnavn],
                    innhold = it[VedleggTable.innhold],
                )
            }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun JdbcTransaction.registrerSluttrapport(
    sakId: UUID,
    soknadId: UUID,
    virksomhetsnummer: String,
    innsendtAv: String,
    filnavn: String,
    innhold: ByteArray,
): UUID {
    if (!kanSendeInnSluttrapport(sakId)) throw SluttrapportKanIkkeSendesInnException()
    slettFilerForEldreSluttrapporter(sakId)
    val sluttrapport = SluttrapportTable.insertReturning(
        listOf(SluttrapportTable.id, SluttrapportTable.opprettet)
    ) {
        it[SluttrapportTable.sakId] = sakId
        it[status] = SluttrapportStatus.MOTTATT.name
    }.single()
    val sluttrapportId = sluttrapport[SluttrapportTable.id]
    val opprettet = sluttrapport[SluttrapportTable.opprettet]

    val vedleggRad = VedleggTable.insertReturning(listOf(VedleggTable.id)) {
        it[VedleggTable.type] = VedleggType.SLUTTRAPPORT.name
        it[VedleggTable.filnavn] = filnavn
        it[VedleggTable.innhold] = innhold
        it[VedleggTable.storrelse] = innhold.size
        it[VedleggTable.sluttrapportId] = sluttrapportId
    }.single()
    val vedleggId = vedleggRad[VedleggTable.id].value
    val vedlegg = VedleggDomain(
        vedleggId = vedleggId.toString(),
        filnavn = filnavn,
        storrelse = innhold.size,
    )

    publishEventQueue(
        EventData.SluttrapportMottatt(
            sluttrapportId = sluttrapportId.toString(),
            sakId = sakId.toString(),
            soknadId = soknadId.toString(),
            virksomhetsnummer = virksomhetsnummer,
            innsendtAv = innsendtAv,
            vedlegg = vedlegg,
            tidspunkt = opprettet,
        )
    )
    return vedleggId
}

/** Sluttrapporten kan ikke sendes inn fordi nyeste sluttrapport er mottatt eller bekreftet. */
class SluttrapportKanIkkeSendesInnException : RuntimeException()

/**
 * Arbeidsgiver kan sende inn sluttrapport når saken ikke har noen, eller når nyeste har status
 * [SluttrapportStatus.NY_INNSENDING_KREVES]. Må kalles i en transaksjon. Kalt fra
 * [registrerSluttrapport] må sak-raden være låst, så to samtidige innsendinger ikke begge slipper gjennom.
 */
fun JdbcTransaction.kanSendeInnSluttrapport(sakId: UUID): Boolean {
    val nyesteStatus = SluttrapportTable
        .select(SluttrapportTable.status)
        .where { SluttrapportTable.sakId eq sakId }
        .orderBy(SluttrapportTable.opprettet to SortOrder.DESC, SluttrapportTable.id to SortOrder.DESC)
        .limit(1)
        .singleOrNull()
        ?.get(SluttrapportTable.status)
    return nyesteStatus == null || nyesteStatus == SluttrapportStatus.NY_INNSENDING_KREVES.name
}

/**
 * Sletter filene til sakens sluttrapporter med status [SluttrapportStatus.NY_INNSENDING_KREVES].
 * Radene i `sluttrapport` beholdes med status, vurdering og begrunnelse. Arbeidsgiver kan ha lastet
 * opp feil personopplysninger, og en erstattet fil trengs ikke lenger. Filer til mottatte og
 * bekreftede sluttrapporter slettes aldri. Hendelsene har bare metadata om filen, ikke innholdet.
 * Se `specifications/vurdering_sluttrapport.md`.
 */
private fun slettFilerForEldreSluttrapporter(sakId: UUID) {
    val eldre = SluttrapportTable
        .select(SluttrapportTable.id)
        .where {
            (SluttrapportTable.sakId eq sakId) and
                (SluttrapportTable.status eq SluttrapportStatus.NY_INNSENDING_KREVES.name)
        }
        .map { it[SluttrapportTable.id] }
    if (eldre.isEmpty()) return
    VedleggTable.deleteWhere {
        (VedleggTable.sluttrapportId inList eldre) and (VedleggTable.type eq VedleggType.SLUTTRAPPORT.name)
    }
}

data class SluttrapportMetadata(
    val sluttrapportId: UUID,
    val filnavn: String,
    val storrelse: Int,
    val lastetOpp: String,
    val status: SluttrapportStatus,
    val vurdertTidspunkt: String?,
    val begrunnelse: String?,
)

data class SluttrapportInnhold(val filnavn: String, val innhold: ByteArray)

enum class VedleggType { SLUTTRAPPORT, REFUSJONSDOKUMENTASJON }
