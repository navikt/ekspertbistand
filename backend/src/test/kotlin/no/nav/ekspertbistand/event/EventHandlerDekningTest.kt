package no.nav.ekspertbistand.event

import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.allSupertypes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sjekker at alle events har minst én handler registrert i `configureEventHandlers`. En event uten
 * handler blir liggende i køen med `TransientError` og prøves på nytt i det uendelige.
 *
 * Registreringen leses fra kildekoden i `Events.kt`, slik at handlere for alle miljøer telles med.
 */
class EventHandlerDekningTest {

    private val eventsKt = listOf(File("src/main/kotlin"), File("backend/src/main/kotlin"))
        .first { it.isDirectory }
        .resolve("no/nav/ekspertbistand/event/Events.kt")
        .readText()
        .replace(Regex("""(?m)^\s*//.*$"""), "")

    @Test
    fun `alle events har en registrert handler`() {
        val fraHandlerklasser = Regex("""dependencies\.create\(\s*(\w+)::class\s*\)""")
            .findAll(eventsKt)
            .map { eventTypeFor(Class.forName("no.nav.ekspertbistand.event.handlers.${it.groupValues[1]}").kotlin) }
        val fraInlineHandlere = Regex("""register<\s*EventData\.(\w+)\s*>""")
            .findAll(eventsKt)
            .map { it.groupValues[1] }

        val haandtert = (fraHandlerklasser + fraInlineHandlere).toSet()
        val mangler = EventData::class.sealedSubclasses.map { it.simpleName!! }.toSet() - haandtert

        assertEquals(
            emptySet(),
            mangler,
            "Events uten registrert handler. Registrer en handler i configureEventHandlers, eventuelt en noop-handler.",
        )
    }

    private fun eventTypeFor(handler: KClass<*>): String =
        (handler.allSupertypes.first { it.classifier == EventHandler::class }
            .arguments.single().type!!.classifier as KClass<*>).simpleName!!
}
