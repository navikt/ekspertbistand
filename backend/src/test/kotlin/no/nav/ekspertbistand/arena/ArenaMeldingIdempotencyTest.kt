package no.nav.ekspertbistand.arena

import no.nav.ekspertbistand.arena.ArenaTiltaksgjennomforingEndretProcessorTest.Companion.createConsumerRecord
import no.nav.ekspertbistand.arena.ArenaTiltaksgjennomforingEndretProcessorTest.Companion.kafkaMelding
import no.nav.ekspertbistand.arena.ArenaTiltaksgjennomforingEndretProcessorTest.Companion.soknad
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret.TiltakStatusKode.AVLYST
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull


class ArenaMeldingIdempotencyTest {



    @Test
    fun `duplikatmelding med samme tilsagnsId skal kun publiseres èn gang`() =
        testApplicationWithDatabase { db ->
            val saksnummer = "2019319383"
            transaction {
                insertArenaSak(saksnummer, 123, ArenaTilsagnsbrevProcessorTest.soknad)
            }
            val processor = ArenaTilsagnsbrevProcessor(
                db.config.jdbcDatabase,
                Instant.EPOCH,
            )
            val record = ArenaTilsagnsbrevProcessorTest.createConsumerRecord(
                ArenaTilsagnsbrevProcessorTest.kafkaMelding(
                    1,
                    42,
                    ArenaTilsagnsbrevProcessorTest.eksempelMelding(EKSPERTBISTAND_TILTAKSKODE, 2019, 319383)
                )
            )

            processor.processRecord(record)
            processor.processRecord(record)

            transaction(db.config.jdbcDatabase) {
                val queuedEvents = QueuedEvents.selectAll().map { it.tilQueuedEvent() }
                assertEquals(1, queuedEvents.count())
                assertIs<EventData.TilskuddsbrevMottatt>(queuedEvents.first().eventData)
            }
        }

    @Test
    fun `duplikatmelding med samme tilskuddsbrevid skal kun publiseres én gang`() =
        testApplicationWithDatabase { db ->
            val tiltaksgjennomfoeringId = 1337
            transaction {
                insertArenaSak("2019319383", tiltaksgjennomfoeringId, soknad)
            }
            val processor = ArenaTiltaksgjennomforingEndretProcessor(
                db.config.jdbcDatabase,
                Instant.EPOCH
            )
            val record = createConsumerRecord(
                kafkaMelding(tiltaksgjennomfoeringId, "EKSPEBIST", AVLYST)
            )

            processor.processRecord(record)
            processor.processRecord(record)

            transaction(db.config.jdbcDatabase) {
                val queuedEvents = QueuedEvents.selectAll().map { it.tilQueuedEvent() }
                assertEquals(1, queuedEvents.count())
                val eventData = queuedEvents.first().eventData as EventData.SoknadAvlystIArena
                assertNotNull(eventData)
                assertEquals(tiltaksgjennomfoeringId, eventData.tiltaksgjennomforingEndret.tiltaksgjennomfoeringId)
            }
        }



    @Test
    fun `duplikatmeldinger med samme koordinat behandles en gang av hver prosessor`() {
        testApplicationWithDatabase { db ->
            val tiltaksgjennomfoeringId = 1337
            val saksnummer = "2019319383"
            transaction {
                insertArenaSak(saksnummer, tiltaksgjennomfoeringId, ArenaTilsagnsbrevProcessorTest.soknad)
            }

            val arenaTilsagnsbrevProcessor = ArenaTilsagnsbrevProcessor(
                db.config.jdbcDatabase,
                Instant.EPOCH,
            )
            val godkjentRecord = ArenaTilsagnsbrevProcessorTest.createConsumerRecord(
                ArenaTilsagnsbrevProcessorTest.kafkaMelding(
                    1,
                    42,
                    ArenaTilsagnsbrevProcessorTest.eksempelMelding(EKSPERTBISTAND_TILTAKSKODE, 2019, 319383)
                )
            )

            arenaTilsagnsbrevProcessor.processRecord(godkjentRecord)
            arenaTilsagnsbrevProcessor.processRecord(godkjentRecord)

            transaction(db.config.jdbcDatabase) {
                val queuedEvents = QueuedEvents.selectAll().map { it.tilQueuedEvent() }
                assertEquals(1, queuedEvents.count {
                    it.eventData is EventData.TilskuddsbrevMottatt
                })
            }

            val endretProcessor = ArenaTiltaksgjennomforingEndretProcessor(
                db.config.jdbcDatabase,
                Instant.EPOCH
            )
            val avlystRecord = createConsumerRecord(
                kafkaMelding(tiltaksgjennomfoeringId, "EKSPEBIST", AVLYST)
            )

            endretProcessor.processRecord(avlystRecord)
            endretProcessor.processRecord(avlystRecord)

            transaction(db.config.jdbcDatabase) {
                val queuedEvents = QueuedEvents.selectAll().map { it.tilQueuedEvent() }
                assertEquals(1, queuedEvents.count {
                    it.eventData is EventData.SoknadAvlystIArena
                })
            }
        }
    }


}

