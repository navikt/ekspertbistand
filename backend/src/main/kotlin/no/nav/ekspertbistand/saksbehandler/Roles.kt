package no.nav.ekspertbistand.saksbehandler

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.basedOnEnv

@Serializable
enum class Role(val groupId: String) {
    SAKSBEHANDLER(basedOnEnv(
        prod = "",
        dev = "fbfba82d-13da-43ad-a2f2-d7f21cb95f42",
        other = "test-saksbehandler-group-id",
    )),
    BESLUTTER(basedOnEnv(
        prod = "",
        dev = "fbfea82d-13da-43ad-a2f2-d7f21cb95f12",
        other = "test-beslutter-group-id",
    )),
    FORTROLIG_ADRESSE(basedOnEnv(
        prod = "",
        dev = "2e1dc582-f762-4510-a660-88bf68fb7128",
        other = "test-fortrolig-group-id",
    )),
    STRENGT_FORTROLIG_ADRESSE(basedOnEnv(
        prod = "",
        dev = "a1b518e2-3947-478d-8929-e5d685c47cac",
        other = "test-strengt-fortrolig-group-id",
    ));

    companion object {
        fun fromGroups(groups: List<String>): Set<Role> =
            entries.filter { it.groupId in groups }.toSet()
    }
}

