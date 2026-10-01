package no.nav.ekspertbistand.infrastruktur

import no.nav.common.audit_log.log.AuditLoggerConstants.AUDIT_LOGGER_NAME
import org.junit.jupiter.api.assertDoesNotThrow
import org.slf4j.LoggerFactory
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
    fun `vanlig logg skal ikke inneholde verdier i teamLog`() {
        val log = logger()
        val teamLog = teamLogger()
        val stdout = captureStdout {
            log.info("FOO")
            teamLog.info("BAR")
        }

        assertTrue(stdout.contains("FOO"), "forventet å finne vanlig logg-melding")
        assertFalse(stdout.contains("BAR"), "forventet å ikke finne team logg-melding")
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

    @Test
    fun `logging av object som arg fungerer`() {
        val teamLog = teamLogger()

        assertDoesNotThrow {
            teamLog.error("tester logging av object {}", mapOf("key" to "value"))
        }
    }

    /**
     * Sporingslogg inneholder fødselsnummer (duid) og skal kun til ArcSight, aldri til stdout.
     */
    @Test
    fun `sporingslogg havner ikke i stdout`() {
        val stdout = captureStdout {
            LoggerFactory.getLogger(AUDIT_LOGGER_NAME).info("CEF:0|ekspertbistand|AuditLogger|1.0|audit:access|Sporingslogg|INFO|duid=AUDITFNR")
        }

        assertFalse(stdout.contains("AUDITFNR"), "forventet at sporingslogg ikke skrives til stdout")
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