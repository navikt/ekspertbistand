package no.nav.ekspertbistand.audit

import no.nav.common.audit_log.cef.AuthorizationDecision
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.common.audit_log.log.AuditLogger
import no.nav.common.audit_log.log.AuditLoggerImpl

const val AUDIT_APPLICATION_NAME = "ekspertbistand"

class ArcSightAuditClient(
    private val auditLogger: AuditLogger = AuditLoggerImpl(),
    private val applicationName: String = AUDIT_APPLICATION_NAME,
) {
    /**
     * Bygger og logger en sporingsmelding for et oppslag.
     *
     * @param navIdent  saksbehandlerens NAV-ident (suid)
     * @param fnr       fødselsnummer det gjøres oppslag på (duid)
     * @param tillatt   true → PERMIT, false → DENY
     * @param melding   menneskelesbar beskrivelse av oppslaget (msg)
     * @param event     CEF-hendelsestype, standard ACCESS
     */
    fun loggOppslag(
        navIdent: String,
        fnr: String,
        tillatt: Boolean,
        melding: String,
        event: CefMessageEvent = CefMessageEvent.ACCESS,
    ) {
        auditLogger.log(byggMelding(navIdent, fnr, tillatt, melding, event))
    }

    internal fun byggMelding(
        navIdent: String,
        fnr: String,
        tillatt: Boolean,
        melding: String,
        event: CefMessageEvent = CefMessageEvent.ACCESS,
    ): CefMessage =
        CefMessage.builder()
            .applicationName(applicationName)
            .event(event)
            .name("Sporingslogg")
            .authorizationDecision(
                if (tillatt) AuthorizationDecision.PERMIT else AuthorizationDecision.DENY,
            )
            .sourceUserId(navIdent)
            .destinationUserId(fnr)
            .timeEnded(System.currentTimeMillis())
            .extension("msg", melding)
            .build()
}
