package no.nav.ekspertbistand.dokument.pdf

import kotlinx.serialization.json.JsonObject

/**
 * Standardimplementasjon av [PdfGenerator] som speiler dokgen sin pipeline:
 *
 * 1. Handlebars rendrer `template.hbs` med data.
 * 2. Markdown → HTML (rå HTML i malen bevares).
 * 3. Fragmentet pakkes i et komplett HTML-dokument med header/footer/CSS og `@font-face`.
 * 4. (kun [renderPdf]) HTML → PDF/A via openhtmltopdf.
 */
class PdfGeneratorImpl(
    resourcePrefix: String,
    private val templates: TemplateRepository = TemplateRepository(resourcePrefix),
    private val handlebars: HandlebarsConfig = HandlebarsConfig(resourcePrefix),
    private val markdown: MarkdownRenderer = MarkdownRenderer(),
    private val pdfa: PdfaRenderer = PdfaRenderer(),
) : PdfGenerator {

    override fun renderPdf(templateName: String, data: JsonObject, variation: String?): ByteArray =
        try {
            val document = renderDocument(templateName, data, variation, templates.pdfCss, includeHeaderFooter = true)
            pdfa.render(document, templates.fonts)
        } catch (e: PdfGenerationException) {
            throw e
        } catch (e: Exception) {
            throw PdfGenerationException("Klarte ikke å generere PDF for mal '$templateName'", e)
        }

    override fun renderHtml(templateName: String, data: JsonObject, variation: String?): String =
        try {
            renderDocument(templateName, data, variation, templates.htmlCss, includeHeaderFooter = false)
        } catch (e: PdfGenerationException) {
            throw e
        } catch (e: Exception) {
            throw PdfGenerationException("Klarte ikke å generere HTML for mal '$templateName'", e)
        }

    private fun renderDocument(
        templateName: String,
        data: JsonObject,
        variation: String?,
        css: String,
        includeHeaderFooter: Boolean,
    ): String {
        val templateSource = templates.loadTemplate(templateName, variation)
        val rendered = handlebars.render(templateName + (variation?.let { "/$it" } ?: ""), templateSource, data)
        val contentHtml = markdown.render(rendered)

        val header = if (includeHeaderFooter) templates.pdfHeader else ""
        val footer = if (includeHeaderFooter) templates.pdfFooter else ""

        return buildString {
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
            append(contentHtml)
            append("\n</div>\n")
            append(footer)
            append("\n</body>\n")
            append("</html>\n")
        }
    }
}

