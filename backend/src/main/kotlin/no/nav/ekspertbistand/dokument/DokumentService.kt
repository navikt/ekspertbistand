package no.nav.ekspertbistand.dokument

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.dokument.pdf.DokumentRenderer
import no.nav.ekspertbistand.dokument.pdf.Format
import no.nav.ekspertbistand.dokument.pdf.PdfKonverterer
import no.nav.ekspertbistand.infrastruktur.defaultJson
import no.nav.ekspertbistand.soknad.DTO
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Domeneadapter for dokumentgenerering.
 *
 * Erstatter den tidligere HTTP-baserte `DokgenClient`. Rendrer maler til HTML in-process via
 * [DokumentRenderer] og konverterer til PDF/A via [PdfKonverterer] (i praksis Gotenberg). De fire
 * offentlige metodesignaturene er uendret fra `DokgenClient`.
 *
 * En [Semaphore] begrenser hvor mange samtidige kall som går til PDF-konverteringen.
 */
class DokumentService(
    private val pdfKonverterer: PdfKonverterer,
    private val renderer: DokumentRenderer = DokumentRenderer(),
    private val samtidigeKonverteringer: Int = 4,
) {
    private val semaphore = Semaphore(samtidigeKonverteringer)

    suspend fun genererSoknadPdf(soknad: DTO.Soknad): ByteArray {
        val data = defaultJson.encodeToJsonElement(SoknadRequest.from(soknad)).jsonObject
        return renderPdf("soknad", data)
    }

    suspend fun genererTilskuddsbrevPdf(tilsagnData: TilsagnData): ByteArray {
        val data = defaultJson.encodeToJsonElement(tilsagnData).jsonObject
        return renderPdf("tilskuddsbrev", data)
    }

    suspend fun genererTilskuddsbrevHtml(tilsagnData: TilsagnData): String {
        val data = defaultJson.encodeToJsonElement(tilsagnData).jsonObject
        return renderer.renderHtml("tilskuddsbrev", data, Format.HTML)
    }

    suspend fun genererArenaNotatPdf(
        saksnummer: String,
        tiltaksgjennomfoeringId: String,
    ): ByteArray {
        val data: JsonObject = buildJsonObject {
            put("saksnummer", saksnummer)
            put("tiltaksgjennomfoeringId", tiltaksgjennomfoeringId)
        }
        return renderPdf("arenaNotat", data)
    }

    private suspend fun renderPdf(templateName: String, data: JsonObject): ByteArray {
        val html = renderer.renderHtml(templateName, data, Format.PDF)
        val bytes = semaphore.withPermit { pdfKonverterer.tilPdfA(html, renderer.assetsForPdf()) }
        check(bytes.hasPdfHeader()) { "Generert dokument for $templateName er ikke en gyldig PDF" }
        return bytes
    }
}

private val pdfMagicNumber = "%PDF".toByteArray()

private fun ByteArray.hasPdfHeader() =
    size >= pdfMagicNumber.size && sliceArray(0 until pdfMagicNumber.size).contentEquals(pdfMagicNumber)

@Serializable
private data class SoknadRequest(
    val virksomhet: Virksomhet,
    val ansatt: Ansatt,
    val ekspert: Ekspert,
    val behovForBistand: BehovForBistand,
    val nav: Nav,
    val opprettetDato: String,
) {
    @Serializable
    data class Virksomhet(
        val virksomhetsnummer: String,
        val virksomhetsnavn: String,
        val kontaktperson: Kontaktperson,
        val beliggenhetsadresse: String?
    )

    @Serializable
    data class Kontaktperson(
        val navn: String,
        val epost: String,
        val telefonnummer: String,
    )

    @Serializable
    data class Ansatt(
        val fnr: String,
        val navn: String,
    )

    @Serializable
    data class Ekspert(
        val navn: String,
        val virksomhet: String,
        val godkjentUtdanningEllerAutorisasjon: List<String>,
        val relevantKompetanse: List<String>,
    )

    @Serializable
    data class BehovForBistand(
        val begrunnelse: String,
        val behov: String,
        val estimertKostnad: String,
        val timer: String,
        val tilrettelegging: String,
        val startdato: String,
    )

    @Serializable
    data class Nav(
        val kontaktperson: String,
    )

    companion object {
        @OptIn(ExperimentalTime::class)
        fun from(dto: DTO.Soknad) = SoknadRequest(
            virksomhet = Virksomhet(
                virksomhetsnummer = dto.virksomhet.virksomhetsnummer,
                virksomhetsnavn = dto.virksomhet.virksomhetsnavn,
                kontaktperson = Kontaktperson(
                    navn = dto.virksomhet.kontaktperson.navn,
                    epost = dto.virksomhet.kontaktperson.epost,
                    telefonnummer = dto.virksomhet.kontaktperson.telefonnummer,
                ),
                beliggenhetsadresse = dto.virksomhet.beliggenhetsadresse,
            ),
            ansatt = Ansatt(
                fnr = dto.ansatt.fnr,
                navn = dto.ansatt.navn,
            ),
            ekspert = Ekspert(
                navn = dto.ekspert.navn,
                virksomhet = dto.ekspert.virksomhet,
                godkjentUtdanningEllerAutorisasjon = dto.ekspert.godkjentUtdanningEllerAutorisasjon
                    .ifEmpty { if (dto.ekspert.kompetanse.isNotBlank()) listOf(dto.ekspert.kompetanse) else emptyList() },
                relevantKompetanse = dto.ekspert.relevantKompetanse,
            ),
            behovForBistand = BehovForBistand(
                begrunnelse = dto.behovForBistand.begrunnelse,
                behov = dto.behovForBistand.behov,
                estimertKostnad = dto.behovForBistand.estimertKostnad,
                timer = dto.behovForBistand.timer,
                tilrettelegging = dto.behovForBistand.tilrettelegging,
                startdato = dto.behovForBistand.startdato.toString(),
            ),
            nav = Nav(
                kontaktperson = dto.nav.kontaktperson,
            ),
            opprettetDato = dto.opprettetTidspunkt ?: Clock.System.now()
                .toLocalDateTime(TimeZone.of("Europe/Oslo")).date.toString(),
        )
    }
}
