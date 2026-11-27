package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Maks antall tegn i et notat, etter trimming. Speiles i `frontend/saksbehandling/src/components/Saksnotater.tsx`. */
const val SAKSNOTAT_MAKS_LENGDE = 1000

/** Felles krav i `init` for notat-eventene. */
internal fun kravTilSaksnotat(tekst: String, rolle: AktorRolle, ident: String) {
    require(tekst.isNotBlank()) { "tekst kan ikke være blank" }
    require(tekst.length <= SAKSNOTAT_MAKS_LENGDE) { "tekst kan ikke være over $SAKSNOTAT_MAKS_LENGDE tegn" }
    require(rolle != AktorRolle.SYSTEM) { "SYSTEM kan ikke skrive notater" }
    require(ident.isNotBlank()) { "ident kan ikke være blank" }
}

@Serializable
data class SaksnotaterResponse(val notater: List<Saksnotat>)

@Serializable
data class Saksnotat(
    val notatId: String,
    val tekst: String,
    val skrevetAvIdent: String,
    val skrevetAvNavn: String,
    val skrevetAvRolle: AktorRolle,
    val opprettet: String,
    val endret: String?,
)

/** Notatene på saken, nyeste først. Må kalles i en transaksjon. */
@OptIn(ExperimentalTime::class)
internal fun JdbcTransaction.hentSaksnotater(sakId: UUID): List<Saksnotat> =
    SaksnotatTable.selectAll()
        .where { SaksnotatTable.sakId eq sakId }
        .orderBy(SaksnotatTable.opprettetAt to SortOrder.DESC)
        .map {
            Saksnotat(
                notatId = it[SaksnotatTable.notatId].toString(),
                tekst = it[SaksnotatTable.tekst],
                skrevetAvIdent = it[SaksnotatTable.skrevetAvIdent],
                skrevetAvNavn = it[SaksnotatTable.skrevetAvNavn],
                skrevetAvRolle = AktorRolle.valueOf(it[SaksnotatTable.skrevetAvRolle]),
                opprettet = it[SaksnotatTable.opprettetAt].toString(),
                endret = it[SaksnotatTable.endretAt]?.toString(),
            )
        }

/** Identen til den som skrev notatet, eller null hvis notatet ikke finnes på [sakId]. */
internal fun JdbcTransaction.hentSaksnotatForfatter(sakId: UUID, notatId: UUID): String? =
    SaksnotatTable.select(SaksnotatTable.skrevetAvIdent)
        .where { (SaksnotatTable.notatId eq notatId) and (SaksnotatTable.sakId eq sakId) }
        .singleOrNull()
        ?.get(SaksnotatTable.skrevetAvIdent)

/** Lagrer notatet. Returnerer false hvis [notatId] finnes fra før. */
@OptIn(ExperimentalTime::class)
internal fun JdbcTransaction.lagreSaksnotat(
    notatId: UUID,
    sakId: UUID,
    tekst: String,
    rolle: AktorRolle,
    ident: String,
    navn: String,
    tidspunkt: Instant,
): Boolean = SaksnotatTable.insertIgnore {
    it[SaksnotatTable.notatId] = notatId
    it[SaksnotatTable.sakId] = sakId
    it[SaksnotatTable.tekst] = tekst
    it[skrevetAvRolle] = rolle.name
    it[skrevetAvIdent] = ident
    it[skrevetAvNavn] = navn
    it[opprettetAt] = tidspunkt
}.insertedCount > 0

/**
 * Endrer teksten hvis notatet finnes og er skrevet av [ident]. Returnerer false ellers, for eksempel
 * når notatet er slettet i mellomtiden.
 */
@OptIn(ExperimentalTime::class)
internal fun JdbcTransaction.endreSaksnotat(notatId: UUID, ident: String, tekst: String, tidspunkt: Instant): Boolean =
    SaksnotatTable.update(
        where = { (SaksnotatTable.notatId eq notatId) and (SaksnotatTable.skrevetAvIdent eq ident) },
    ) {
        it[SaksnotatTable.tekst] = tekst
        it[endretAt] = tidspunkt
    } > 0

/** Sletter notatet hvis det finnes og er skrevet av [ident]. Returnerer false ellers. */
internal fun JdbcTransaction.slettSaksnotat(notatId: UUID, ident: String): Boolean =
    SaksnotatTable.deleteWhere {
        (SaksnotatTable.notatId eq notatId) and (SaksnotatTable.skrevetAvIdent eq ident)
    } > 0
