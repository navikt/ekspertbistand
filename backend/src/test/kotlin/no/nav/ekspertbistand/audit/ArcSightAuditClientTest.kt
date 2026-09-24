package no.nav.ekspertbistand.audit

import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.common.audit_log.log.AuditLogger
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArcSightAuditClientTest {

    private class RecordingAuditLogger : AuditLogger {
        val meldinger = mutableListOf<CefMessage>()
        override fun log(message: CefMessage) {
            meldinger.add(message)
        }

        override fun log(message: String) = error("ikke i bruk")
    }

    private val navIdent = "Z994488"
    private val fnr = "22420094160"

    @Test
    fun `bygger PERMIT-melding med riktige felter`() {
        val client = ArcSightAuditClient(applicationName = "ekspertbistand")

        val cef = client.byggMelding(
            navIdent = navIdent,
            fnr = fnr,
            tillatt = true,
            melding = "NAV-ansatt har gjort oppslag på bruker",
            event = CefMessageEvent.ACCESS,
        ).toString()

        assertTrue(cef.startsWith("CEF:0|ekspertbistand|"), cef)
        assertTrue(cef.contains("audit:access"), cef)
        assertTrue(cef.contains("suid=$navIdent"), cef)
        assertTrue(cef.contains("duid=$fnr"), cef)
        assertTrue(cef.contains("flexString1Label=Decision"), cef)
        assertTrue(cef.contains("flexString1=Permit"), cef)
    }

    @Test
    fun `DENY gir avslagsbeslutning`() {
        val client = ArcSightAuditClient()

        val cef = client.byggMelding(
            navIdent = navIdent,
            fnr = fnr,
            tillatt = false,
            melding = "Saksbehandler fikk ikke tilgang",
        ).toString()

        assertTrue(cef.contains("flexString1=Deny"), cef)
    }

    @Test
    fun `loggOppslag sender meldingen til AuditLogger`() {
        val recorder = RecordingAuditLogger()
        val client = ArcSightAuditClient(auditLogger = recorder)

        client.loggOppslag(
            navIdent = navIdent,
            fnr = fnr,
            tillatt = true,
            melding = "oppslag",
        )

        assertEquals(1, recorder.meldinger.size)
    }
}
