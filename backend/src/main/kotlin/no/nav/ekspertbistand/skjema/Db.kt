package no.nav.ekspertbistand.skjema

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object SkjemaTable : Table("skjema") {
    val id = uuid("id")
    val tittel = text("tittel")
    val organisasjonsnummer = text("organisasjonsnummer").index()
    val beskrivelse = text("beskrivelse")
    val opprettetAv = text("opprettet_av")

    @OptIn(ExperimentalTime::class)
    val opprettetTidspunkt = text("opprettet_tidspunkt").clientDefault {
        Clock.System.now().toString()
    }

    override val primaryKey = PrimaryKey(id)
}

object UtkastTable : UUIDTable("utkast") {
    val tittel = text("tittel").nullable()
    val organisasjonsnummer = text("organisasjonsnummer").nullable().index()
    val beskrivelse = text("beskrivelse").nullable()
    val opprettetAv = text("opprettet_av").index()

    @OptIn(ExperimentalTime::class)
    val opprettetTidspunkt = text("opprettet_tidspunkt").clientDefault {
        Clock.System.now().toString()
    }
}

suspend fun findSkjemaOrUtkastById(id: UUID): DTO? =
    SkjemaTable.selectAll()
        .where { SkjemaTable.id eq id }
        .singleOrNull()?.tilSkjemaDTO()
        ?: // If not found, try UtkastTable
        UtkastTable.selectAll()
            .where { UtkastTable.id eq id }
            .singleOrNull()?.tilUtkastDTO()

fun ResultRow.tilSkjemaDTO() = DTO.Skjema(
    id = this[SkjemaTable.id].toString(),
    tittel = this[SkjemaTable.tittel],
    organisasjonsnummer = this[SkjemaTable.organisasjonsnummer],
    beskrivelse = this[SkjemaTable.beskrivelse],
    opprettetAv = this[SkjemaTable.opprettetAv],
    opprettetTidspunkt = this[SkjemaTable.opprettetTidspunkt]
)

fun ResultRow.tilUtkastDTO() = DTO.Utkast(
    id = this[UtkastTable.id].value.toString(),
    tittel = this[UtkastTable.tittel],
    organisasjonsnummer = this[UtkastTable.organisasjonsnummer],
    beskrivelse = this[UtkastTable.beskrivelse],
    opprettetAv = this[UtkastTable.opprettetAv],
    opprettetTidspunkt = this[UtkastTable.opprettetTidspunkt]
)