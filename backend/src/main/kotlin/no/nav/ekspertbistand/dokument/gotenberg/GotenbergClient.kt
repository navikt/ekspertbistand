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
import no.nav.ekspertbistand.dokument.pdf.Asset
import no.nav.ekspertbistand.dokument.pdf.PdfGenerationException
import no.nav.ekspertbistand.dokument.pdf.PdfKonverterer
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import org.slf4j.MDC

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

    override suspend fun tilPdfA(html: String, assets: List<Asset>): ByteArray {
        val response: HttpResponse = httpClient.submitFormWithBinaryData(
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
                assets.forEach { asset ->
                    append(
                        "files",
                        asset.bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, asset.contentType)
                            append(HttpHeaders.ContentDisposition, "filename=\"${asset.fileName}\"")
                        },
                    )
                }
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
                append("pdfa", "PDF/A-2b")
                append("failOnResourceLoadingFailed", "true")
                append("failOnConsoleExceptions", "true")
                append("skipNetworkIdleEvent", "true")
            },
        ) {
            MDC.get("x_correlation_id")?.let { header("Gotenberg-Trace", it) }
        }

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
        return bytes
    }
}

private val pdfMagicNumber = "%PDF".toByteArray()

private fun ByteArray.hasPdfHeader() =
    size >= pdfMagicNumber.size && sliceArray(0 until pdfMagicNumber.size).contentEquals(pdfMagicNumber)
