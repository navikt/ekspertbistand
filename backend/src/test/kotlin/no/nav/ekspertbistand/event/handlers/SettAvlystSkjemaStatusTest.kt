package no.nav.ekspertbistand.event.handlers

import io.ktor.server.testing.*
import no.nav.ekspertbistand.arena.EKSPERTBISTAND_TILTAKSKODE
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.skjema.SkjemaStatus
import no.nav.ekspertbistand.skjema.SkjemaTable
import no.nav.ekspertbistand.skjema.tilSkjemaDTO
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettAvlystSkjemaStatusTest {

    @Test
    fun `Søknad settes til avlyst`() = testApplication {
        val database = TestDatabase().cleanMigrate().config.jdbcDatabase
        val handler = SettAvlystSkjemaStatus(database = database)

        val skjema = transaction(database) {
            SkjemaTable.insertReturning {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "1337"
                it[virksomhetsnavn] = " foo bar AS"
                it[opprettetAv] = "42"
                it[behovForBistand] = "innsendt skjema"
                it[behovForBistandTilrettelegging] = ""
                it[behovForBistandBegrunnelse] = ""
                it[behovForBistandEstimertKostnad] = "42"
                it[behovForBistandTimer] = "9"
                it[behovForBistandStartdato] = CurrentDate
                it[kontaktpersonNavn] = ""
                it[kontaktpersonEpost] = ""
                it[kontaktpersonTelefon] = ""
                it[ansattFnr] = ""
                it[ansattNavn] = ""
                it[ekspertNavn] = ""
                it[ekspertVirksomhet] = ""
                it[ekspertKompetanse] = ""
                it[navKontaktPerson] = ""
                it[beliggenhetsadresse] = ""
                it[status] = SkjemaStatus.innsendt.toString()
            }.single().tilSkjemaDTO().also {
                assertEquals(SkjemaStatus.innsendt, it.status)
            }
        }

        val event = Event(
            id = 1L, data = EventData.SoknadAvlystIArena(
                skjema = skjema,
                tiltaksgjennomforingEndret = TiltaksgjennomforingEndret(
                    tiltaksgjennomfoeringId = 1,
                    tiltakKode = EKSPERTBISTAND_TILTAKSKODE,
                    tiltakStatusKode = TiltaksgjennomforingEndret.TiltakStatusKode.AVLYST,
                )
            )
        )
        assertIs<EventHandledResult.Success>(handler.handle(event))

        transaction(database) {
            SkjemaTable.selectAll().single().tilSkjemaDTO().let {
                assertEquals(SkjemaStatus.avlyst, it.status)
            }
        }
    }

    @Test
    fun `Søknad finnes ikke i databasen returnerer unrecoverable`() = testApplication {
        val database = TestDatabase().cleanMigrate().config.jdbcDatabase
        val handler = SettAvlystSkjemaStatus(database = database)

        val skjema = transaction(database) {
            SkjemaTable.insertReturning {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "1337"
                it[virksomhetsnavn] = " foo bar AS"
                it[opprettetAv] = "42"
                it[behovForBistand] = "innsendt skjema"
                it[behovForBistandTilrettelegging] = ""
                it[behovForBistandBegrunnelse] = ""
                it[behovForBistandEstimertKostnad] = "42"
                it[behovForBistandTimer] = "9"
                it[behovForBistandStartdato] = CurrentDate
                it[kontaktpersonNavn] = ""
                it[kontaktpersonEpost] = ""
                it[kontaktpersonTelefon] = ""
                it[ansattFnr] = ""
                it[ansattNavn] = ""
                it[ekspertNavn] = ""
                it[ekspertVirksomhet] = ""
                it[ekspertKompetanse] = ""
                it[navKontaktPerson] = ""
                it[beliggenhetsadresse] = ""
                it[status] = SkjemaStatus.innsendt.toString()
            }.single().tilSkjemaDTO().also {
                assertEquals(SkjemaStatus.innsendt, it.status)
            }
        }.copy(id = UUID.randomUUID().toString())

        val event = Event(
            id = 1L, data = EventData.SoknadAvlystIArena(
                skjema = skjema,
                tiltaksgjennomforingEndret = TiltaksgjennomforingEndret(
                    tiltaksgjennomfoeringId = 1,
                    tiltakKode = EKSPERTBISTAND_TILTAKSKODE,
                    tiltakStatusKode = TiltaksgjennomforingEndret.TiltakStatusKode.AVLYST,
                )
            )
        )
        assertIs<EventHandledResult.UnrecoverableError>(handler.handle(event))
    }
}