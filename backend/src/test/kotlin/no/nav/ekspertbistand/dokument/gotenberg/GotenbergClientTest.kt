package no.nav.ekspertbistand.dokument.gotenberg

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import no.nav.ekspertbistand.dokument.pdf.PdfGenerationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GotenbergClientTest {

    private val pdfBytes = "%PDF-1.7\nmock".toByteArray()
    private val pdfHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
    private fun pdfHeaders(versjon: String) = headersOf(
        HttpHeaders.ContentType to listOf(ContentType.Application.Pdf.toString()),
        "Gotenberg-Version" to listOf(versjon),
    )
    private val stages = """[{"op":"remediate","profile":"PDF/A-2b"}]"""
    private val pdfaFelt = "name=\"pdfa\""

    private fun client(engine: MockEngine) = GotenbergClient(HttpClient(engine))

    @Test
    fun `sender html som multipart med skjemafeltene fra spec`() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = request.body.readAllBytes().toString(Charsets.ISO_8859_1)
            assertTrue(request.url.encodedPath.endsWith("/forms/chromium/convert/html"))
            respond(content = pdfBytes, headers = pdfHeaders("8.40.0"))
        }

        val result = client(engine).tilPdfA(html = "<html><body>hei</body></html>")

        assertContentEquals(pdfBytes, result)
        val multipart = requireNotNull(body)
        assertTrue(multipart.contains("filename=\"index.html\""), "HTML skal sendes som index.html")
        assertTrue(multipart.contains(pdfaFelt) && multipart.contains("PDF/A-2b"), "pdfa-feltet skal be om PDF/A-2b")
        assertTrue(multipart.contains("8.27in"), "paperWidth skal settes")
        assertTrue(multipart.contains("failOnResourceLoadingFailed"), "skal feile på ressurslasting")
    }

    @Test
    fun `bytter PDF-A-felt naar podene svarer med ulik Gotenberg-versjon`() = runTest {
        val sendt = mutableListOf<String>()
        val svar = ArrayDeque(
            listOf(
                HttpStatusCode.BadRequest to "9.8.0",
                HttpStatusCode.OK to "8.40.0",
                HttpStatusCode.OK to "8.40.0",
            )
        )
        val engine = MockEngine { request ->
            sendt += request.body.readAllBytes().toString(Charsets.ISO_8859_1)
            val (status, versjon) = svar.removeFirst()
            respond(content = pdfBytes, status = status, headers = pdfHeaders(versjon))
        }

        val result = client(engine).tilPdfA("<html/>")

        assertContentEquals(pdfBytes, result)
        assertEquals(3, sendt.size)
        assertTrue(sendt[0].contains(pdfaFelt), "første forsøk antar Gotenberg 8")
        assertTrue(sendt[1].contains(stages), "Gotenberg 9 svarte 400, neste forsøk bruker stages")
        assertTrue(sendt[2].contains(pdfaFelt), "Gotenberg 8 ignorerer stages, neste forsøk bruker pdfa")
    }

    @Test
    fun `husker Gotenberg-versjonen fra forrige svar`() = runTest {
        val sendt = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = request.body.readAllBytes().toString(Charsets.ISO_8859_1)
            sendt += body
            val status = if (body.contains(pdfaFelt)) HttpStatusCode.BadRequest else HttpStatusCode.OK
            respond(content = pdfBytes, status = status, headers = pdfHeaders("9.8.0"))
        }
        val client = client(engine)

        client.tilPdfA("<html/>")
        client.tilPdfA("<html/>")

        assertEquals(3, sendt.size, "andre konvertering skal ikke prøve pdfa først")
        assertTrue(sendt[2].contains(stages))
    }

    @Test
    fun `feiler paa 400 fra Gotenberg 9 selv med stages`() = runTest {
        val forsok = AtomicInteger(0)
        val engine = MockEngine {
            forsok.incrementAndGet()
            respond(content = "feil", status = HttpStatusCode.BadRequest, headers = pdfHeaders("9.8.0"))
        }

        assertFailsWith<PdfGenerationException> { client(engine).tilPdfA("<html/>") }
        assertEquals(2, forsok.get(), "ett forsøk med pdfa og ett med stages")
    }

    @Test
    fun `retry paa 503 og deretter suksess`() = runTest {
        val forsok = AtomicInteger(0)
        val engine = MockEngine {
            if (forsok.incrementAndGet() == 1) {
                respondError(HttpStatusCode.ServiceUnavailable)
            } else {
                respond(content = pdfBytes, headers = pdfHeaders)
            }
        }

        val result = client(engine).tilPdfA("<html/>")

        assertContentEquals(pdfBytes, result)
        assertEquals(2, forsok.get(), "skal ha gjort ett nytt forsøk etter 503")
    }

    @Test
    fun `ingen retry paa 400`() = runTest {
        val forsok = AtomicInteger(0)
        val engine = MockEngine {
            forsok.incrementAndGet()
            respondError(HttpStatusCode.BadRequest)
        }

        assertFailsWith<PdfGenerationException> {
            client(engine).tilPdfA("<html/>")
        }
        assertEquals(1, forsok.get(), "skal ikke gjøre nye forsøk på 400")
    }

    @Test
    fun `feiler naar svaret ikke starter med %PDF`() = runTest {
        val engine = MockEngine {
            respond(content = "ikke en pdf".toByteArray(), headers = pdfHeaders)
        }

        assertFailsWith<PdfGenerationException> {
            client(engine).tilPdfA("<html/>")
        }
    }

    @Test
    fun `feiler naar content-type ikke er pdf`() = runTest {
        val engine = MockEngine {
            respond(
                content = pdfBytes,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }

        assertFailsWith<PdfGenerationException> {
            client(engine).tilPdfA("<html/>")
        }
    }
}

private suspend fun OutgoingContent.readAllBytes(): ByteArray = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes()
    is OutgoingContent.WriteChannelContent -> coroutineScope {
        val channel = ByteChannel(autoFlush = true)
        launch {
            try {
                writeTo(channel)
            } finally {
                channel.flushAndClose()
            }
        }
        channel.readRemaining().readByteArray()
    }

    else -> ByteArray(0)
}
