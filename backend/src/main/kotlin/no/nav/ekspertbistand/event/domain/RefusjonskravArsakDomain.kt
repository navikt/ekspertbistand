package no.nav.ekspertbistand.event.domain

import kotlinx.serialization.Serializable

@Serializable
enum class RefusjonskravArsakDomain {
    BELOP_MAA_JUSTERES,
    DOKUMENT_IKKE_FAKTURA,
    DOKUMENT_IKKE_LESELIG,
    ANNET,
}
