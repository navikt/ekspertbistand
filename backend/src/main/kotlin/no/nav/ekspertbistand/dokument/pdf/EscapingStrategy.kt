package no.nav.ekspertbistand.dokument.pdf

import com.github.jknack.handlebars.EscapingStrategy

/**
 * Escaping for alle `{{verdi}}`-uttrykk i malene. Uten Markdown-steget er dette det eneste
 * som står mellom fritekst fra arbeidsgiver og HTML-en som rendres av Chromium.
 *
 * 1. HTML-escaper `&`, `<`, `>`, `"`, `'`, `` ` `` og `=` (samme sett som Handlebars sin standard).
 * 2. Normaliserer `\r\n`/`\r` til `\n` og gjør deretter `\n` til `<br/>`, slik at brukerens
 *    linjeskift blir synlige i PDF-en.
 *
 * Linjeskift håndteres **etter** HTML-escaping, så `<br/>` er det eneste HTML-elementet en
 * verdi kan produsere.
 */
object HtmlEscapingStrategy : EscapingStrategy {
    override fun escape(value: CharSequence?): CharSequence {
        if (value == null) return ""
        val escaped = StringBuilder(value.length)
        for (c in value) {
            when (c) {
                '&' -> escaped.append("&amp;")
                '<' -> escaped.append("&lt;")
                '>' -> escaped.append("&gt;")
                '"' -> escaped.append("&quot;")
                '\'' -> escaped.append("&#x27;")
                '`' -> escaped.append("&#x60;")
                '=' -> escaped.append("&#x3D;")
                else -> escaped.append(c)
            }
        }
        return escaped
            .replace(Regex("\\r\\n|\\r"), "\n")
            .replace("\n", "<br/>")
    }
}
