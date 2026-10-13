package no.nav.ekspertbistand.mocks

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import no.nav.ekspertbistand.dokument.gotenberg.GotenbergClient
import no.nav.ekspertbistand.dokument.pdf.PdfKonverterer

/**
 * PDF-konvertering for `LocalApplication`. Bruker Gotenberg på `localhost:3003` når den kjører
 * (`docker compose -f backend/docker-compose.yml up -d gotenberg`). Ellers svarer den med en gyldig
 * PDF som sier at Gotenberg mangler, så PDF-forhåndsvisningene i frontend kan vises.
 */
class LokalPdfKonverterer(httpClient: HttpClient) : PdfKonverterer {
    private val gotenberg = GotenbergClient(httpClient)
    private val helsesjekk = httpClient.config {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 500
            requestTimeoutMillis = 1_000
        }
    }

    override suspend fun tilPdfA(html: String): ByteArray =
        if (gotenbergKjorer()) gotenberg.tilPdfA(html) else UTEN_GOTENBERG

    private suspend fun gotenbergKjorer() =
        runCatching { helsesjekk.get("${GotenbergClient.baseUrl}/health").status.isSuccess() }
            .getOrDefault(false)

    companion object {
        val UTEN_GOTENBERG: ByteArray = enkelPdf(
            "Gotenberg kjorer ikke lokalt.",
            "Start den med: docker compose -f backend/docker-compose.yml up -d gotenberg",
        )
    }
}

/** En A4-side med [linjer] i Helvetica. Teksten må være ASCII uten parenteser og backslash. */
internal fun enkelPdf(vararg linjer: String): ByteArray {
    val tekst = linjer.withIndex().joinToString("\n") { (i, linje) ->
        "BT /F1 12 Tf 56 ${780 - i * 20} Td ($linje) Tj ET"
    }
    val objekter = listOf(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
            "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        "<< /Length ${tekst.length} >>\nstream\n$tekst\nendstream",
    )
    val pdf = StringBuilder("%PDF-1.4\n")
    val offsetter = objekter.mapIndexed { i, objekt ->
        pdf.length.also { pdf.append("${i + 1} 0 obj\n$objekt\nendobj\n") }
    }
    val xref = pdf.length
    pdf.append("xref\n0 ${objekter.size + 1}\n0000000000 65535 f \n")
    offsetter.forEach { pdf.append("%010d 00000 n \n".format(it)) }
    pdf.append("trailer\n<< /Size ${objekter.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return pdf.toString().toByteArray(Charsets.US_ASCII)
}
