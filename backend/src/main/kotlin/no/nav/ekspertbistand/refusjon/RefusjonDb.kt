package no.nav.ekspertbistand.refusjon

import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.domain.VedleggDomain
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.vedlegg.VedleggTable
import no.nav.ekspertbistand.vedlegg.VedleggType
import no.nav.ekspertbistand.saksbehandling.KildeTilBehandling
import no.nav.ekspertbistand.saksbehandling.SakIkkeFunnetException
import no.nav.ekspertbistand.saksbehandling.SakKildeArenaException
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.hentGodkjentSakIdForUpdate
import no.nav.ekspertbistand.saksbehandling.hentInnvilgetBelopKroner
import no.nav.ekspertbistand.saksbehandling.hentSakIdForSoknad
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

@OptIn(kotlin.time.ExperimentalTime::class)
object RefusjonskravTable : UUIDTable("refusjonskrav") {
    val sakId = javaUUID("sak_id")
    val belopOre = long("belop_ore")
    val status = text("status")
    val opprettet = timestamp("opprettet").defaultExpression(CurrentTimestamp)
}

data class RefusjonsfilInput(val filnavn: String, val innhold: ByteArray)

class RefusjonskravFinnesAlleredeException : RuntimeException("Saken har allerede et refusjonskrav")
class InnvilgetBelopManglerException : RuntimeException("Saken har ikke et innvilget beløp")
class BelopOverInnvilgetException(val maksBelopKroner: Int) :
    RuntimeException("Beløpet er høyere enn innvilget beløp")

class RefusjonDb(private val database: Database) {

    fun lagreRefusjonskrav(
        soknadId: UUID,
        virksomhetsnummer: String,
        innsendtAv: String,
        belopOre: Long,
        filer: List<RefusjonsfilInput>,
    ): UUID = transaction(database) {
        val sakId = hentGodkjentSakIdForUpdate(soknadId)
        // Saken er låst over, så sjekk og innsetting kan ikke kappe med en parallell innsending.
        if (RefusjonskravTable.select(RefusjonskravTable.id).where { RefusjonskravTable.sakId eq sakId }.any()) {
            throw RefusjonskravFinnesAlleredeException()
        }
        val maksBelopKroner = hentInnvilgetBelopKroner(sakId) ?: throw InnvilgetBelopManglerException()
        if (belopOre > maksBelopKroner.toLong() * 100) {
            throw BelopOverInnvilgetException(maksBelopKroner)
        }
        registrerRefusjonskrav(
            sakId,
            soknadId,
            virksomhetsnummer,
            innsendtAv,
            belopOre,
            maksBelopKroner,
            filer,
        )
    }

    /**
     * Maksbeløpet i kroner som kan kreves for søknaden: innvilget beløp fra vedtaket. Leser uten lås, så
     * [lagreRefusjonskrav] kontrollerer beløpet på nytt.
     *
     * @throws SakIkkeFunnetException når søknaden ikke har sak
     * @throws SakKildeArenaException for Arena-saker
     * @throws InnvilgetBelopManglerException når saken ikke har innvilget beløp
     */
    fun hentMaksBelopKroner(soknadId: UUID): Int = transaction(database) {
        val sak = SakTable.select(SakTable.sakId, SakTable.kildeTilBehandling)
            .where { SakTable.soknadId eq soknadId }
            .singleOrNull()
            ?: throw SakIkkeFunnetException()
        if (sak[SakTable.kildeTilBehandling] == KildeTilBehandling.ARENA.name) {
            throw SakKildeArenaException()
        }
        hentInnvilgetBelopKroner(sak[SakTable.sakId]) ?: throw InnvilgetBelopManglerException()
    }

    /**
     * Henter metadata om siste refusjonskrav for en søknad: beløp, tidspunkt
     * og vedleggsliste (id + filnavn + størrelse). Filinnholdet (`innhold`) leses bevisst
     * ikke her; det hentes separat via [hentRefusjonsvedlegg] ved nedlasting.
     */
    fun finnRefusjonskravStatus(soknadId: UUID): RefusjonskravStatus? {
        val sakId = transaction(database) { hentSakIdForSoknad(soknadId) } ?: return null
        return finnRefusjonskravStatusForSak(sakId)
    }

