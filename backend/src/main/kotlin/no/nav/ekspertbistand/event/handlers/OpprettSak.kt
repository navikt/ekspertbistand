package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.saksbehandling.*
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val opprettSakSubtask = "sak_opprettet"

/**
 * Oppretter sak for en journalført søknad.
 *
 * Behandlende enhet hentes fra [EventData.InnsendtSoknadJournalfoert] og mappes fra Arena- til
 * Norg-enhetsnummer før saken opprettes med alle vilkår. I samme transaksjon publiseres
 * [EventData.SakOppdatert] for saksloggen. Idempotent: saken opprettes én gang per søknad, og
 * eventen publiseres én gang per [EventData.InnsendtSoknadJournalfoert].
 */
@OptIn(ExperimentalTime::class)
class OpprettSak(
    private val database: Database,
) : EventHandler<EventData.InnsendtSoknadJournalfoert> {
    override val id: String = "Opprett sak"
    override val eventType: KClass<EventData.InnsendtSoknadJournalfoert> = EventData.InnsendtSoknadJournalfoert::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val logger = logger()
    private val clock = Clock.System

    override suspend fun handle(event: Event<EventData.InnsendtSoknadJournalfoert>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, opprettSakSubtask)) {
            return success()
        }

        val soknadId = event.data.soknad.id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return unrecoverableError("Søknad mangler gyldig id")
        val behandlendeEnhet = event.data.behandlendeEnhetId

        val tidspunkt = clock.now()
        val sakId = transaction(database) {
            val sakId = opprettSakForSoknad(soknadId, behandlendeEnhet, tidspunkt) ?: return@transaction null
            publishEventQueue(
                EventData.SakOppdatert(
                    sakId = sakId.toString(),
                    soknadId = soknadId.toString(),
                    utfortAvRolle = AktorRolle.SYSTEM,
                    utfortAvIdent = null,
                    notat = "Sak opprettet",
                    tidspunkt = tidspunkt,
                )
            )
            idempotencyGuard.guard(event, opprettSakSubtask)
            sakId
        } ?: return unrecoverableError("Søknad $soknadId finnes ikke, kan ikke opprette sak")

        logger.info("Opprettet sak {} for søknad {}", sakId, soknadId)
        return success()
    }


    companion object {
        /**
         * Oppretter alle vilkår for saken som ikke vurdert. Idempotent: eksisterende rader beholdes.
         * Må kalles i en pågående transaksjon.
         */
        fun opprettVilkarForSak(sakId: UUID) {
            SaksvilkarTable.batchInsert(Vilkar.entries, ignore = true, shouldReturnGeneratedValues = false) { vilkar ->
                this[SaksvilkarTable.sakId] = sakId
                this[SaksvilkarTable.vilkarId] = vilkar.name
            }
        }

        /**
         * Oppretter sak for søknaden (`OPPRETTET`, kilde `ARENA`) med [behandlendeEnhet] og alle vilkår,
         * og returnerer `sakId`. Idempotent på `soknad_id`: finnes saken allerede (for eksempel opprettet av
         * [no.nav.ekspertbistand.event.projections.SakProjection]), settes behandlende enhet bare hvis den mangler.
         * Returnerer null hvis søknaden ikke finnes. Må kalles i en pågående transaksjon.
         */
        fun opprettSakForSoknad(soknadId: UUID, behandlendeEnhet: String, tidspunkt: Instant): UUID? {
            val soknadFinnes = !SoknadTable.select(SoknadTable.id).where { SoknadTable.id eq soknadId }.empty()
            if (!soknadFinnes) return null

            SakTable.insertIgnore {
                it[SakTable.soknadId] = soknadId
                it[status] = Saksstatus.OPPRETTET.name
                it[kildeTilBehandling] = KildeTilBehandling.EKSPERTBISTAND.name
                it[SakTable.behandlendeEnhet] = behandlendeEnhet
                it[opprettet] = tidspunkt
                it[sistEndret] = tidspunkt
            }
            SakTable.update({ (SakTable.soknadId eq soknadId) and SakTable.behandlendeEnhet.isNull() }) {
                it[SakTable.behandlendeEnhet] = behandlendeEnhet
                it[sistEndret] = tidspunkt
            }

            val sakId = SakTable.select(SakTable.sakId).where { SakTable.soknadId eq soknadId }.single()[SakTable.sakId]
            opprettVilkarForSak(sakId)
            return sakId
        }
    }
}
