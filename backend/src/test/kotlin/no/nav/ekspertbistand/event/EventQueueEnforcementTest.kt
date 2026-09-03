package no.nav.ekspertbistand.event

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Håndhevingslag 2 fra spesifikasjonen: skriving til [QueuedEvents] skal kun skje via
 * publiseringsmetodene i EventQueue.kt. Denne testen skanner kildekoden og feiler dersom
 * en ny bypass (`QueuedEvents.insert` / `.insertReturning` / `.upsert`) sniker seg inn i
 * `src/main` — da fanges den i PR-en som innfører den, ikke i prod.
 */
class EventQueueEnforcementTest {

    private val forbudt = Regex("""QueuedEvents\s*\.\s*(insertReturning|insertIgnore|insert|upsert)\b""")

    @Test
    fun `ingen direkte skriving til QueuedEvents utenfor EventQueue`() {
        val mainKotlin = File("src/main/kotlin")
        assertTrue(mainKotlin.isDirectory, "Fant ikke src/main/kotlin fra ${File(".").absolutePath}")

        val brudd = mainKotlin.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "EventQueue.kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> forbudt.containsMatchIn(line) }
                    .map { (idx, line) -> "${file.path}:${idx + 1}: ${line.trim()}" }
            }
            .toList()

        if (brudd.isNotEmpty()) {
            fail(
                "Skriv aldri til QueuedEvents direkte — bruk publishEventQueue(ev) " +
                    "inne i en transaksjon. Fant:\n" + brudd.joinToString("\n")
            )
        }
    }
}