    /** Henter status og vedleggmetadata for nyeste refusjonskrav knyttet til saken. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun finnRefusjonskravStatusForSak(sakId: UUID): RefusjonskravStatus? = transaction(database) {
        val krav = RefusjonskravTable
            .selectAll()
            .where { RefusjonskravTable.sakId eq sakId }
            .orderBy(
                RefusjonskravTable.opprettet to SortOrder.DESC,
                RefusjonskravTable.id to SortOrder.DESC,
            )
            .limit(1)
            .singleOrNull()
            ?: return@transaction null

        val refusjonskravId = krav[RefusjonskravTable.id].value
        val vedlegg = VedleggTable
            .select(VedleggTable.id, VedleggTable.filnavn, VedleggTable.storrelse)
            .where {
                (VedleggTable.refusjonskravId eq refusjonskravId) and
                    (VedleggTable.type eq VedleggType.REFUSJONSDOKUMENTASJON.name)
            }
            .orderBy(
                VedleggTable.lastetOpp to SortOrder.ASC,
                VedleggTable.id to SortOrder.ASC,
            )
            .map {
                RefusjonsvedleggMeta(
                    id = it[VedleggTable.id].value.toString(),
                    filnavn = it[VedleggTable.filnavn],
                    storrelse = it[VedleggTable.storrelse],
                )
            }

        RefusjonskravStatus(
            belopKroner = krav[RefusjonskravTable.belopOre] / 100,
            opprettet = krav[RefusjonskravTable.opprettet].toString(),
            // TODO: hent kontonummer fra SOKOS. Lagres ikke lokalt.
            kontonummer = null,
            vedlegg = vedlegg,
        )
    }

    /** Henter bare oppgitt vedlegg hvis det tilhører nyeste refusjonskrav for saken. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun hentNyesteRefusjonsvedleggForSak(
        sakId: UUID,
        vedleggId: UUID,
    ): RefusjonsvedleggInnhold? = transaction(database) {
        val refusjonskravId = RefusjonskravTable
            .select(RefusjonskravTable.id)
            .where { RefusjonskravTable.sakId eq sakId }
            .orderBy(
                RefusjonskravTable.opprettet to SortOrder.DESC,
                RefusjonskravTable.id to SortOrder.DESC,
            )
            .limit(1)
            .singleOrNull()
            ?.get(RefusjonskravTable.id)
            ?.value
            ?: return@transaction null

        VedleggTable
            .select(VedleggTable.filnavn, VedleggTable.innhold)
            .where {
                (VedleggTable.id eq vedleggId) and
                    (VedleggTable.refusjonskravId eq refusjonskravId) and
                    (VedleggTable.type eq VedleggType.REFUSJONSDOKUMENTASJON.name)
            }
            .singleOrNull()
            ?.let {
                RefusjonsvedleggInnhold(
                    filnavn = it[VedleggTable.filnavn],
                    innhold = it[VedleggTable.innhold],
                )
            }
    }

    /**
     * Henter filinnholdet for ett refusjonsvedlegg. Slår kun opp vedlegg som tilhører et
     * refusjonskrav på søknadens sak – hindrer at man laster ned vedlegg på tvers
     * av saker (IDOR).
     */
    fun hentRefusjonsvedlegg(soknadId: UUID, vedleggId: UUID): RefusjonsvedleggInnhold? =
        transaction(database) {
            val sakId = hentSakIdForSoknad(soknadId) ?: return@transaction null

            val vedlegg = VedleggTable
                .select(VedleggTable.filnavn, VedleggTable.innhold, VedleggTable.refusjonskravId)
                .where {
                    (VedleggTable.id eq vedleggId) and
                        (VedleggTable.type eq VedleggType.REFUSJONSDOKUMENTASJON.name)
                }
                .singleOrNull()
                ?: return@transaction null

            val refusjonskravId = vedlegg[VedleggTable.refusjonskravId] ?: return@transaction null
            val tilhorerSak = RefusjonskravTable
                .select(RefusjonskravTable.id)
                .where { (RefusjonskravTable.id eq refusjonskravId) and (RefusjonskravTable.sakId eq sakId) }
                .any()
            if (!tilhorerSak) return@transaction null

            RefusjonsvedleggInnhold(
                filnavn = vedlegg[VedleggTable.filnavn],
                innhold = vedlegg[VedleggTable.innhold],
            )
        }
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun JdbcTransaction.registrerRefusjonskrav(
    sakId: UUID,
    soknadId: UUID,
    virksomhetsnummer: String,
    innsendtAv: String,
    belopOre: Long,
    innvilgetBelopKroner: Int,
    filer: List<RefusjonsfilInput>,
): UUID {
    val refusjonskrav = RefusjonskravTable.insertReturning(
        listOf(RefusjonskravTable.id, RefusjonskravTable.opprettet)
    ) {
        it[RefusjonskravTable.sakId] = sakId
        it[RefusjonskravTable.belopOre] = belopOre
        it[status] = "MOTTATT"
    }.single()
    val refusjonskravId = refusjonskrav[RefusjonskravTable.id].value
    val opprettet = refusjonskrav[RefusjonskravTable.opprettet]

    val vedlegg = filer.map { fil ->
        val rad = VedleggTable.insertReturning(listOf(VedleggTable.id)) {
            it[type] = VedleggType.REFUSJONSDOKUMENTASJON.name
            it[filnavn] = fil.filnavn
            it[innhold] = fil.innhold
            it[storrelse] = fil.innhold.size
            it[VedleggTable.refusjonskravId] = refusjonskravId
        }.single()
        VedleggDomain(
            vedleggId = rad[VedleggTable.id].value.toString(),
            filnavn = fil.filnavn,
            storrelse = fil.innhold.size,
        )
    }

    publishEventQueue(
        EventData.RefusjonskravMottatt(
            refusjonskravId = refusjonskravId.toString(),
            sakId = sakId.toString(),
            soknadId = soknadId.toString(),
            virksomhetsnummer = virksomhetsnummer,
            innsendtAv = innsendtAv,
            belopOre = belopOre,
            innvilgetBelopKroner = innvilgetBelopKroner,
            vedlegg = vedlegg,
            tidspunkt = opprettet,
        )
    )
    publishEventQueue(
        EventData.SakOppdatert(
            sakId = sakId.toString(),
            soknadId = soknadId.toString(),
            utfortAvRolle = AktorRolle.SYSTEM,
            utfortAvIdent = null,
            notat = "Refusjonskrav mottatt fra arbeidsgiver",
            tidspunkt = opprettet,
        )
    )
    return refusjonskravId
}

data class RefusjonskravStatus(
    val belopKroner: Long,
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
