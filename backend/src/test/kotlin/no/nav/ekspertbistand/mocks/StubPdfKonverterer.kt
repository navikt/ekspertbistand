package no.nav.ekspertbistand.mocks

import no.nav.ekspertbistand.dokument.pdf.Asset
import no.nav.ekspertbistand.dokument.pdf.PdfKonverterer

/**
 * Deterministisk test-stub for [PdfKonverterer]. Returnerer en fast `%PDF-mock` og lar tester
 * eventuelt inspisere HTML-en og assetene via [onConvert].
 */
class StubPdfKonverterer(
    private val pdf: ByteArray = "%PDF-mock".toByteArray(),
    private val onConvert: ((html: String, assets: List<Asset>) -> Unit)? = null,
) : PdfKonverterer {
    override suspend fun tilPdfA(html: String, assets: List<Asset>): ByteArray {
        onConvert?.invoke(html, assets)
        return pdf
    }
}
