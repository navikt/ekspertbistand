package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable

@Serializable
enum class Role(val groupId: String) {
    SAKSBEHANDLER("0000-CA-Ekspertbistand_Saksbehandler"),
    BESLUTTER("0000-CA-Ekspertbistand_Beslutter"),
    FORTROLIG_ADRESSE("0000-GA-Fortrolig_Adresse"),
    STRENGT_FORTROLIG_ADRESSE("0000-GA-Strengt_Fortrolig_Adresse");

    companion object {
        fun fromGroups(groups: List<String>): Set<Role> =
            entries.filter { it.groupId in groups }.toSet()
    }
}

