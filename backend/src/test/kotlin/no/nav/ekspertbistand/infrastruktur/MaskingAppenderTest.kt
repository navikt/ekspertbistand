package no.nav.ekspertbistand.infrastruktur

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.ekspertbistand.infrastruktur.MaskingAppender.Companion.mask
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaskingAppenderTest {
    @Test
    fun `Masking fødselsnummer`() {
        // works for 11 digits
        assertFalse(mask("At nummer 11223344556")!!.contains("11223344556"))

        // works 12 numbers
        assertTrue(mask("At nummer 112233445566")!!.contains("112233445566"))

        // works fnr leading
        assertFalse(mask("At nummer x11223344556")!!.contains("11223344556"))

        // works fnr followed by char
        assertFalse(mask("At nummer 11223344556x")!!.contains("11223344556"))

        // works fnr:
        assertFalse(mask("fnr:11223344556")!!.contains("11223344556"))

        // works fnr=
        assertFalse(mask("fnr=11223344556")!!.contains("11223344556"))
        assertEquals("fnr=***********", mask("fnr=11223344556"))
        assertEquals("fnr=***********lolwat", mask("fnr=11223344556lolwat"))

        // works epost=
        assertFalse(mask("wat=navn@domene.no")!!.contains("navn@domene.no"))
        assertFalse(mask("wat=navn12@domene.no")!!.contains("navn12@domene.no"))
        assertEquals("wat=********&noeannet", mask("wat=navn12@domene.no&noeannet"))

        // works altinn error message
        assertEquals(
            "The ReceiverAddress/User profile must contain a valid emailaddress. Address: ********, User: *********",
            mask("The ReceiverAddress/User profile must contain a valid emailaddress. Address: julenissen@nordpoolen.no, User: 123123123")
        )
    }

    @Test
    fun `Maskerer melding i hele unntakskjeden`() {
        val undertrykt = IllegalStateException("undertrykt fnr=11223344556")
        val aarsak = RuntimeException("årsak fnr=11223344556")
        val feil = IllegalArgumentException("topp fnr=11223344556", aarsak).apply { addSuppressed(undertrykt) }

        val maskert = loggOgFang { error("feilet", feil) }.throwableProxy!!

        assertEquals("topp fnr=***********", maskert.message)
        assertEquals("årsak fnr=***********", maskert.cause!!.message)
        assertEquals("undertrykt fnr=***********", maskert.suppressed.single().message)
        assertEquals(RuntimeException::class.qualifiedName, maskert.cause!!.className)
    }

    @Test
    fun `Maskerer MDC-verdier, men ikke korrelasjons-ID`() {
        val korrelasjonsId = "0a1b2c3d-0000-4000-8000-a12345678901"
        MDC.put("path", "/api/person/11223344556/virksomhet/123456789")
        MDC.put("x_correlation_id", korrelasjonsId)
        val mdc = try {
            loggOgFang { info("hei") }.mdcPropertyMap
        } finally {
            MDC.clear()
        }

        assertEquals("/api/person/***********/virksomhet/*********", mdc["path"])
        assertEquals(korrelasjonsId, mdc["x_correlation_id"])
    }

    private fun loggOgFang(logg: Logger.() -> Unit): ILoggingEvent {
        // Bruker den globale konteksten slik at MDC fra org.slf4j.MDC blir med i hendelsen.
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val fanger = ListAppender<ILoggingEvent>().apply { this.context = context; start() }
        val masking = MaskingAppender().apply { this.context = context; appender = fanger; start() }
        val logger = context.getLogger("MaskingAppenderTest.${UUID.randomUUID()}").apply {
            isAdditive = false
            addAppender(masking)
        }
        try {
            logger.logg()
        } finally {
            logger.detachAppender(masking)
        }
        return fanger.list.single()
    }

    @Test
    fun `Masking password in connection strings`() {
        // works for jdbc:url
        mask(
            "jdbc:postgresql://127.0.0.1:5432/bruker-model?user=notifikasjon-bruker-api&password=foobar&socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=lol2%3Anorth-pole1%3Anotifikasjon-bruker-api"
        ).let {
            assertFalse(it!!.contains("foobar"))
            assertEquals(
                "jdbc:postgresql://127.0.0.1:5432/bruker-model?user=notifikasjon-bruker-api&password=********",
                it
            )
        }
    }
}
