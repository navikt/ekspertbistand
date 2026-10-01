package no.nav.ekspertbistand.mocks

import no.nav.ekspertbistand.dokument.pdf.PdfKonverterer

/**
 * Deterministisk test-stub for [PdfKonverterer]. Returnerer en fast `%PDF-mock` og lar tester
 * eventuelt inspisere HTML-en via [onConvert].
 */
class StubPdfKonverterer(
    private val pdf: ByteArray = "%PDF-mock".toByteArray(),
    private val onConvert: ((html: String) -> Unit)? = null,
) : PdfKonverterer {
    override suspend fun tilPdfA(html: String): ByteArray {
        onConvert?.invoke(html)
        return pdf
    }
}
