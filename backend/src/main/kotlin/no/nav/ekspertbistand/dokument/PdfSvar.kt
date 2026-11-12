package no.nav.ekspertbistand.dokument

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun ApplicationCall.utenMellomlagring() {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header(HttpHeaders.Pragma, "no-cache")
    response.header("X-Content-Type-Options", "nosniff")
}

suspend fun ApplicationCall.svarMedPdf(filnavn: String, innhold: ByteArray, inline: Boolean) {
    utenMellomlagring()
    val disposisjon = if (inline) ContentDisposition.Inline else ContentDisposition.Attachment
    val trygtFilnavn = filnavn.substringAfterLast('/').substringAfterLast('\\')
        .substringBeforeLast('.')
        .map { tegn -> if (tegn.code < 128 && (tegn.isLetterOrDigit() || tegn in "-_.")) tegn else '_' }
        .joinToString("")
        .take(90)
        .ifBlank { "dokument" } + ".pdf"
    response.header(
        HttpHeaders.ContentDisposition,
        disposisjon.withParameter(ContentDisposition.Parameters.FileName, trygtFilnavn).toString(),
    )
    respondBytes(innhold, ContentType.Application.Pdf)
}
