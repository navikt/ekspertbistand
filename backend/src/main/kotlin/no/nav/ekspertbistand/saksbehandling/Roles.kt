package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.basedOnEnv

@Serializable
enum class Role(val groupId: String) {
    SAKSBEHANDLER(basedOnEnv(
        prod = "",
        dev = "1b3a2c4d-d620-4fcf-a29b-a6cdadf29680",
        other = "test-saksbehandler-group-id",
    )),
    BESLUTTER(basedOnEnv(
        prod = "",
        dev = "79985315-b2de-40b8-a740-9510796993c6",
        other = "test-beslutter-group-id",
    )),
    FORTROLIG_ADRESSE(basedOnEnv(
        prod = "",
        dev = "",
        other = "test-fortrolig-group-id",
    )),
    STRENGT_FORTROLIG_ADRESSE(basedOnEnv(
        prod = "",
        dev = "",
        other = "test-strengt-fortrolig-group-id",
    ));

    companion object {
        fun fromGroups(groups: List<String>): Set<Role> =
            entries.filter { it.groupId in groups }.toSet()
    }
}

