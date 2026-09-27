package no.nav.ekspertbistand.dokument.pdf

/**
 * En fil som følger med HTML-en til PDF-konverteringen, typisk en font. Refereres fra
 * `@font-face` i HTML-en med [fileName] alene.
 */
data class Asset(
    val fileName: String,
    val bytes: ByteArray,
    val contentType: String = "font/ttf",
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Asset && fileName == other.fileName)

    override fun hashCode(): Int = fileName.hashCode()
}

/**
 * Abstraksjon over HTML → PDF/A-konvertering. Kjernen ([DokumentRenderer]) produserer komplett
 * XHTML og [Asset]-er; en implementasjon gjør om det til PDF/A. I dag er
 * `no.nav.ekspertbistand.dokument.gotenberg.GotenbergClient` den eneste implementasjonen; i test
 * brukes en stub. Senere kan en in-process openhtmltopdf-variant plugges inn bak samme interface.
 */
interface PdfKonverterer {
    /** Konverterer [html] (med [assets] tilgjengelig i samme katalog) til PDF/A. */
    suspend fun tilPdfA(html: String, assets: List<Asset>): ByteArray
}
