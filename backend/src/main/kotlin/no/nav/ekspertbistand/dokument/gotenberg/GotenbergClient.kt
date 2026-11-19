package no.nav.ekspertbistand.dokument.gotenberg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.ekspertbistand.dokument.pdf.PdfGenerationException
import no.nav.ekspertbistand.dokument.pdf.PdfKonverterer
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.logger
import org.slf4j.MDC
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tynn HTTP-klient mot `ekspertbistand-gotenberg`. Konverterer ferdig rendret XHTML + fonter til
 * PDF/A-2b via Chromium-ruten `POST /forms/chromium/convert/html`.
 *
 * Klienten sender aldri maler eller data, bare ferdig HTML fra
 * [no.nav.ekspertbistand.dokument.pdf.DokumentRenderer]. Backend bruker aldri
 * `/forms/chromium/convert/url`.
 */
class GotenbergClient(
    defaultHttpClient: HttpClient,
) : PdfKonverterer {

    private val log = logger()

    companion object {
        val baseUrl: String = basedOnEnv(
            prod = "http://ekspertbistand-gotenberg",
            dev = "http://ekspertbistand-gotenberg",
            other = "http://localhost:3003",
        )
    }

    private val httpClient = defaultHttpClient.config {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "gotenberg.client"
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { _, response -> response.status.value == 503 }
            retryOnExceptionIf(3) { _, cause -> cause !is PdfGenerationException }
            exponentialDelay()
        }
    }

    // Gotenberg 8 lager PDF/A med feltet `pdfa`, Gotenberg 9 med en `remediate`-stage. Gotenberg 9
    // svarer 400 på `pdfa`, og Gotenberg 8 ignorerer `stages` og svarer 200 uten PDF/A. Podene kan ha
    // ulik versjon under utrullingen, så formatet velges ut fra Gotenberg-Version i hvert svar.
    // Fjernes når Gotenberg 9 kjører i prod.
    private val sistSettMajorversjon = AtomicInteger(8)

    override suspend fun tilPdfA(html: String): ByteArray {
        val response = konverterMedFormatForVersjon(html)

        if (!response.status.isSuccess()) {
            throw PdfGenerationException("Gotenberg svarte med status ${response.status.value} for HTML → PDF/A")
        }

        val contentType = response.contentType()
        if (contentType == null || !contentType.match(ContentType.Application.Pdf)) {
            throw PdfGenerationException(
                "Gotenberg returnerte uventet Content-Type '$contentType' (forventet application/pdf)"
            )
        }

        val bytes: ByteArray = response.body()
        if (!bytes.hasPdfHeader()) {
            throw PdfGenerationException("Gotenberg returnerte et svar som ikke starter med %PDF")
        }
        response.headers["Gotenberg-Remediation-Residual"]?.takeIf { it != "0" }?.let {
            log.warn("Gotenberg-PDF har {} gjenstående PDF/A-2b-avvik etter remediate", it)
        }
        return bytes
    }

    private suspend fun konverterMedFormatForVersjon(html: String): HttpResponse {
        var antattMajor = sistSettMajorversjon.get()
        repeat(3) {
            val response = konverter(html, brukStages = antattMajor >= 9)
            val svarMajor = response.headers["Gotenberg-Version"]?.substringBefore('.')?.toIntOrNull()
                ?: return response
            sistSettMajorversjon.set(svarMajor)
            if ((svarMajor >= 9) == (antattMajor >= 9)) return response
            antattMajor = svarMajor
        }
        throw PdfGenerationException("Gotenberg svarte med skiftende versjon i tre forsøk")
    }

    private suspend fun konverter(html: String, brukStages: Boolean): HttpResponse =
        httpClient.submitFormWithBinaryData(
            url = "$baseUrl/forms/chromium/convert/html",
            formData = formData {
                append(
                    "files",
                    html.toByteArray(Charsets.UTF_8),
                    Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                        append(HttpHeaders.ContentDisposition, "filename=\"index.html\"")
                    },
                )
                append("paperWidth", "8.27in")
                append("paperHeight", "11.69in")
                // Malene bruker fysiske pt-enheter (dpi-uavhengig). Marginene tilsvarer @page-margin
                // i PDF-CSS-en (64pt topp/sider, 74pt bunn), omregnet til tommer.
                append("marginTop", "0.889in")
                append("marginBottom", "1.028in")
                append("marginLeft", "0.889in")
                append("marginRight", "0.889in")
                append("preferCssPageSize", "false")
                append("printBackground", "true")
                if (brukStages) {
                    append("stages", """[{"op":"remediate","profile":"PDF/A-2b"}]""")
                } else {
                    append("pdfa", "PDF/A-2b")
                }
                append("failOnResourceLoadingFailed", "true")
                append("failOnConsoleExceptions", "true")
                append("skipNetworkIdleEvent", "true")
            },
        ) {
            MDC.get("x_correlation_id")?.let { header("Gotenberg-Trace", it) }
        }
}

private val pdfMagicNumber = "%PDF".toByteArray()

private fun ByteArray.hasPdfHeader() =
    size >= pdfMagicNumber.size && sliceArray(0 until pdfMagicNumber.size).contentEquals(pdfMagicNumber)
