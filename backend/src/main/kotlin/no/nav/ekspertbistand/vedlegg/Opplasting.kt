package no.nav.ekspertbistand.vedlegg

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.utils.io.readBuffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray

@Suppress("ArrayInDataClass")
internal data class OpplastetFil(val filnavn: String, val innhold: ByteArray)

internal sealed interface Opplasting {
    class Ok(val filer: List<OpplastetFil>, val felter: Map<String, String>) : Opplasting
    class ForStorFil(val filnavn: String) : Opplasting
    data object ForMangeFiler : Opplasting
}

/**
 * Leser en multipart-opplasting uten å holde mer enn [maksAntallFiler] filer på [maksFilStorrelse] byte i minnet.
 * Avbryter så snart en fil er for stor eller det kommer for mange filer, i stedet for å lese hele forespørselen først.
 * Bare skjemafelt med navn i [feltnavn] tas vare på.
 *
 * Ktor stopper selv deler, både filer og skjemafelt, som er større enn `formFieldLimit`, og kaster da
 * [erForStorMultipartDel]. Det gir 413, se `StatusPages` i `Application.kt`.
 */
internal suspend fun ApplicationCall.lesOpplasting(
    maksAntallFiler: Int,
    maksFilStorrelse: Int,
    feltnavn: Set<String> = emptySet(),
): Opplasting {
    val multipart = receiveMultipart(formFieldLimit = maksFilStorrelse.toLong())
    val filer = mutableListOf<OpplastetFil>()
    val felter = mutableMapOf<String, String>()

    while (true) {
        val part = multipart.readPart() ?: break
        try {
            when (part) {
                is PartData.FileItem -> {
                    if (filer.size >= maksAntallFiler) return Opplasting.ForMangeFiler
                    val filnavn = part.originalFileName?.ifBlank { null } ?: "vedlegg.pdf"
                    val innhold = part.provider().readBuffer(maksFilStorrelse + 1L).readByteArray()
                    if (innhold.size > maksFilStorrelse) return Opplasting.ForStorFil(filnavn)
                    filer += OpplastetFil(filnavn, innhold)
                }

                is PartData.FormItem -> part.name?.takeIf { it in feltnavn }?.let { felter[it] = part.value }
                else -> {}
            }
        } finally {
            part.release()
        }
    }

    return Opplasting.Ok(filer, felter)
}

/** Unntaket Ktor kaster når en multipart-del er større enn `formFieldLimit`. */
fun Throwable.erForStorMultipartDel(): Boolean =
    this is IOException && message?.contains("formFieldLimit") == true

internal fun forStorFilMelding(filnavn: String) = "Filen '$filnavn' overskrider maks 10 MB"
