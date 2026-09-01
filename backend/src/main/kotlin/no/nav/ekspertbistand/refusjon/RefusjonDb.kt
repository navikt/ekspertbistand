package no.nav.ekspertbistand.refusjon

import no.nav.ekspertbistand.vedlegg.VedleggTable
import no.nav.ekspertbistand.vedlegg.VedleggType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
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
        val refusjonskravId = RefusjonskravTable.insertReturning {
            it[RefusjonskravTable.soknadId] = soknadId
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
        val krav = RefusjonskravTable
            .selectAll()
            .where { RefusjonskravTable.soknadId eq soknadId }
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
            VedleggTable
                .selectAll()
                .where {
                    (VedleggTable.id eq vedleggId) and
                        (VedleggTable.soknadId eq soknadId) and
                        (VedleggTable.type eq VedleggType.REFUSJONSDOKUMENTASJON.name)
                }
                .firstOrNull()
                ?.let {
                    RefusjonsvedleggInnhold(
                        filnavn = it[VedleggTable.filnavn],
                        innhold = it[VedleggTable.innhold],
                    )
                }
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
