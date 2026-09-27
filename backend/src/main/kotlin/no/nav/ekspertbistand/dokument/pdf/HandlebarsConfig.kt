package no.nav.ekspertbistand.dokument.pdf

import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.ValueResolver
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Bygger en [Handlebars]-instans med en classpath-`TemplateLoader` rotet i [resourcePrefix]
 * slik at `{{> partial}}` virker, og registrerer standard NAV-helpers. Kompilerte maler caches.
 *
 * Escaping styres av [HtmlEscapingStrategy]: alle `{{verdi}}` HTML-escapes, og linjeskift blir
 * `<br/>`. Helpers som skriver ut data ([dateFormatHelper], [norwegianDateHelper]) returnerer
 * vanlig `String`, ikke `Handlebars.SafeString`, slik at også de escapes.
 *
 * I [strict]-modus feiler rendering med en `HandlebarsException` når malen refererer et felt som
 * ikke finnes i dataene (typos, felt som er fjernet). `null`-verdier slipper gjennom. Produksjon
 * kjører uten strict, så et uventet manglende felt gir tom tekst framfor en feilet journalføring.
 * Strict brukes bare i test (`DokumentmalTest`).
 */
class HandlebarsConfig(
    resourcePrefix: String,
    private val strict: Boolean = false,
) {
    private val handlebars: Handlebars = Handlebars(
        ClassPathTemplateLoader("/$resourcePrefix/templates", ".hbs")
    ).with(HtmlEscapingStrategy).apply {
        registerHelper("dateFormat", dateFormatHelper)
        registerHelper("norwegian-date", norwegianDateHelper)
        registerHelper("add", addHelper)
        registerHelper("eq", eqHelper)
    }

    private val cache = ConcurrentHashMap<String, Template>()

    /** Kompilerer (og cacher) malen og rendrer den med [data]. */
    fun render(templateName: String, templateSource: String, data: JsonObject): String {
        val template = cache.getOrPut(templateName) { handlebars.compileInline(templateSource) }
        val model = jsonToJava(data)
        return if (strict) {
            val context = Context.newBuilder(model)
                .resolver(StrictMapValueResolver)
                .build()
            template.apply(context)
        } else {
            template.apply(model)
        }
    }

    companion object {
        private val ISO_INPUT = DateTimeFormatter.ISO_LOCAL_DATE
        private val NORWEGIAN = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        private val dateFormatHelper = Helper<Any?> { context, options ->
            val pattern = options.param(0, "dd.MM.yyyy") as String
            formatIsoDate(context?.toString(), DateTimeFormatter.ofPattern(pattern))
        }

        private val norwegianDateHelper = Helper<Any?> { context, _ ->
            formatIsoDate(context?.toString(), NORWEGIAN)
        }

        private val addHelper = Helper<Any?> { context, options ->
            val a = (context?.toString())?.toLongOrNull() ?: 0L
            val b = (options.param(0, 0).toString()).toLongOrNull() ?: 0L
            a + b
        }

        private val eqHelper = Helper<Any?> { context, options ->
            val other = options.param<Any?>(0, null)
            if (context?.toString() == other?.toString()) options.fn() else options.inverse()
        }

        private fun formatIsoDate(value: String?, formatter: DateTimeFormatter): String {
            if (value.isNullOrBlank()) return ""
            return runCatching { LocalDate.parse(value, ISO_INPUT).format(formatter) }
                .getOrDefault(value)
        }

        /**
         * Konverterer kotlinx [JsonElement] til vanlige Java-typer (Map/List/String/Number/Boolean)
         * slik at Handlebars sin standard `MapValueResolver` kan navigere strukturen og leaf-verdier
         * skrives uten JSON-anførselstegn.
         */
        fun jsonToJava(element: JsonElement): Any? = when (element) {
            is JsonObject -> element.mapValues { jsonToJava(it.value) }
            is JsonArray -> element.map { jsonToJava(it) }
            is JsonNull -> null
            is JsonPrimitive ->
                if (element.isString) element.content
                else element.booleanOrNull
                    ?: element.longOrNull
                    ?: element.doubleOrNull
                    ?: element.content
        }
    }
}

/**
 * Strict variant av [MapValueResolver]: kaster [PdfGenerationException] når malen slår opp en
 * nøkkel som mangler helt i et [Map]. `null`-verdier (nøkkel finnes, verdi er `null`) slipper
 * gjennom. For ikke-Map-kontekster (for eksempel et list-element inne i `{{#each}}`) delegeres
 * til [MapValueResolver], som returnerer [ValueResolver.UNRESOLVED] slik at Handlebars kan gå
 * videre til foreldre-scope.
 */
private object StrictMapValueResolver : ValueResolver {
    override fun resolve(context: Any?, name: String): Any? {
        if (context is Map<*, *>) {
            if (!context.containsKey(name)) {
                throw PdfGenerationException("Malen refererer feltet '$name' som mangler i dataene")
            }
            return context[name]
        }
        return MapValueResolver.INSTANCE.resolve(context, name)
    }

    override fun resolve(context: Any?): Any? = MapValueResolver.INSTANCE.resolve(context)

    override fun propertySet(context: Any?): Set<Map.Entry<String, Any>> =
        MapValueResolver.INSTANCE.propertySet(context)
}
