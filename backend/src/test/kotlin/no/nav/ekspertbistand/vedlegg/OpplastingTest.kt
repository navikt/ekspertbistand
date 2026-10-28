package no.nav.ekspertbistand.vedlegg

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.configureServer
import kotlin.test.Test
import kotlin.test.assertEquals

class OpplastingTest {

    private val maksStorrelse = 100

    private fun ApplicationTestBuilder.rute() {
        application { configureServer() }
        routing {
            post("/opplasting") {
                when (val opplasting = call.lesOpplasting(2, maksStorrelse, setOf("felt"))) {
                    is Opplasting.Ok -> call.respondText(
                        opplasting.filer.joinToString(",") { "${it.filnavn}:${it.innhold.size}" } +
                            " " + opplasting.felter.entries.joinToString(",") { "${it.key}=${it.value}" }
                    )
                    is Opplasting.ForMangeFiler -> call.respond(HttpStatusCode.BadRequest, "for mange filer")
                    is Opplasting.ForStorFil -> call.respond(HttpStatusCode.PayloadTooLarge, "for stor fil")
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.lastOpp(block: FormBuilder.() -> Unit): HttpResponse =
        client.post("/opplasting") {
            setBody(MultiPartFormDataContent(formData(block)))
        }

    private fun FormBuilder.fil(navn: String, storrelse: Int) =
        append("fil", ByteArray(storrelse), Headers.build {
            append(HttpHeaders.ContentType, "application/pdf")
            append(HttpHeaders.ContentDisposition, "filename=\"$navn\"")
        })

    @Test
    fun `leser filer opp til grensene og bare kjente felt`() = testApplication {
        rute()

        val respons = lastOpp {
            append("felt", "verdi")
            append("ukjent", "ignoreres")
            fil("a.pdf", maksStorrelse)
            fil("b.pdf", 1)
        }

        assertEquals(HttpStatusCode.OK, respons.status)
        assertEquals("a.pdf:$maksStorrelse,b.pdf:1 felt=verdi", respons.bodyAsText())
    }

    @Test
    fun `avviser for mange filer`() = testApplication {
        rute()

        val respons = lastOpp {
            fil("a.pdf", 1)
            fil("b.pdf", 1)
            fil("c.pdf", 1)
        }

        assertEquals(HttpStatusCode.BadRequest, respons.status)
        assertEquals("for mange filer", respons.bodyAsText())
    }

    @Test
    fun `avviser fil som er en byte for stor`() = testApplication {
        rute()

        assertEquals(HttpStatusCode.PayloadTooLarge, lastOpp { fil("a.pdf", maksStorrelse + 1) }.status)
    }

    @Test
    fun `avviser fil som er mye storre enn grensen`() = testApplication {
        rute()

        assertEquals(HttpStatusCode.PayloadTooLarge, lastOpp { fil("a.pdf", 5 * 1024 * 1024) }.status)
    }

    @Test
    fun `avviser skjemafelt over grensen`() = testApplication {
        rute()

        assertEquals(HttpStatusCode.PayloadTooLarge, lastOpp { append("felt", "x".repeat(maksStorrelse + 1)) }.status)
    }
}
