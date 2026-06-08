package no.nav.ekspertbistand.mocks

import kotlinx.serialization.json.JsonObject
import no.nav.ekspertbistand.dokument.pdf.PdfGenerator

/**
 * Deterministisk test-stub for [PdfGenerator]. Returnerer faste verdier og lar tester
 * eventuelt inspisere malnavn + data via callbacks.
 */
class StubPdfGenerator(
    private val pdf: ByteArray = "%PDF-mock".toByteArray(),
    private val html: String = "<html>Mock</html>",
    private val onRenderPdf: ((templateName: String, data: JsonObject) -> Unit)? = null,
    private val onRenderHtml: ((templateName: String, data: JsonObject) -> Unit)? = null,
) : PdfGenerator {
    override fun renderPdf(templateName: String, data: JsonObject, variation: String?): ByteArray {
        onRenderPdf?.invoke(templateName, data)
        return pdf
    }

    override fun renderHtml(templateName: String, data: JsonObject, variation: String?): String {
        onRenderHtml?.invoke(templateName, data)
        return html
    }
}

