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
object SaksbehandlerSaksfilterTable : Table("saksbehandler_saksfilter") {
    val navIdent = text("nav_ident")
    val enhet = text("enhet")
    val kommuner = array<String>("kommuner")
    val virksomhetstyper = array<String>("virksomhetstyper")
    val sistEndret = timestamp("sist_endret").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(navIdent)
}

/**
 * Saksfilteret over sakslisten, lagret per saksbehandler: enhet, kommuner og virksomhetstyper.
 *
 * GET /api/saksbehandling/v1/meg/saksfilter => lagret filter med kommunenavn
 * PUT /api/saksbehandling/v1/meg/saksfilter => erstatter filteret (204)
 *
 * Kommunene hører til enheten og returneres bare når den innloggede fortsatt har enheten. Virksomhetstypene
 * hører ikke til en enhet og returneres alltid. Lagrede typer som ikke lenger finnes i [Virksomhetstype],
 * hoppes over.
 *
 * Filteret er en brukerinnstilling, ikke tilstand på saken. PUT skriver derfor direkte til tabellen,
 * ikke via event og handler. Se `specifications/ruting_egne_ansatte_og_kommune.md`, del 2, og
 * `specifications/kategorisering_virksomhetstype.md`.
 */
suspend fun Application.configureSaksfilterApiV1() {
    val database = dependencies.resolve<Database>()
    val kommunenavnCache = dependencies.resolve<KommunenavnCache>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1/meg/saksfilter") {
                get {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@get
                    val lagret = transaction(database) { hentSaksfilter(principal.navIdent) }
                    if (lagret == null) {
                        return@get call.respond(
                            SaksfilterResponse(enhet = null, kommuner = emptyList(), virksomhetstyper = emptyList())
                        )
                    }
                    if (!principal.harTilgangTilEnhet(lagret.enhet)) {
                        return@get call.respond(
                            SaksfilterResponse(
                                enhet = null,
                                kommuner = emptyList(),
                                virksomhetstyper = lagret.virksomhetstyper,
                            )
                        )
                    }

                    val kommunenavn = if (lagret.kommuner.isEmpty()) emptyMap() else kommunenavnCache.hent()
                    call.respond(
                        SaksfilterResponse(
                            enhet = lagret.enhet,
                            kommuner = lagret.kommuner
                                .filter { kommunenavn.isEmpty() || it in kommunenavn }
                                .map { SaksfilterResponse.Kommune(nummer = it, navn = kommunenavn[it]) },
                            virksomhetstyper = lagret.virksomhetstyper,
                        )
                    )
                }

                put {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@put
                    val request = call.receive<SaksfilterRequest>().also { valider(it) }
                    if (!principal.harTilgangTilEnhet(request.enhet)) {
                        throw UgyldigInputException("enhet", "ikke en av enhetene dine")
                    }
                    request.kommuner.forEachIndexed { index, kommune ->
                        if (!kommune.matches(NorgKlient.KOMMUNENUMMER)) {
                            throw UgyldigInputException("kommuner[$index]", "må være fire sifre")
                        }
                    }

                    transaction(database) {
                        lagreSaksfilter(
                            navIdent = principal.navIdent,
                            enhet = request.enhet,
                            kommuner = request.kommuner.distinct().sorted(),
                            virksomhetstyper = request.virksomhetstyper.distinct().sorted(),
                        )
                    }
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private class LagretSaksfilter(
    val enhet: String,
    val kommuner: List<String>,
    val virksomhetstyper: List<Virksomhetstype>,
)

private fun hentSaksfilter(navIdent: String): LagretSaksfilter? =
    SaksbehandlerSaksfilterTable.selectAll()
        .where { SaksbehandlerSaksfilterTable.navIdent eq navIdent }
        .singleOrNull()
        ?.let {
            LagretSaksfilter(
                enhet = it[SaksbehandlerSaksfilterTable.enhet],
                kommuner = it[SaksbehandlerSaksfilterTable.kommuner],
                virksomhetstyper = it[SaksbehandlerSaksfilterTable.virksomhetstyper].mapNotNull { type ->
                    Virksomhetstype.entries.find { it.name == type }
                },
            )
        }

@OptIn(ExperimentalTime::class)
private fun lagreSaksfilter(
    navIdent: String,
    enhet: String,
    kommuner: List<String>,
    virksomhetstyper: List<Virksomhetstype>,
) {
    SaksbehandlerSaksfilterTable.upsert(
        onUpdateExclude = listOf(SaksbehandlerSaksfilterTable.navIdent),
    ) {
        it[SaksbehandlerSaksfilterTable.navIdent] = navIdent
        it[SaksbehandlerSaksfilterTable.enhet] = enhet
        it[SaksbehandlerSaksfilterTable.kommuner] = kommuner
        it[SaksbehandlerSaksfilterTable.virksomhetstyper] = virksomhetstyper.map { it.name }
        it[sistEndret] = CurrentTimestamp
    }
}

@Serializable
data class SaksfilterRequest(
    val enhet: String,
    val kommuner: List<String>,
    val virksomhetstyper: List<Virksomhetstype>,
)

/**
 * [enhet] er null når ingenting er lagret, eller når den innloggede ikke lenger har enheten. [kommuner] er da tom.
 * [virksomhetstyper] er de lagrede typene, også når enheten ikke lenger er tilgjengelig.
 */
@Serializable
data class SaksfilterResponse(
    val enhet: String?,
    val kommuner: List<Kommune>,
    val virksomhetstyper: List<Virksomhetstype>,
) {
    /** [navn] er null når Norg ikke kjenner kommunen eller ikke svarer. */
    @Serializable
    data class Kommune(
        val nummer: String,
        val navn: String?,
    )
}
