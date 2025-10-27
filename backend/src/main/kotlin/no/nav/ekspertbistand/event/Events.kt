package no.nav.ekspertbistand.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


data class Event<T: EventData>(
    val id: Long,
    val data: T
)

@Serializable
sealed interface EventData {


    @Serializable
    @SerialName("foo")
    data class Foo(
        val fooName: String
    ) : EventData

    @Serializable
    @SerialName("bar")
    data class Bar(
        val barName: String
    ) : EventData
}

