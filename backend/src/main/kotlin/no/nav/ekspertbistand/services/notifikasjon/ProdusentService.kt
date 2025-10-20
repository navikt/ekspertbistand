//package no.nav.ekspertbistand.services.notifikasjon
//
//import no.nav.ekspertbistand.infrastruktur.logger
//
//class ProdusentService(
//    private val client: ProdusentClient
//) {
//
//    private val logger = logger()
//
//    suspend fun opprettEkspertBistandSak(event: OpprettEkstertBistandEvent): EventResult {
//        try {
//            val sak = event.tilSakInput()
//            return client.opprettSak(sak).fold(
//                onSuccess = { status ->
//                    EventResult(Status.Success, event)
//                },
//                onFailure = {
//                    EventResult(Status.Reschedule, event)
//                }
//            )
//        }
//        catch (e: Exception) {
//            logger.error("Feil ved produsenting av sak: ${e.message}")
//            return EventResult(Status.Reschedule, event)
//        }
//    }
//}
//
//sealed interface Event {
//}
//
//data class OpprettEkstertBistandEvent(
//    val id: String,
//    val fnr: String,
//    val orgnr: String,
//    //etc.
//) : Event {
//
//    fun tilSakInput(): NySakInput {
//        return NySakInput(
//
//        )
//    }
//
//}
//
//data class EventResult(
//    val status: Status,
//    val event: Event
//)
//
//enum class Status {
//    Success,
//    Reschedule,
//}
