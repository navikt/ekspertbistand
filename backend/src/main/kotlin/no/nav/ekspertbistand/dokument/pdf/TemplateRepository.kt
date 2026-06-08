package no.nav.ekspertbistand.dokument.pdf

import java.io.File
import java.net.URLDecoder
import java.util.jar.JarFile

/**
 * En innebygd font som skal registreres og embeddes i PDF/A-en.
 *
 * Holdes fri for openhtmltopdf-typer slik at [TemplateRepository] kan kopieres
 * mellom apper uten å dra med PDF-motoren. [PdfaRenderer] oversetter [italic]/[weight]
 * til motorens fontstil.
 */
data class FontResource(
    val family: String,
    val fileName: String,
    val bytes: ByteArray,
    val weight: Int,
    val italic: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is FontResource && fileName == other.fileName)

    override fun hashCode(): Int = fileName.hashCode()
}

/**
 * Laster maler, formater og fonter fra classpath under et konfigurerbart [resourcePrefix].
 *
 * Forventet struktur (identisk med dokgen sin `content/`):
 * ```
 * {resourcePrefix}/
 *   fonts/{font}.ttf
 *   formats/pdf/{style.css,header.html,footer.html}
 *   formats/html/style.css
 *   templates/{name}/template.hbs
 * ```
 *
 * Alt leses én gang og memoiseres. Tåler kjøring fra fat-jar.
 */
class TemplateRepository(
    private val resourcePrefix: String,
    private val fontFamily: String = "Source Sans Pro",
    private val classLoader: ClassLoader = TemplateRepository::class.java.classLoader,
) {
    private val templates = HashMap<String, String>()

    val pdfHeader: String by lazy { readText("formats/pdf/header.html") }
    val pdfFooter: String by lazy { readText("formats/pdf/footer.html") }
    val pdfCss: String by lazy { readText("formats/pdf/style.css") }
    val htmlCss: String by lazy { runCatching { readText("formats/html/style.css") }.getOrDefault("") }

    val fonts: List<FontResource> by lazy { loadFonts() }

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

    private fun loadFonts(): List<FontResource> =
        listResources("fonts", ".ttf").map { fileName ->
            val bytes = readBytes("fonts/$fileName")
            FontResource(
                family = fontFamily,
                fileName = fileName,
                bytes = bytes,
                weight = weightFor(fileName),
                italic = fileName.contains("It", ignoreCase = false) &&
                    fileName.substringBeforeLast(".").endsWith("It"),
            )
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

    /** Lister filnavn (uten katalog) under [dir] som slutter på [suffix]. */
    private fun listResources(dir: String, suffix: String): List<String> {
        val resourceDir = resourcePath(dir)
        val url = classLoader.getResource(resourceDir)
            ?: throw PdfGenerationException("Fant ikke katalog på classpath: $resourceDir")

        return when (url.protocol) {
            "file" -> File(url.toURI()).listFiles { f -> f.isFile && f.name.endsWith(suffix) }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()

            "jar" -> {
                val jarPath = url.path.substringBefore("!").removePrefix("file:")
                val decoded = URLDecoder.decode(jarPath, Charsets.UTF_8)
                JarFile(decoded).use { jar ->
                    jar.entries().asSequence()
                        .map { it.name }
                        .filter { it.startsWith("$resourceDir/") && it.endsWith(suffix) }
                        .map { it.substringAfterLast("/") }
                        .filter { it.isNotEmpty() }
                        .sorted()
                        .toList()
                }
            }

            else -> throw PdfGenerationException("Støtter ikke classpath-protokoll: ${url.protocol}")
        }
    }

    private fun weightFor(fileName: String): Int = when {
        fileName.contains("ExtraLight") -> 200
        fileName.contains("Light") -> 300
        fileName.contains("Semibold") -> 600
        fileName.contains("Bold") -> 700
        fileName.contains("Black") -> 900
        else -> 400
    }
}

