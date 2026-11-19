@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.ExperimentalTime

/**
 * Hvilken sak og søknad en bestilling hører til. Skrives av
 * [no.nav.ekspertbistand.oebs.OebsKlient.opprettBestilling] i samme transaksjon som outbox-raden, og
 * leses av [no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer] når VALP melder status.
 *
 * Gir `oebs/` koblingen til saken uten å avhenge av `saksbehandling/`.
 */
object OebsBestillingReferanse : Table("oebs_bestilling_referanse") {
    val bestillingsnummer = text("bestillingsnummer")
    val sakId = javaUUID("sak_id")
    val soknadId = javaUUID("soknad_id")
    val opprettet = timestamp("opprettet").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(bestillingsnummer)
}

data class BestillingReferanse(val sakId: UUID, val soknadId: UUID)

fun JdbcTransaction.lagreBestillingReferanse(bestillingsnummer: String, sakId: UUID, soknadId: UUID) {
    OebsBestillingReferanse.insert {
        it[OebsBestillingReferanse.bestillingsnummer] = bestillingsnummer
        it[OebsBestillingReferanse.sakId] = sakId
        it[OebsBestillingReferanse.soknadId] = soknadId
    }
}

fun JdbcTransaction.hentBestillingReferanse(bestillingsnummer: String): BestillingReferanse? =
    OebsBestillingReferanse.selectAll()
        .where { OebsBestillingReferanse.bestillingsnummer eq bestillingsnummer }
        .singleOrNull()
        ?.let { BestillingReferanse(it[OebsBestillingReferanse.sakId], it[OebsBestillingReferanse.soknadId]) }
