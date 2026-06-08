package no.nav.ekspertbistand.dokument.pdf

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Template
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
 */
class HandlebarsConfig(
    resourcePrefix: String,
) {
    private val handlebars: Handlebars = Handlebars(
        ClassPathTemplateLoader("/$resourcePrefix/templates", ".hbs")
    ).apply {
        registerHelper("dateFormat", dateFormatHelper)
        registerHelper("norwegian-date", norwegianDateHelper)
        registerHelper("add", addHelper)
        registerHelper("eq", eqHelper)
    }

    private val cache = ConcurrentHashMap<String, Template>()

    /** Kompilerer (og cacher) malen og rendrer den med [data]. */
    fun render(templateName: String, templateSource: String, data: JsonObject): String {
        val template = cache.getOrPut(templateName) { handlebars.compileInline(templateSource) }
        return template.apply(jsonToJava(data))
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

