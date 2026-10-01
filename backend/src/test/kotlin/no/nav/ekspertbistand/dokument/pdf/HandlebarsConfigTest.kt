package no.nav.ekspertbistand.dokument.pdf

import com.github.jknack.handlebars.HandlebarsException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HandlebarsConfigTest {

    private val strict = HandlebarsConfig("dokumentmaler", strict = true)

    @Test
    fun `strict godtar helpers selv om helper-navnet ikke finnes i dataene`() {
        val html = strict.render(
            "helper",
            "{{norwegian-date dato}}",
            buildJsonObject { put("dato", "2026-10-01") },
        )

        assertEquals("01.10.2026", html)
    }

    @Test
    fun `strict feiler fortsatt paa manglende felt`() {
        val feil = assertFailsWith<HandlebarsException> {
            strict.render("manglende", "{{finnesIkke}}", buildJsonObject { put("dato", "2026-10-01") })
        }

        assertTrue(feil.cause is PdfGenerationException)
    }

    @Test
    fun `strict feiler paa manglende helper-argument`() {
        assertFailsWith<HandlebarsException> {
            strict.render("manglende-arg", "{{norwegian-date finnesIkke}}", buildJsonObject { })
        }
    }
}
