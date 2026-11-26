package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable

/**
 * Arbeidsgivers virksomhetstype i saksoversikten, mappet fra SSBs institusjonelle sektorkode på juridisk enhet
 * ([SakTable.sektorkode]). Sektorkoden lagres på saken og mappes ved lesing, så kategoriseringen kan endres her
 * uten migrering. Mangler koden, eller er den ukjent, blir typen [UKJENT].
 *
 * Se `specifications/kategorisering_virksomhetstype.md` og
 * [Sektorkoder](https://kodeverk.ansatt.nav.no/kodeverk/Sektorkoder).
 */
@Serializable
enum class Virksomhetstype {
    STATLIG, KOMMUNAL, PRIVAT, UKJENT;

    companion object {
        fun fraSektorkode(sektorkode: String?): Virksomhetstype = when (sektorkode) {
            "1110", "3100", "3900", "6100" -> STATLIG
            "1510", "1520", "6500" -> KOMMUNAL
            "1120", "2100", "2300", "2500", "3200", "3500", "3600", "4100",
            "4300", "4500", "4900", "5500", "5700", "7000", "8200", "8300",
            "9000" -> PRIVAT
            else -> UKJENT
        }
    }
}
