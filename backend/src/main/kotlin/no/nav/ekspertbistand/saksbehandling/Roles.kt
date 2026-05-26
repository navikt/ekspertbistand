package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.basedOnEnv

@Serializable
enum class Role(val groupId: String) {
    SAKSBEHANDLER(basedOnEnv(
        prod = "",
        dev = "",
        other = "test-saksbehandler-group-id",
    )),
    BESLUTTER(basedOnEnv(
        prod = "",
        dev = "",
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

