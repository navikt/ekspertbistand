package no.nav.ekspertbistand.refusjon

import no.nav.ekspertbistand.vedlegg.VedleggTable
import no.nav.ekspertbistand.vedlegg.VedleggType
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.hentGodkjentSakIdForUpdate
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

@OptIn(kotlin.time.ExperimentalTime::class)
object RefusjonskravTable : UUIDTable("refusjonskrav") {
    val soknadId = uuid("soknad_id")
    val sakId = uuid("sak_id").nullable()
    val belopOre = long("belop_ore")
    val utgifter = text("utgifter")
    val status = text("status")
    val opprettet = timestamp("opprettet").defaultExpression(CurrentTimestamp)
}

data class RefusjonsfilInput(val filnavn: String, val innhold: ByteArray)

class RefusjonDb(private val database: Database) {

    fun lagreRefusjonskrav(
        soknadId: UUID,
        belopOre: Long,
        utgifter: String,
        filer: List<RefusjonsfilInput>,
    ): UUID = transaction(database) {
        val sakId = hentGodkjentSakIdForUpdate(soknadId)
        val refusjonskravId = RefusjonskravTable.insertReturning {
            it[RefusjonskravTable.soknadId] = soknadId
            it[RefusjonskravTable.sakId] = sakId
            it[RefusjonskravTable.belopOre] = belopOre
            it[RefusjonskravTable.utgifter] = utgifter
            it[status] = "MOTTATT"
        }.single()[RefusjonskravTable.id].value

        filer.forEach { fil ->
            VedleggTable.insert {
                it[VedleggTable.soknadId] = soknadId
                it[type] = VedleggType.REFUSJONSDOKUMENTASJON.name
                it[filnavn] = fil.filnavn
                it[innhold] = fil.innhold
                it[storrelse] = fil.innhold.size
                it[VedleggTable.refusjonskravId] = refusjonskravId
            }
        }

        refusjonskravId
    }

    /**
     * Henter metadata om siste refusjonskrav for en søknad – beløp, utgifter, tidspunkt
     * og vedleggsliste (id + filnavn + størrelse). Filinnholdet (`innhold`) leses bevisst
     * ikke her; det hentes separat via [hentRefusjonsvedlegg] ved nedlasting.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun finnRefusjonskravStatus(soknadId: UUID): RefusjonskravStatus? = transaction(database) {
        val sakId = SakTable
            .select(SakTable.sakId)
            .where { SakTable.soknadId eq soknadId }
            .singleOrNull()
            ?.get(SakTable.sakId)

        val kravForSak = sakId?.let {
            RefusjonskravTable
                .selectAll()
                .where { RefusjonskravTable.sakId eq it }
                .orderBy(RefusjonskravTable.opprettet, SortOrder.DESC)
                .firstOrNull()
        }

        val krav = kravForSak ?: RefusjonskravTable
            .selectAll()
            .where {
                (RefusjonskravTable.soknadId eq soknadId) and
                    RefusjonskravTable.sakId.isNull()
            }
            .orderBy(RefusjonskravTable.opprettet, SortOrder.DESC)
            .firstOrNull()
            ?: return@transaction null

        val refusjonskravId = krav[RefusjonskravTable.id].value

        val vedlegg = VedleggTable
            .select(VedleggTable.id, VedleggTable.filnavn, VedleggTable.storrelse)
            .where { VedleggTable.refusjonskravId eq refusjonskravId }
            .orderBy(VedleggTable.lastetOpp, SortOrder.ASC)
            .map {
                RefusjonsvedleggMeta(
                    id = it[VedleggTable.id].value.toString(),
                    filnavn = it[VedleggTable.filnavn],
                    storrelse = it[VedleggTable.storrelse],
                )
            }

        RefusjonskravStatus(
            belopKroner = krav[RefusjonskravTable.belopOre] / 100,
            utgifter = krav[RefusjonskravTable.utgifter],
            opprettet = krav[RefusjonskravTable.opprettet].toString(),
            // TODO: hent kontonummer fra SOKOS. Lagres ikke lokalt.
            kontonummer = null,
            vedlegg = vedlegg,
        )
    }

    /**
     * Henter filinnholdet for ett refusjonsvedlegg. Slår kun opp vedlegg som tilhører
     * den oppgitte søknadens refusjonskrav – hindrer at man laster ned vedlegg på tvers
     * av søknader (IDOR).
     */
    fun hentRefusjonsvedlegg(soknadId: UUID, vedleggId: UUID): RefusjonsvedleggInnhold? =
        transaction(database) {
            val sakId = SakTable
                .select(SakTable.sakId)
                .where { SakTable.soknadId eq soknadId }
                .singleOrNull()
                ?.get(SakTable.sakId)
                ?: return@transaction null

            val vedlegg = VedleggTable
                .selectAll()
                .where {
                    (VedleggTable.id eq vedleggId) and
                        (VedleggTable.type eq VedleggType.REFUSJONSDOKUMENTASJON.name)
                }
                .firstOrNull()
                ?: return@transaction null

            val refusjonskravId = vedlegg[VedleggTable.refusjonskravId]
                ?: return@transaction null
            val refusjonskrav = RefusjonskravTable
                .select(RefusjonskravTable.sakId, RefusjonskravTable.soknadId)
                .where { RefusjonskravTable.id eq refusjonskravId }
                .singleOrNull()
                ?: return@transaction null

            val tilhorerSak = refusjonskrav[RefusjonskravTable.sakId] == sakId
            val erLegacyForSoknad =
                refusjonskrav[RefusjonskravTable.sakId] == null &&
                    refusjonskrav[RefusjonskravTable.soknadId] == soknadId &&
                    vedlegg[VedleggTable.soknadId] == soknadId

            if (!tilhorerSak && !erLegacyForSoknad) {
                return@transaction null
            }

            RefusjonsvedleggInnhold(
                filnavn = vedlegg[VedleggTable.filnavn],
                innhold = vedlegg[VedleggTable.innhold],
            )
        }
}

data class RefusjonskravStatus(
    val belopKroner: Long,
    val utgifter: String,
    val opprettet: String,
    val kontonummer: String?,
    val vedlegg: List<RefusjonsvedleggMeta>,
)

data class RefusjonsvedleggMeta(
    val id: String,
    val filnavn: String,
    val storrelse: Int,
)

data class RefusjonsvedleggInnhold(
    val filnavn: String,
    val innhold: ByteArray,
)
