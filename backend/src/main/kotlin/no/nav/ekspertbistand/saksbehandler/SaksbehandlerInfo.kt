package no.nav.ekspertbistand.saksbehandler

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.entraproxy.Enhet

@Serializable
data class SaksbehandlerInfo(
    val navIdent: String,
    val visningNavn: String? = null,
    val fornavn: String? = null,
    val etternavn: String? = null,
    val epost: String? = null,
    val enhet: Enhet,
    val tident: String,
    val enheter: List<Enhet>,
    val roller: Set<Role>,
)

