package no.nav.ekspertbistand.dokument.pdf

/**
 * Abstraksjon over HTML → PDF/A-konvertering. Kjernen ([DokumentRenderer]) produserer komplett
 * XHTML; en implementasjon gjør om det til PDF/A. I dag er
 * `no.nav.ekspertbistand.dokument.gotenberg.GotenbergClient` den eneste implementasjonen; i test
 * brukes en stub. Senere kan en in-process openhtmltopdf-variant plugges inn bak samme interface.
 *
 * Malene bruker DejaVu Sans, som er installert i Gotenberg-containeren (både Chromium og
 * LibreOffice). Da trenger vi ikke embedde egne fonter, og PDF/A-konverteringen reflyter ikke
 * teksten med en bredere erstatningsfont.
 */
interface PdfKonverterer {
    /** Konverterer [html] til PDF/A. */
    suspend fun tilPdfA(html: String): ByteArray
}
