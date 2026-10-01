package no.nav.ekspertbistand.dokument.pdf

import kotlinx.serialization.json.JsonObject

/** Ønsket utdata-format fra [DokumentRenderer]. */
enum class Format { PDF, HTML }

/**
 * Domeneuavhengig templating-kjerne. Rendrer en Handlebars-mal til et komplett XHTML-dokument.
 * Kjenner ingenting til Ktor, Micrometer, Gotenberg eller resten av ekspertbistand.
 *
 * Pipeline: [TemplateRepository.loadTemplate] → [HandlebarsConfig.render] → innpakning. Ingen
 * Markdown. Malene er ren HTML med Handlebars-uttrykk.
 *
 * For [Format.PDF] pakkes `formats/pdf/style.css`, `header.html` og `footer.html` inn i `<body>`.
 * Malene bruker DejaVu Sans (systemfont i Gotenberg), så ingen fonter embeddes. For [Format.HTML]
 * brukes `formats/html/style.css` uten header/footer.
 */
class DokumentRenderer(
    resourcePrefix: String = "dokumentmaler",
    private val templates: TemplateRepository = TemplateRepository(resourcePrefix),
    private val handlebars: HandlebarsConfig = HandlebarsConfig(resourcePrefix),
) {
    fun renderHtml(templateName: String, data: JsonObject, format: Format): String =
        try {
            val templateSource = templates.loadTemplate(templateName)
            val content = handlebars.render(templateName, templateSource, data)
            when (format) {
                Format.PDF -> wrap(content, templates.pdfCss, templates.pdfHeader, templates.pdfFooter)
                Format.HTML -> wrap(content, templates.htmlCss, header = "", footer = "")
            }
        } catch (e: PdfGenerationException) {
            throw e
        } catch (e: Exception) {
            throw PdfGenerationException("Klarte ikke å rendre mal '$templateName' til $format", e)
        }

    private fun wrap(content: String, css: String, header: String, footer: String): String =
        buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"no\" xmlns=\"http://www.w3.org/1999/xhtml\">\n")
            append("<head>\n")
            append("<meta charset=\"UTF-8\"/>\n")
            append("<style type=\"text/css\">\n")
            append(css)
            append("\n</style>\n")
            append("</head>\n")
            append("<body>\n")
            append(header)
            append("\n<div id=\"content\">\n")
            append(content)
            append("\n</div>\n")
            append(footer)
            append("\n</body>\n")
            append("</html>\n")
        }
}
