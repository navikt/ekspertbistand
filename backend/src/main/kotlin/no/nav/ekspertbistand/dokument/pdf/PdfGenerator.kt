package no.nav.ekspertbistand.dokument.pdf

import kotlinx.serialization.json.JsonObject

/**
 * Domeneuavhengig dokumentgenerering. Tar et malnavn + generisk [JsonObject]-datamodell
 * og returnerer PDF/A (`ByteArray`) eller HTML (`String`).
 *
 * Kjernen kjenner ingenting til app-spesifikke DTO-er, Ktor, Arena eller Joark. For å
 * gjenbruke den i en annen app: kopier pakken + `resources/{resourcePrefix}/`, sett
 * `resourcePrefix` og legg til de fire avhengighetene (handlebars, commonmark,
 * openhtmltopdf-pdfbox, openhtmltopdf-svg-support).
 */
interface PdfGenerator {
    /** Render mal -> PDF/A. Kaster [PdfGenerationException] ved feil. */
    fun renderPdf(templateName: String, data: JsonObject, variation: String? = null): ByteArray

    /** Render mal -> HTML (samme HTML som går inn i PDF, uten PDF-konvertering). */
    fun renderHtml(templateName: String, data: JsonObject, variation: String? = null): String
}

