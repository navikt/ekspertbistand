package no.nav.ekspertbistand.oebs.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/**
 * Fagsystembokstav for ekspertbistand i bestillingsnummer-serien.
 *
 * Som kode-konstant, ikke ekstern config (jf. spec-beslutning 5): teamet deployer hyppig, så vi
 * sparer ingenting på å legge verdien i ekstern config — config er reservert for plattform-gitte
 * verdier og hemmeligheter.
 *
 * `E` er midlertidig og må avklares/bekreftes mot OeBS før produksjon (se spec, gjenstående punkt).
 */
const val FAGSYSTEM_KILDE: String = "E"

/**
 * Løpenummer per sak. Én rad per sak holder neste ledige løpenummer; oppdatering skjer i samme
 * transaksjon som outbox-skrivingen slik at et bestillingsnummer aldri deles ut to ganger.
 */
object OebsLopenummer : Table("oebs_lopenummer") {
    val sakId = text("sak_id")
    val nesteLopenr = integer("neste_lopenr").default(1)
    @OptIn(kotlin.time.ExperimentalTime::class)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(sakId)
}

/**
 * 🔴 RØD SONE — skriv selv.
 *
 * Genererer neste unike bestillingsnummer for en sak, på formatet `<FAGSYSTEM_KILDE><sak><løpenr>`.
 * Må være transaksjonssikker: les-og-inkrementer [OebsLopenummer] under radlås i kallerens
 * transaksjon (samme transaksjon som [leggIOutbox]) slik at samtidige kall ikke deler ut samme
 * nummer. Nummerserie-logikk er økonomikritisk og skal implementeres og forstås av teamet.
 */
@Suppress("unused")
fun JdbcTransaction.nesteBestillingsnummer(sakId: String): String =
    TODO("Rød sone: implementer transaksjonssikker løpenummer-generering (les+inkrementer OebsLopenummer under radlås).")
