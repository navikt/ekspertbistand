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

    @Test
    fun `renser filnavn fra opplastingen`() = testApplication {
        rute()

        val respons = lastOpp { fil("../mappe/a.pdf", 1) }

        assertEquals(HttpStatusCode.OK, respons.status)
        assertEquals("a.pdf:1 ", respons.bodyAsText())
    }

    @Test
    fun `rensFilnavn beholder bare siste del av stien`() {
        assertEquals("a.pdf", rensFilnavn("../a.pdf"))
        assertEquals("a.pdf", rensFilnavn("C:\\Users\\x\\a.pdf"))
        assertEquals("a.pdf", rensFilnavn("/tmp/a.pdf"))
    }

    @Test
    fun `rensFilnavn fjerner kontrolltegn og linjeskift`() {
        assertEquals("ab.pdf", rensFilnavn("a\r\nb.pdf"))
        assertEquals("ab.pdf", rensFilnavn("a\u0000\u001Fb\u007F.pdf"))
        assertEquals("rapport 2026.pdf", rensFilnavn("  rapport 2026.pdf\t"))
    }

    @Test
    fun `rensFilnavn kutter til maks lengde uten å dele surrogatpar`() {
        assertEquals(MAKS_FILNAVN_LENGDE, rensFilnavn("x".repeat(300)).length)

        val emoji = "\uD83D\uDE00"
        val renset = rensFilnavn("x".repeat(MAKS_FILNAVN_LENGDE - 1) + emoji)
        assertEquals("x".repeat(MAKS_FILNAVN_LENGDE - 1), renset)
    }

    @Test
    fun `rensFilnavn gir standardnavn når ingenting er igjen`() {
        assertEquals("vedlegg.pdf", rensFilnavn(null))
        assertEquals("vedlegg.pdf", rensFilnavn(""))
        assertEquals("vedlegg.pdf", rensFilnavn(" \r\n\t "))
        assertEquals("vedlegg.pdf", rensFilnavn("mappe/"))
        assertEquals("vedlegg.pdf", rensFilnavn(".."))
        assertEquals("æøå.pdf", rensFilnavn("æøå.pdf"))
    }
}
