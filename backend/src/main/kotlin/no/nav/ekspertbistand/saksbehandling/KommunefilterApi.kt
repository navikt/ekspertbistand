package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.UgyldigInputException
import no.nav.ekspertbistand.infrastruktur.valider
import no.nav.ekspertbistand.norg.KommunenavnCache
import no.nav.ekspertbistand.norg.NorgKlient
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object SaksbehandlerKommunefilterTable : Table("saksbehandler_kommunefilter") {
    val navIdent = text("nav_ident")
    val enhet = text("enhet")
    val kommuner = array<String>("kommuner")
    val sistEndret = timestamp("sist_endret").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(navIdent)
}

/**
 * Kommunefilteret over sakslisten, lagret per saksbehandler.
 *
 * GET /api/saksbehandling/v1/meg/kommunefilter => lagret filter med kommunenavn
 * PUT /api/saksbehandling/v1/meg/kommunefilter => erstatter filteret (204)
 *
 * Filteret er en brukerinnstilling, ikke tilstand på saken. PUT skriver derfor direkte til tabellen,
 * ikke via event og handler. Se `specifications/ruting_egne_ansatte_og_kommune.md`, del 2.
 */
suspend fun Application.configureKommunefilterApiV1() {
    val database = dependencies.resolve<Database>()
    val kommunenavnCache = dependencies.resolve<KommunenavnCache>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1/meg/kommunefilter") {
                get {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@get
                    val lagret = transaction(database) { hentKommunefilter(principal.navIdent) }
                    if (lagret == null || !principal.harTilgangTilEnhet(lagret.enhet)) {
                        return@get call.respond(KommunefilterResponse(enhet = null, kommuner = emptyList()))
                    }

                    val kommunenavn = if (lagret.kommuner.isEmpty()) emptyMap() else kommunenavnCache.hent()
                    call.respond(
                        KommunefilterResponse(
                            enhet = lagret.enhet,
                            kommuner = lagret.kommuner
                                .filter { kommunenavn.isEmpty() || it in kommunenavn }
                                .map { KommunefilterResponse.Kommune(nummer = it, navn = kommunenavn[it]) },
                        )
                    )
                }

                put {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@put
                    val request = call.receive<KommunefilterRequest>().also { valider(it) }
                    if (!principal.harTilgangTilEnhet(request.enhet)) {
                        throw UgyldigInputException("enhet", "ikke en av enhetene dine")
                    }
                    request.kommuner.forEachIndexed { index, kommune ->
                        if (!kommune.matches(NorgKlient.KOMMUNENUMMER)) {
                            throw UgyldigInputException("kommuner[$index]", "må være fire sifre")
                        }
                    }

                    transaction(database) {
                        lagreKommunefilter(principal.navIdent, request.enhet, request.kommuner.distinct().sorted())
                    }
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private class LagretKommunefilter(val enhet: String, val kommuner: List<String>)

private fun hentKommunefilter(navIdent: String): LagretKommunefilter? =
    SaksbehandlerKommunefilterTable.selectAll()
        .where { SaksbehandlerKommunefilterTable.navIdent eq navIdent }
        .singleOrNull()
        ?.let {
            LagretKommunefilter(
                enhet = it[SaksbehandlerKommunefilterTable.enhet],
                kommuner = it[SaksbehandlerKommunefilterTable.kommuner],
            )
        }

@OptIn(ExperimentalTime::class)
private fun lagreKommunefilter(navIdent: String, enhet: String, kommuner: List<String>) {
    SaksbehandlerKommunefilterTable.upsert(
        onUpdateExclude = listOf(SaksbehandlerKommunefilterTable.navIdent),
    ) {
        it[SaksbehandlerKommunefilterTable.navIdent] = navIdent
        it[SaksbehandlerKommunefilterTable.enhet] = enhet
        it[SaksbehandlerKommunefilterTable.kommuner] = kommuner
        it[sistEndret] = CurrentTimestamp
    }
}

@Serializable
data class KommunefilterRequest(
    val enhet: String,
    val kommuner: List<String>,
)

/** [enhet] er null når ingenting er lagret, eller når den innloggede ikke lenger har enheten. */
@Serializable
data class KommunefilterResponse(
    val enhet: String?,
    val kommuner: List<Kommune>,
) {
    /** [navn] er null når Norg ikke kjenner kommunen eller ikke svarer. */
    @Serializable
    data class Kommune(
        val nummer: String,
        val navn: String?,
    )
}
