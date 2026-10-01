package no.nav.ekspertbistand.dokument.pdf

import java.util.concurrent.ConcurrentHashMap

/**
 * Laster maler og formater fra classpath under et konfigurerbart [resourcePrefix].
 *
 * Forventet struktur (identisk med dokgen sin `content/`):
 * ```
 * {resourcePrefix}/
 *   formats/pdf/{style.css,header.html,footer.html}
 *   formats/html/style.css
 *   templates/{name}/template.hbs
 * ```
 *
 * Alt leses én gang og memoiseres. Tåler kjøring fra fat-jar.
 */
class TemplateRepository(
    private val resourcePrefix: String,
    private val classLoader: ClassLoader = TemplateRepository::class.java.classLoader,
) {
    private val templates = ConcurrentHashMap<String, String>()

    val pdfHeader: String by lazy { readText("formats/pdf/header.html") }
    val pdfFooter: String by lazy { readText("formats/pdf/footer.html") }
    val pdfCss: String by lazy { readText("formats/pdf/style.css") }
    val htmlCss: String by lazy { runCatching { readText("formats/html/style.css") }.getOrDefault("") }


    /**
     * Leser `templates/{name}/template.hbs`, eller `templates/{name}/{variation}.hbs`
     * dersom [variation] er satt.
     */
    fun loadTemplate(name: String, variation: String? = null): String {
        val path = if (variation == null) {
            "templates/$name/template.hbs"
        } else {
            "templates/$name/$variation.hbs"
        }
        return templates.getOrPut(path) { readText(path) }
    }

    private fun resourcePath(relative: String) = "$resourcePrefix/$relative"

    private fun readText(relative: String): String =
        readBytes(relative).toString(Charsets.UTF_8)

    private fun readBytes(relative: String): ByteArray {
        val path = resourcePath(relative)
        return (classLoader.getResourceAsStream(path)
            ?: throw PdfGenerationException("Fant ikke ressurs på classpath: $path"))
            .use { it.readBytes() }
    }
}

