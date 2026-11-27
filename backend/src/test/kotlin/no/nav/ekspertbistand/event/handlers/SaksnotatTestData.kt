package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.SaksnotatTable
import no.nav.ekspertbistand.sak.LagretSak
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
internal fun saksnotatOpprettet(
    sak: LagretSak,
    notatId: UUID,
    ident: String,
    rolle: AktorRolle,
    tekst: String,
    tidspunkt: Instant,
) = EventData.SaksnotatOpprettet(
    notatId = notatId.toString(),
    sakId = sak.sakId.toString(),
    soknadId = sak.soknadId.toString(),
    tekst = tekst,
    skrevetAvRolle = rolle,
    skrevetAvIdent = ident,
    skrevetAvNavn = "Navn $ident",
    tidspunkt = tidspunkt,
)

internal fun hentSaksnotat(database: Database, notatId: UUID): ResultRow? = transaction(database) {
    SaksnotatTable.selectAll().where { SaksnotatTable.notatId eq notatId }.singleOrNull()
}
