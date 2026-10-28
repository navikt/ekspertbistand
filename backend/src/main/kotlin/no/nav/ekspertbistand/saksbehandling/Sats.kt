package no.nav.ekspertbistand.saksbehandling

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class MaksSats(val gyldigFra: LocalDate, val belop: Int)

/**
 * Maks tilskudd per sak. Kilde: MD-brev eller fag. En ny sats legges til med [MaksSats.gyldigFra]
 * og kan deployes før den gjelder. Se `specifications/forelopig_vedtak.md` (2b).
 */
val MAKS_SATSER: List<MaksSats> = listOf(
    MaksSats(gyldigFra = LocalDate(2026, 1, 1), belop = 24_900),
)

/** Satsen med senest [MaksSats.gyldigFra] som ikke er etter [dato], eller null hvis ingen gjelder. */
fun gjeldendeMaksSats(dato: LocalDate, satser: List<MaksSats> = MAKS_SATSER): MaksSats? =
    satser.filter { it.gyldigFra <= dato }.maxByOrNull { it.gyldigFra }
