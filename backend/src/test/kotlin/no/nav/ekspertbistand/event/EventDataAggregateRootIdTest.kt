package no.nav.ekspertbistand.event

import no.nav.ekspertbistand.soknad.aggregateRootId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P3-modelltestene: `EventData.aggregateRootId` er implementert for hver subklasse, gir forventet
 * verdi, og er aldri blank.
 */
class EventDataAggregateRootIdTest {

    private val soknadRoot = TestEventData.sampleSoknad.aggregateRootId
    private val tilsagnRoot = "1337:42:43"

    @Test
    fun `hver subklasse deriverer forventet aggregateRootId og aldri blank`() {
        val forventet: Map<EventData, String> = mapOf(
            EventData.SoknadInnsendt(TestEventData.sampleSoknad) to soknadRoot,
            TestEventData.innsendtSoknadJournalfoert to soknadRoot,
            EventData.TiltaksgjennomforingOpprettet(TestEventData.sampleSoknad, "2026202", 1) to soknadRoot,
            EventData.TilskuddsbrevMottatt(TestEventData.sampleSoknad, 1, TestEventData.sampleTilsagnData) to soknadRoot,
            EventData.TilskuddsbrevMottattKildeAltinn(1, TestEventData.sampleTilsagnData) to tilsagnRoot,
            EventData.TilskuddsbrevJournalfoert(TestEventData.sampleSoknad, 1, 2, TestEventData.sampleTilsagnData) to soknadRoot,
            EventData.TilskuddsbrevJournalfoertKildeAltinn(1, 2, TestEventData.sampleTilsagnData) to tilsagnRoot,
            EventData.SoknadAvlystIArena(TestEventData.sampleSoknad, TestEventData.sampleTiltaksgjennomforingEndret) to soknadRoot,
            EventData.SaksbehandlingStartetIArena(TestEventData.sampleSoknad, TestEventData.sampleTiltakssakEndret) to soknadRoot,
            EventData.TilsagnsdataLagret(TestEventData.sampleSoknad, TestEventData.sampleTilsagnData) to soknadRoot,
            EventData.TilskuddsbrevVist("1337:42:43", TestEventData.sampleSoknad) to soknadRoot,
            EventData.TilskuddsbrevVist("1337:42:43", null) to tilsagnRoot,
        )

        forventet.forEach { (event, expected) ->
            assertEquals(expected, event.aggregateRootId, "feil aggregateRootId for ${event::class.simpleName}")
            assertTrue(event.aggregateRootId.isNotBlank(), "aggregateRootId er blank for ${event::class.simpleName}")
        }
    }

    @Test
    fun `alle sealedSubclasses er dekket og gir aldri blank aggregateRootId`() {
        val dekkede = TestEventData.allEventSamples.map { it::class }.toSet()
        val alle = EventData::class.sealedSubclasses.toSet()

        assertEquals(
            alle,
            dekkede,
            "Alle EventData-subklasser må ha et testeksempel. Mangler: ${alle - dekkede}"
        )

        TestEventData.allEventSamples.forEach { event ->
            assertTrue(
                event.aggregateRootId.isNotBlank(),
                "aggregateRootId er blank for ${event::class.simpleName}"
            )
        }
    }
}
