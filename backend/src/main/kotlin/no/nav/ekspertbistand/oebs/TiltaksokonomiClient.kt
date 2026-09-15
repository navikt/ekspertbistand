package no.nav.ekspertbistand.oebs

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/**
 * Internt API for å bestille, fakturere/utbetale, annullere og gjøre opp mot OeBS/tiltaksøkonomi.
 *
 * Alle operasjoner skriver til [OebsOutbox] i kallerens transaksjon (metodene er
 * [JdbcTransaction]-extensions), slik at bestillingen commiter atomisk med kallerens
 * forretningsendring. Selve publiseringen til Kafka gjøres asynkront av outbox-polleren.
 *
 * Domene-triggere (hva som utløser en bestilling) og saksbehandling er utenfor scope — dette er
 * kun klienten (jf. spec-avgrensning).
 */
interface TiltaksokonomiClient {

    /**
     * Genererer et unikt bestillingsnummer for [sakId], lar [bygg] fylle resten av bestillingen med
     * nummeret, legger meldingen i outbox-en og returnerer bestillingsnummeret.
     */
    fun JdbcTransaction.opprettBestilling(sakId: String, bygg: (bestillingsnummer: String) -> OpprettBestilling): String

    /** Fakturerer/utbetaler mot en eksisterende bestilling. */
    fun JdbcTransaction.opprettFaktura(faktura: OpprettFaktura)

    /** Annullerer en eksisterende bestilling. */
    fun JdbcTransaction.annullerBestilling(annullering: AnnullerBestilling)

    /** Gjør opp (avslutter) en bestilling. */
    fun JdbcTransaction.gjorOppBestilling(gjorOpp: GjorOppBestilling)
}

class TiltaksokonomiClientImpl : TiltaksokonomiClient {

    override fun JdbcTransaction.opprettBestilling(
        sakId: String,
        bygg: (bestillingsnummer: String) -> OpprettBestilling,
    ): String {
        // 🔴 Rød sone: nummerserien er ikke implementert ennå (se Nummerserie.kt).
        val bestillingsnummer = nesteBestillingsnummer(sakId)
        leggIOutbox(OkonomiBestillingMelding.Bestilling(bygg(bestillingsnummer)))
        return bestillingsnummer
    }

    override fun JdbcTransaction.opprettFaktura(faktura: OpprettFaktura) {
        leggIOutbox(OkonomiBestillingMelding.Faktura(faktura))
    }

    override fun JdbcTransaction.annullerBestilling(annullering: AnnullerBestilling) {
        leggIOutbox(OkonomiBestillingMelding.Annullering(annullering))
    }

    override fun JdbcTransaction.gjorOppBestilling(gjorOpp: GjorOppBestilling) {
        leggIOutbox(OkonomiBestillingMelding.GjorOppBestilling(gjorOpp))
    }
}
