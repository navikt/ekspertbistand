package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.arena.EKSPERTBISTAND_TILTAKSKODE
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.slettSøknadOm
import no.nav.ekspertbistand.soknad.tilSoknadDTO
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SettAvlystSoknadStatusTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Søknad settes til avlyst`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        var now = Instant.parse("2026-01-01T12:00:00Z")
        val handler = SettAvlystSoknadStatus(database = database, clock = object : Clock {
            override fun now(): Instant {
                return now
            }
        })

        val soknad = transaction(database) {
            SoknadTable.insertReturning {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "1337"
                it[virksomhetsnavn] = " foo bar AS"
                it[opprettetAv] = "42"
                it[behovForBistand] = "innsendt soknad"
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
                it[status] = SoknadStatus.innsendt.toString()
                it[sletteTidspunkt] = now
            }.single().also { rw ->
                assertEquals(SoknadStatus.innsendt, SoknadStatus.valueOf(rw[SoknadTable.status]))
                assertEquals(now, rw[SoknadTable.sletteTidspunkt])
            }.tilSoknadDTO()
        }
        // flytt now 1 dag frem i tid
        now = now.plus(1.days)

        val event = Event(
            id = 1L, data = EventData.SoknadAvlystIArena(
                soknad = soknad,
                tiltaksgjennomforingEndret = TiltaksgjennomforingEndret(
                    tiltaksgjennomfoeringId = 1,
                    tiltakKode = EKSPERTBISTAND_TILTAKSKODE,
                    tiltakStatusKode = TiltaksgjennomforingEndret.TiltakStatusKode.AVLYST,
                )
            )
        )
        assertIs<EventHandledResult.Success>(handler.handle(event))

        val nyttSletteTidspunkt = now.plus(slettSøknadOm)
        transaction(database) {
            SoknadTable.selectAll().single().let { rw ->
                assertEquals(SoknadStatus.avlyst, SoknadStatus.valueOf(rw[SoknadTable.status]))
                assertEquals(nyttSletteTidspunkt, rw[SoknadTable.sletteTidspunkt])
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Søknad finnes ikke i databasen returnerer unrecoverable`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val handler = SettAvlystSoknadStatus(database = database)

        val soknad = transaction(database) {
            SoknadTable.insertReturning {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "1337"
                it[virksomhetsnavn] = " foo bar AS"
                it[opprettetAv] = "42"
                it[behovForBistand] = "innsendt soknad"
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
                it[status] = SoknadStatus.innsendt.toString()
            }.single().tilSoknadDTO().also {
                assertEquals(SoknadStatus.innsendt, it.status)
            }
        }.copy(id = UUID.randomUUID().toString())

        val event = Event(
            id = 1L, data = EventData.SoknadAvlystIArena(
                soknad = soknad,
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