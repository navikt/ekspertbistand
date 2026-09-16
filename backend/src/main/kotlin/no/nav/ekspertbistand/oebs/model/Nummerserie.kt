package no.nav.ekspertbistand.oebs.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

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
 * OeBS-mottaket har en teknisk grense på **maks 20 tegn** for bestillingsnummeret. Generatoren
 * håndhever grensen så vi feiler tidlig og tydelig i stedet for å få nummeret avvist av OeBS.
 */
const val BESTILLINGSNUMMER_MAKS_LENGDE: Int = 20

/**
 * OeBS-mottaket har en teknisk grense på **maks 50 tegn** for fakturanummeret. Generatoren
 * håndhever grensen så vi feiler tidlig og tydelig i stedet for å få nummeret avvist av OeBS.
 */
const val FAKTURANUMMER_MAKS_LENGDE: Int = 50

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
 * Løpenummer per bestilling. Én rad per bestillingsnummer holder neste ledige faktura-løpenummer;
 * oppdatering skjer i samme transaksjon som outbox-skrivingen slik at et fakturanummer aldri deles
 * ut to ganger.
 */
object OebsFakturaLopenummer : Table("oebs_faktura_lopenummer") {
    val bestillingsnummer = text("bestillingsnummer")
    val nesteLopenr = integer("neste_lopenr").default(1)
    @OptIn(kotlin.time.ExperimentalTime::class)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(bestillingsnummer)
}

/**
 * Genererer neste unike bestillingsnummer for en sak, på OeBS-formatet
 * `<FAGSYSTEM_KILDE>-<sakId>-<løpenr>` (f.eks. `E-2026/10000-1`).
 *
 * **Transaksjonssikkerhet (økonomikritisk).** Funksjonen kjører i kallerens [JdbcTransaction] —
 * samme transaksjon som [leggIOutbox] — slik at nummeret og outbox-raden committes atomisk.
 *
 * 1. [insertIgnore] oppretter løpenummer-raden for saken hvis den mangler (idempotent og trygt ved
 *    samtidige første-bestillinger, siden `sak_id` er primærnøkkel).
 * 2. `SELECT … FOR UPDATE` ([ForUpdateOption.ForUpdate]) tar radlås på saken. To samtidige kall for
 *    **samme** sak serialiseres: det andre venter til det første har committet, og leser da det
 *    inkrementerte nummeret. Dermed deles aldri samme løpenummer ut to ganger. (Samme mønster som
 *    [no.nav.ekspertbistand.event.EventQueue], men plain `FOR UPDATE` — vi vil vente, ikke hoppe over.)
 * 3. Vi reserverer nummeret ved å flytte «neste ledige løpenr» ett hakk fram i samme transaksjon.
 *
 * Til slutt håndheves OeBS sin lengdegrense ([BESTILLINGSNUMMER_MAKS_LENGDE]) så et for langt
 * nummer feiler her, ikke først ved avvisning fra OeBS.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
fun JdbcTransaction.nesteBestillingsnummer(sakId: String): String {
    OebsLopenummer.insertIgnore {
        it[OebsLopenummer.sakId] = sakId
        it[nesteLopenr] = 1
    }

    val lopenr = OebsLopenummer
        .selectAll()
        .where { OebsLopenummer.sakId eq sakId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .single()[OebsLopenummer.nesteLopenr]

    OebsLopenummer.update({ OebsLopenummer.sakId eq sakId }) {
        it[nesteLopenr] = lopenr + 1
        it[updatedAt] = CurrentTimestamp
    }

    val bestillingsnummer = "$FAGSYSTEM_KILDE-$sakId-$lopenr"
    require(bestillingsnummer.length <= BESTILLINGSNUMMER_MAKS_LENGDE) {
        "Bestillingsnummer '$bestillingsnummer' er ${bestillingsnummer.length} tegn, " +
            "over OeBS-grensen på $BESTILLINGSNUMMER_MAKS_LENGDE."
    }
    return bestillingsnummer
}

/**
 * Genererer neste unike fakturanummer for en bestilling, på OeBS-formatet
 * `<bestillingsnummer>-<løpenr>` (f.eks. `E-2026/10000-1-1`). Fakturanummeret korrelerer til
 * bestillingen ved at bestillingsnummeret er et prefiks (jf. spec §4) — én bestilling kan ha flere
 * fakturaer (forventet 1:1, men format støtter 1:N).
 *
 * Transaksjonssikkerheten er identisk med [nesteBestillingsnummer], men telleren er skopet til
 * **bestillingsnummeret** i stedet for saken: [insertIgnore] oppretter raden, `SELECT … FOR UPDATE`
 * serialiserer samtidige fakturaer på samme bestilling, og løpenummeret reserveres i samme
 * transaksjon. Til slutt håndheves OeBS sin lengdegrense ([FAKTURANUMMER_MAKS_LENGDE]).
 */
@OptIn(kotlin.time.ExperimentalTime::class)
fun JdbcTransaction.nesteFakturanummer(bestillingsnummer: String): String {
    OebsFakturaLopenummer.insertIgnore {
        it[OebsFakturaLopenummer.bestillingsnummer] = bestillingsnummer
        it[nesteLopenr] = 1
    }

    val lopenr = OebsFakturaLopenummer
        .selectAll()
        .where { OebsFakturaLopenummer.bestillingsnummer eq bestillingsnummer }
        .forUpdate(ForUpdateOption.ForUpdate)
        .single()[OebsFakturaLopenummer.nesteLopenr]

    OebsFakturaLopenummer.update({ OebsFakturaLopenummer.bestillingsnummer eq bestillingsnummer }) {
        it[nesteLopenr] = lopenr + 1
        it[updatedAt] = CurrentTimestamp
    }

    val fakturanummer = "$bestillingsnummer-$lopenr"
    require(fakturanummer.length <= FAKTURANUMMER_MAKS_LENGDE) {
        "Fakturanummer '$fakturanummer' er ${fakturanummer.length} tegn, " +
            "over OeBS-grensen på $FAKTURANUMMER_MAKS_LENGDE."
    }
    return fakturanummer
}
