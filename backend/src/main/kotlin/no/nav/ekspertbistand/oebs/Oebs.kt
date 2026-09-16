package no.nav.ekspertbistand.oebs

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.ekspertbistand.oebs.integration.AnnullerBestilling
import no.nav.ekspertbistand.oebs.integration.OebsBestillingMelding
import no.nav.ekspertbistand.oebs.integration.OpprettBestilling
import no.nav.ekspertbistand.oebs.integration.OpprettFaktura
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiProducer
import no.nav.ekspertbistand.oebs.model.OebsOutboxPoller
import no.nav.ekspertbistand.oebs.model.leggIOutbox
import no.nav.ekspertbistand.oebs.model.nesteBestillingsnummer
import no.nav.ekspertbistand.oebs.model.nesteFakturanummer
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import kotlin.coroutines.CoroutineContext
import no.nav.ekspertbistand.oebs.integration.GjorOppBestilling as GjorOppBestillingPayload

/**
 * Inngangen til OeBS-/tiltaksøkonomi-integrasjonen. Resten av appen forholder seg til to ting her:
 *
 * - [OebsKlient] — skrive-API-et (bestille / fakturere / annullere / gjøre opp).
 * - [startOebsProsessering] — starter bakgrunnsprosessene ([OebsProcessor]).
 *
 * Alt annet i [no.nav.ekspertbistand.oebs.integration] (Kafka + wire-kontrakter) og
 * [no.nav.ekspertbistand.oebs.model] (persistens) er implementasjon bak disse inngangene.
 */

/**
 * Skrive-API mot OeBS/tiltaksøkonomi. Alle operasjoner skriver til [OebsOutbox][no.nav.ekspertbistand.oebs.model.OebsOutbox]
 * i kallerens transaksjon (metodene er [JdbcTransaction]-extensions), slik at bestillingen commiter
 * atomisk med kallerens forretningsendring. Selve publiseringen til Kafka gjøres asynkront av
 * outbox-polleren.
 *
 * Konkret klasse uten interface: vi har ikke behov for å stubbe/mocke den. Domene-triggere (hva som
 * utløser en bestilling) og saksbehandling er utenfor scope (jf. spec-avgrensning).
 */
class OebsKlient {

    /**
     * Genererer et unikt bestillingsnummer for [sakId], lar [bygg] fylle resten av bestillingen med
     * nummeret, legger meldingen i outbox-en og returnerer bestillingsnummeret.
     */
    fun JdbcTransaction.opprettBestilling(sakId: String, bygg: (bestillingsnummer: String) -> OpprettBestilling): String {
        val bestillingsnummer = nesteBestillingsnummer(sakId)
        leggIOutbox(OebsBestillingMelding.Bestilling(bygg(bestillingsnummer)))
        return bestillingsnummer
    }

    /**
     * Genererer et unikt fakturanummer for [bestillingsnummer], lar [bygg] fylle resten av fakturaen
     * med nummeret, legger meldingen i outbox-en og returnerer fakturanummeret.
     */
    fun JdbcTransaction.opprettFaktura(bestillingsnummer: String, bygg: (fakturanummer: String) -> OpprettFaktura): String {
        val fakturanummer = nesteFakturanummer(bestillingsnummer)
        leggIOutbox(OebsBestillingMelding.Faktura(bygg(fakturanummer)))
        return fakturanummer
    }

    /** Annullerer en eksisterende bestilling. */
    fun JdbcTransaction.annullerBestilling(annullering: AnnullerBestilling) {
        leggIOutbox(OebsBestillingMelding.Annullering(annullering))
    }

    /** Gjør opp (avslutter) en bestilling. */
    fun JdbcTransaction.gjorOppBestilling(gjorOpp: GjorOppBestillingPayload) {
        leggIOutbox(OebsBestillingMelding.GjorOppBestilling(gjorOpp))
    }
}

/**
 * Eier oppstart av bakgrunnsprosessene i integrasjonen: outbox-polleren (utgående) og
 * status-consumeren (innkommende). Mønsteret speiler
 * [no.nav.ekspertbistand.arena.startKafkaConsumers]: én dedikert single-thread-dispatcher per
 * prosess.
 *
 * Rød-sone-logikken ([OebsOutboxPoller.startProcessing] og [TiltaksokonomiConsumer.tolkStatus]) er
 * nå implementert. Wiring inn i `main()` avventer likevel ekstern koordinering med Team VALP
 * (read-ACL på status-topicene + at ekspertbistand-kilden og enum-verdiene er lagt inn hos VALP).
 */
class OebsProcessor(
    private val database: Database,
    private val producer: TiltaksokonomiProducer = TiltaksokonomiProducer(),
) {
    private val outboxPoller = OebsOutboxPoller(database, producer)
    private val statusConsumer = TiltaksokonomiConsumer(database)

    fun start(parentContext: CoroutineContext) {
        // Outbox-poller (utgående).
        CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
            outboxPoller.startProcessing()
        }

        // Status-consument (innkommende).
        CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
            statusConsumer.startProcessing()
        }
    }
}

/**
 * Starter bakgrunnsprosessene for OeBS-integrasjonen.
 *
 * ⚠️ IKKE wiret inn i [no.nav.ekspertbistand.Application] sin `main()` ennå — se [OebsProcessor].
 * Rød-sone-logikken er skrevet; koble denne på i `main()` (ved siden av `startKafkaConsumers`) når
 * den eksterne koordineringen med Team VALP er på plass.
 */
fun Application.startOebsProsessering(parentContext: CoroutineContext) {
    // dependencies.resolve er suspend, så den må kalles i en coroutine (jf. arena.startKafkaConsumers).
    CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
        OebsProcessor(dependencies.resolve<Database>()).start(parentContext)
    }
}
