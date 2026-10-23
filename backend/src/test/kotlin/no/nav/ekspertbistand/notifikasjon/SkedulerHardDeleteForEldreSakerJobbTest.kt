package no.nav.ekspertbistand.notifikasjon

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.notifikasjon.SkedulerHardDeleteForEldreSakerJobb.Utfall
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SkedulerHardDeleteForEldreSakerJobbTest {

    private val now = Instant.parse("2026-10-23T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@SkedulerHardDeleteForEldreSakerJobbTest.now
    }

    @Test
    fun `skedulerer hard delete 455 dager etter siste oppdatering, og fullfører bare en gang`() =
        testApplicationWithDatabase { db ->
            val kall = settOppProdusentApi()
            startApplication()

            transaction(db.config.jdbcDatabase) {
                // Søknad med flere events: siste event styrer.
                settInnEvent(1, "soknad-a", "2026-01-01 10:00:00")
                settInnEvent(2, "soknad-a", "2026-03-01 10:00:00")
                // Legacy-tilsagn fra Altinn, grupperingsid er tilsagnsnummeret.
                settInnEvent(3, "2025:1:1", "2025-12-01 12:00:00")
                // Aggregat uten sak i plattformen.
                settInnEvent(4, "finnes-ikke", "2026-02-01 00:00:00")
                // Fristen er passert (skal ikke finnes, men rapporteres).
                settInnEvent(5, "gammel", "2025-01-01 00:00:00")
            }

            val jobb = jobb(db)
            assertEquals(Utfall.FERDIG, jobb.kjorEnGang())

            assertEquals(
                mapOf(
                    // 2026-03-01T10:00Z + 455 dager = 2027-05-30T10:00Z = 12:00 Oslo (sommertid)
                    "soknad-a" to "2027-05-30T12:00:00",
                    // 2025-12-01T12:00Z + 455 dager = 2027-03-01T12:00Z = 13:00 Oslo (normaltid)
                    "2025:1:1" to "2027-03-01T13:00:00",
                    "finnes-ikke" to "2027-05-02T02:00:00",
                ),
                kall.associate { it.grupperingsid to it.den },
            )
            assertEquals(setOf("FORLENG"), kall.map { it.strategi }.toSet())

            kall.clear()
            assertEquals(Utfall.ANNEN_POD, jobb.kjorEnGang())
            assertEquals(0, kall.size)
            assertEquals(true, erFullfort(db))
        }

    @Test
    fun `fullfører ikke når et kall feiler`() = testApplicationWithDatabase { db ->
        settOppProdusentApi()
        startApplication()

        transaction(db.config.jdbcDatabase) {
            settInnEvent(1, "feiler", "2026-03-01 10:00:00")
            settInnEvent(2, "soknad-b", "2026-03-01 10:00:00")
        }

        val jobb = jobb(db)
        assertEquals(Utfall.FEIL, jobb.kjorEnGang())
        assertEquals(false, erFullfort(db))
        // Leasen er sluppet, så neste forsøk kan starte med en gang.
        assertEquals(Utfall.FEIL, jobb.kjorEnGang())
    }

    private fun ApplicationTestBuilder.jobb(db: TestDatabase) = SkedulerHardDeleteForEldreSakerJobb(
        database = db.config.jdbcDatabase,
        produsentApiKlient = ProdusentApiKlient(successAzureAdTokenProvider, client),
        clock = clock,
        pauseMellomKall = ZERO,
    )

    private data class Kall(val grupperingsid: String, val den: String, val strategi: String)

    private fun ApplicationTestBuilder.settOppProdusentApi(): MutableList<Kall> {
        val kall = Collections.synchronizedList(mutableListOf<Kall>())
        externalServices {
            hosts("http://notifikasjon-produsent-api.fager") {
                routing {
                    post("/api/graphql") {
                        val variables = Json.parseToJsonElement(call.receiveText()).jsonObject["variables"]!!.jsonObject
                        val grupperingsid = variables["grupperingsid"]!!.jsonPrimitive.content
                        val hardDelete = variables["hardDelete"]!!.jsonObject
                        if (grupperingsid == "feiler") {
                            call.respond(HttpStatusCode.InternalServerError)
                            return@post
                        }
                        kall += Kall(
                            grupperingsid = grupperingsid,
                            den = hardDelete["nyTid"]!!.jsonObject["den"]!!.jsonPrimitive.content,
                            strategi = hardDelete["strategi"]!!.jsonPrimitive.content,
                        )
                        val resultat = if (grupperingsid == "finnes-ikke") {
                            """{"__typename": "SakFinnesIkke", "feilmelding": "finnes ikke"}"""
                        } else {
                            """{"__typename": "SkedulerHardDeleteSakVellykket", "id": "$grupperingsid"}"""
                        }
                        call.respondText(
                            """{"data": {"skedulerHardDeleteSakByGrupperingsid": $resultat}}""",
                            ContentType.Application.Json,
                        )
                    }
                }
            }
        }
        return kall
    }

    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.settInnEvent(id: Long, aggregateRootId: String, createdAt: String) =
        exec(
            """
            INSERT INTO event_log (id, event_json, status, created_at, updated_at, aggregate_root_id)
            VALUES ($id, '{}', 0, ?::timestamp, ?::timestamp, ?)
            """.trimIndent(),
            listOf(TextColumnType() to createdAt, TextColumnType() to createdAt, TextColumnType() to aggregateRootId),
        )

    private fun erFullfort(db: TestDatabase): Boolean = transaction(db.config.jdbcDatabase) {
        exec(
            "SELECT completed_at IS NOT NULL FROM backfill_state WHERE job_name = ?",
            listOf(TextColumnType() to SkedulerHardDeleteForEldreSakerJobb.JOB_NAME),
        ) { rs -> rs.next() && rs.getBoolean(1) } ?: false
    }
}
