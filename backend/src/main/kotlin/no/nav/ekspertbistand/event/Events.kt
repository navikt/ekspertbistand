package no.nav.ekspertbistand.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Event {


    @Serializable
    @SerialName("foo")
    data class Foo(
        val fooName: String
    ) : Event

    @Serializable
    @SerialName("bar")
    data class Bar(
        val barName: String
    ) : Event
}

