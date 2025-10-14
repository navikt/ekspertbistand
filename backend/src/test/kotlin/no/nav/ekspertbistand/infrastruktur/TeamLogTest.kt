package no.nav.ekspertbistand.infrastruktur

import org.slf4j.MDC
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamLogTest {

    /**
     * Dersom denne testen feiler så er [LogConfig] blitt feilkonfigurert slik at
     * sensitive data kan lekke i loggene.
     */
    @Test
    fun `vanlig logg skal ikke inneholde verdier i teamLogCtx`() {
        val log = logger()
        val ctx = mapOf("secret" to "shhh")
        val stdout = captureStdout {
            log.info("FOO", *TeamLogCtx.of(ctx))
        }

        assertTrue(stdout.contains("FOO"))
        for (arg in ctx) {
            assertFalse(stdout.contains(arg.key), "StructuredArgument skal være slått av i vanlig log. se LogConfig")
            assertFalse(stdout.contains(arg.value), "StructuredArgument skal være slått av i vanlig log. se LogConfig")
        }
    }

    @Test
    fun `logging av placeholders, mdc og exceptions fungerer som normalt`() {
        val log = logger()
        val mdcCtx = mapOf("secret" to "shhh")
        val ex = RuntimeException("oh noes, more lemmings")
        val stdout = captureStdout {
            MDC.setContextMap(mdcCtx)
            log.warn("FOO {} {}", "bar", "baz", ex)
            MDC.clear()
        }

        assertTrue(stdout.contains("FOO"))
        assertTrue(stdout.contains("bar"))
        assertTrue(stdout.contains("baz"))
        assertTrue(stdout.contains(ex.message!!))
        for (arg in mdcCtx) {
            assertTrue(stdout.contains(arg.key))
            assertTrue(stdout.contains(arg.value))
        }
    }

}

private fun captureStdout(block: () -> Unit): String {
    val originalOut = System.out
    val captured = try {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val printStream = PrintStream(byteArrayOutputStream, true)
        System.setOut(printStream)
        block()
        byteArrayOutputStream.toString()
    } finally {
        System.setOut(originalOut)
    }
    print(captured)
    return captured
}