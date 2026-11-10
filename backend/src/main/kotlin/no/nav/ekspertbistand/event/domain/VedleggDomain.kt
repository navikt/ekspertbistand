package no.nav.ekspertbistand.event.domain

import kotlinx.serialization.Serializable

/** Domain-type for eventmodellen, isolert fra API-DTO-er. */
@Serializable
data class VedleggDomain(val vedleggId: String, val filnavn: String, val storrelse: Int)
