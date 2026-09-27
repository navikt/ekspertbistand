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

    private fun client(engine: MockEngine) = GotenbergClient(HttpClient(engine))

    @Test
    fun `sender html som multipart med skjemafeltene fra spec`() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = request.body.readAllBytes().toString(Charsets.ISO_8859_1)
            assertTrue(request.url.encodedPath.endsWith("/forms/chromium/convert/html"))
            respond(content = pdfBytes, headers = pdfHeaders)
        }

        val result = client(engine).tilPdfA(html = "<html><body>hei</body></html>")

        assertContentEquals(pdfBytes, result)
        val multipart = requireNotNull(body)
        assertTrue(multipart.contains("filename=\"index.html\""), "HTML skal sendes som index.html")
        assertTrue(multipart.contains("PDF/A-2b"), "pdfa-feltet skal be om PDF/A-2b")
        assertTrue(multipart.contains("8.27in"), "paperWidth skal settes")
        assertTrue(multipart.contains("failOnResourceLoadingFailed"), "skal feile på ressurslasting")
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
