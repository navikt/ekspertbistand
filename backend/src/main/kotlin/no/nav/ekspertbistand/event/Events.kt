package no.nav.ekspertbistand.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Event {
    val id: Long


    @Serializable
    @SerialName("foo")
    data class Foo(
        override val id: Long,
        val fooName: String
    ) : Event

    @Serializable
    @SerialName("bar")
    data class Bar(
        override val id: Long,
        val barName: String
    ) : Event
}

