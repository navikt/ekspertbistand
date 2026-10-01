package no.nav.ekspertbistand.event.projections

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.saksbehandling.KildeTilBehandling
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Backfiller `sak`-tabellen fra event-loggen for søknader som behandles i Arena.
 * Saker kan også opprettes utenfor projeksjonen. Se `specifications/sak_projection.md`.
 *
 * - [EventData.SoknadInnsendt] oppretter saken (`OPPRETTET`, kilde `ARENA`), men kun hvis søknaden
 *   fortsatt finnes og det ikke allerede finnes en sak for søknaden. Ved replay kan søknaden være
 *   slettet, og da hoppes eventen over.
 * - [EventData.InnsendtSoknadJournalfoert] setter behandlende enhet. Eventen inneholder enhetsnummeret
 *   som ble sendt til Arena, så det mappes tilbake til Norg-enhetsnummeret.
 * - [EventData.TiltaksgjennomforingOpprettet] setter Arena-saksnummer.
 * - [EventData.SaksbehandlingStartetIArena] gir `UNDER_BEHANDLING`, men kun fra `OPPRETTET`.
 * - [EventData.TilskuddsbrevMottatt] gir `INNVILGET` og [EventData.SoknadAvlystIArena] gir `AVSLATT`.
 *   Terminalstatus overskrives aldri.
 *
 * Re-kjøring: bump versjonen i [name]. Da starter projeksjonen på nytt fra posisjon 0.
 * Projeksjonen er idempotent: eksisterende saker opprettes ikke på nytt, men feltene oppdateres.
 */
@OptIn(ExperimentalTime::class)
class SakProjection(
    database: Database,
) : EventLogProjectionBuilder(database) {
    // v2: revers-mapping av behandlende enhet fra Arena- til Norg-enhetsnummer
    override val name = "Sak-v2"

    override fun handle(event: Event<out EventData>, eventTimestamp: Instant) {
        when (val data = event.data) {
            is EventData.SoknadInnsendt -> opprettSak(data.soknad.uuid(), eventTimestamp)

            is EventData.InnsendtSoknadJournalfoert ->
                oppdaterSak(data.soknad, eventTimestamp) {
                    it[SakTable.behandlendeEnhet] = BehandlendeEnhetService.arenaTilNorgEnhetNr(data.behandlendeEnhetId)
                }

            is EventData.TiltaksgjennomforingOpprettet ->
                oppdaterSak(data.soknad, eventTimestamp) {
                    it[SakTable.arenaSakId] = data.saksnummer
                }

            is EventData.SaksbehandlingStartetIArena ->
                oppdaterSak(data.soknad, eventTimestamp, { SakTable.status eq Saksstatus.OPPRETTET.name }) {
                    it[SakTable.status] = Saksstatus.UNDER_BEHANDLING.name
                }

            is EventData.TilskuddsbrevMottatt ->
                oppdaterSak(data.soknad, eventTimestamp, notFinalized) {
                    it[SakTable.status] = Saksstatus.INNVILGET.name
                }

            is EventData.SoknadAvlystIArena ->
                oppdaterSak(data.soknad, eventTimestamp, notFinalized) {
                    it[SakTable.status] = Saksstatus.AVSLATT.name
                }

            else -> Unit
        }
    }

    private fun opprettSak(soknadId: UUID, eventTimestamp: Instant) {
        val soknadFinnes = !SoknadTable.selectAll().where { SoknadTable.id eq soknadId }.empty()
        if (!soknadFinnes) {
            log.info("Søknad {} finnes ikke lenger, oppretter ikke sak", soknadId)
            return
        }

        val sakFinnes = !SakTable.selectAll().where { SakTable.soknadId eq soknadId }.empty()
        if (sakFinnes) {
            log.info("Sak for søknad {} finnes allerede, oppretter ikke ny", soknadId)
            return
        }

        // insertIgnore i tillegg til sjekken over, i tilfelle saken opprettes samtidig et annet sted.
        SakTable.insertIgnore {
            it[SakTable.soknadId] = soknadId
            it[status] = Saksstatus.OPPRETTET.name
            it[kildeTilBehandling] = KildeTilBehandling.ARENA.name
            it[opprettet] = eventTimestamp
            it[sistEndret] = eventTimestamp
        }
    }

    private fun oppdaterSak(
        soknad: DTO.Soknad,
        eventTimestamp: Instant,
        vilkaar: () -> Op<Boolean> = { Op.TRUE },
        body: SakTable.(UpdateStatement) -> Unit,
    ) {
        SakTable.update(
            where = {
                (SakTable.soknadId eq soknad.uuid()) and
                        (SakTable.kildeTilBehandling eq KildeTilBehandling.ARENA.name) and
                        vilkaar()
            }
        ) {
            body(it)
            it[sistEndret] = eventTimestamp
        }
    }

    private companion object {
        val finalizedStatus = listOf(Saksstatus.INNVILGET, Saksstatus.AVSLATT).map { it.name }
        val notFinalized: () -> Op<Boolean> = { SakTable.status notInList finalizedStatus }

        fun DTO.Soknad.uuid(): UUID = UUID.fromString(requireNotNull(id) { "Søknad mangler id" })
    }
}
