package no.nav.ekspertbistand.dokument.pdf

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Markdown → HTML, med rå HTML i input bevart uendret (samme oppførsel som dokgen).
 *
 * Rekkefølgen i pipelinen er: Handlebars → [MarkdownRenderer] → innpakning → PDF.
 * Maler kan derfor blande Markdown (`# overskrift`, lenker) og rå HTML (`<table>`).
 */
class MarkdownRenderer {
    private val parser: Parser = Parser.builder().build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        // Behold rå HTML i malen (ikke escape) – dokgen gjør det samme.
        .escapeHtml(false)
        .build()

    fun render(markdown: String): String = renderer.render(parser.parse(markdown))
}

