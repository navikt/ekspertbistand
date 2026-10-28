package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.pdl.GraderingOppslag
import no.nav.ekspertbistand.saksbehandling.*
import no.nav.ekspertbistand.skjerming.SkjermingOppslag
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
 * Norg-enhetsnummer før saken opprettes med alle vilkår. Før transaksjonen slås deltakerens adressebeskyttelse
 * opp i PDL og skjerming i `skjermede-personer-pip`. Feiler et oppslag, gir handleren transient error, slik at
 * ingen sak opprettes uten flaggene. I samme transaksjon publiseres [EventData.SakOppdatert] for saksloggen og
 * [EventData.SakOpprettet]. Idempotent: saken opprettes én gang per søknad, og eventene publiseres én gang per
 * [EventData.InnsendtSoknadJournalfoert].
 */
@OptIn(ExperimentalTime::class)
class OpprettSak(
    private val database: Database,
    private val graderingOppslag: GraderingOppslag,
    private val skjermingOppslag: SkjermingOppslag,
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
        val fnr = event.data.soknad.ansatt.fnr

        val adressebeskyttelse = try {
            graderingOppslag.hentAdressebeskyttelse(fnr)
        } catch (e: Exception) {
            return transientError("Feil ved oppslag av adressebeskyttelse i PDL", e)
        }
        val skjermet = try {
            skjermingOppslag.erSkjermet(fnr)
        } catch (e: Exception) {
            return transientError("Feil ved oppslag av skjerming", e)
        }

        val tidspunkt = clock.now()
        val sakId = transaction(database) {
            val sakId = opprettSakForSoknad(
                soknadId = soknadId,
                behandlendeEnhet = behandlendeEnhet,
                kommunenummer = event.data.virksomhetKommunenummer,
                adressebeskyttelse = adressebeskyttelse,
                skjermet = skjermet,
                tidspunkt = tidspunkt,
            ) ?: return@transaction null
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
            publishEventQueue(
                EventData.SakOpprettet(
                    sakId = sakId.toString(),
                    soknad = event.data.soknad,
                    adressebeskyttelse = adressebeskyttelse,
                    skjermet = skjermet,
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
         * Oppretter sak for søknaden (`OPPRETTET`, kilde `EKSPERTBISTAND`) med [behandlendeEnhet], [kommunenummer],
         * [adressebeskyttelse], [skjermet] og alle vilkår, og returnerer `sakId`. Idempotent på `soknad_id`: finnes saken
         * allerede (for eksempel opprettet av [no.nav.ekspertbistand.event.projections.SakProjection]), settes enhet,
         * kommune og flagg bare hvis enheten mangler. Returnerer null hvis søknaden ikke finnes. Må kalles i en pågående
         * transaksjon.
         */
        fun opprettSakForSoknad(
            soknadId: UUID,
            behandlendeEnhet: String,
            kommunenummer: String?,
            adressebeskyttelse: Adressebeskyttelse?,
            skjermet: Boolean,
            tidspunkt: Instant,
        ): UUID? {
            val soknadFinnes = !SoknadTable.select(SoknadTable.id).where { SoknadTable.id eq soknadId }.empty()
            if (!soknadFinnes) return null

            SakTable.insertIgnore {
                it[SakTable.soknadId] = soknadId
                it[status] = Saksstatus.OPPRETTET.name
                it[kildeTilBehandling] = KildeTilBehandling.EKSPERTBISTAND.name
                it[SakTable.behandlendeEnhet] = behandlendeEnhet
                it[SakTable.kommunenummer] = kommunenummer
                it[SakTable.adressebeskyttelse] = adressebeskyttelse?.name
                it[SakTable.skjermet] = skjermet
                it[opprettet] = tidspunkt
                it[sistEndret] = tidspunkt
            }
            SakTable.update({ (SakTable.soknadId eq soknadId) and SakTable.behandlendeEnhet.isNull() }) {
                it[SakTable.behandlendeEnhet] = behandlendeEnhet
                it[SakTable.kommunenummer] = kommunenummer
                it[SakTable.adressebeskyttelse] = adressebeskyttelse?.name
                it[SakTable.skjermet] = skjermet
                it[sistEndret] = tidspunkt
            }

            val sakId = SakTable.select(SakTable.sakId).where { SakTable.soknadId eq soknadId }.single()[SakTable.sakId]
            opprettVilkarForSak(sakId)
            return sakId
        }
    }
}
