package no.nav.ekspertbistand.saksbehandling

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger("Sakslogg")

const val SYSTEM_NAVN = "System"

@Serializable
data class SaksloggResponse(
    val innslag: List<SaksloggInnslag>,
)

@Serializable
data class SaksloggInnslag(
    val id: String,
    val tidspunkt: String,
    val utfortAvRolle: AktorRolle,
    val utfortAvIdent: String?,
    val utfortAvNavn: String,
    val notat: String?,
)

data class SaksloggRad(
    val id: String,
    val tidspunkt: String,
    val rolle: AktorRolle,
    val ident: String?,
    val notat: String?,
)

/** Fødselsnummeret til den ansatte saken gjelder, eller null hvis saken ikke finnes. */
fun Database.hentAnsattFnrForSak(sakId: UUID): String? = transaction(this) {
    SakTable
        .join(SoknadTable, JoinType.INNER, SakTable.soknadId, SoknadTable.id)
        .select(SoknadTable.ansattFnr)
        .where { SakTable.sakId eq sakId }
        .singleOrNull()
        ?.get(SoknadTable.ansattFnr)
}

/** Saksloggen for en sak, nyeste først. */
@OptIn(ExperimentalTime::class)
fun Database.hentSakslogg(sakId: UUID): List<SaksloggRad> = transaction(this) {
    SaksloggTable.selectAll()
        .where { SaksloggTable.sakId eq sakId }
        .orderBy(SaksloggTable.utfortAt to SortOrder.DESC)
        .map {
            SaksloggRad(
                id = it[SaksloggTable.saksloggId].toString(),
                tidspunkt = it[SaksloggTable.utfortAt].toString(),
                rolle = AktorRolle.valueOf(it[SaksloggTable.utfortAvRolle]),
                ident = it[SaksloggTable.utfortAvIdent],
                notat = it[SaksloggTable.notat],
            )
        }
}

/**
 * Slår opp visningsnavn for hver ident parallelt. Identer der oppslaget feiler eller
 * ikke gir noe navn, er utelatt fra resultatet.
 */
suspend fun EntraProxyClient.slaaOppNavn(identer: Set<String>): Map<String, String> = coroutineScope {
    identer.map { ident ->
        async {
            val navn = try {
                val ansatt = hentAnsatt(ident)
                ansatt.visningNavn
                    ?: listOfNotNull(ansatt.fornavn, ansatt.etternavn).joinToString(" ").ifBlank { null }
            } catch (e: Exception) {
                logger.warn("Klarte ikke slå opp navn for aktør i sakslogg", e)
                null
            }
            navn?.let { ident to it }
        }
    }.awaitAll().filterNotNull().toMap()
}

/** Setter sammen svaret. Mangler navnet, brukes identen. `SYSTEM` vises som [SYSTEM_NAVN]. */
fun List<SaksloggRad>.tilSaksloggResponse(navn: Map<String, String>) = SaksloggResponse(
    innslag = map { rad ->
        SaksloggInnslag(
            id = rad.id,
            tidspunkt = rad.tidspunkt,
            utfortAvRolle = rad.rolle,
            utfortAvIdent = rad.ident,
            utfortAvNavn = when {
                rad.rolle == AktorRolle.SYSTEM || rad.ident == null -> SYSTEM_NAVN
                else -> navn[rad.ident] ?: rad.ident
            },
            notat = rad.notat,
        )
    }
)
