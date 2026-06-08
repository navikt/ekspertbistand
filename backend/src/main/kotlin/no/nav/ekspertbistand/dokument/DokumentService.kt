package no.nav.ekspertbistand.dokument

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import no.nav.ekspertbistand.dokument.pdf.PdfGenerator
import no.nav.ekspertbistand.dokument.pdf.PdfGeneratorImpl
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.infrastruktur.defaultJson
import no.nav.ekspertbistand.soknad.DTO
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Domeneadapter for dokumentgenerering.
 *
 * Erstatter den tidligere HTTP-baserte `DokgenClient`. Rendrer PDF/HTML in-process via den
 * generiske [PdfGenerator]-kjernen (`no.nav.ekspertbistand.dokument.pdf`) i stedet for å kalle
 * den separate `ekspertbistand-dokgen`-tjenesten. De fire offentlige metodesignaturene er uendret.
 */
class DokumentService(
    private val pdf: PdfGenerator = PdfGeneratorImpl(resourcePrefix = "dokumentmaler"),
) {
    suspend fun genererSoknadPdf(soknad: DTO.Soknad): ByteArray = withContext(Dispatchers.IO) {
        val data = defaultJson.encodeToJsonElement(SoknadRequest.from(soknad)).jsonObject
        val bytes = pdf.renderPdf("soknad", data)
        check(bytes.hasPdfHeader()) { "Generert dokument for soknad er ikke en gyldig PDF" }
        bytes
    }

    suspend fun genererTilskuddsbrevPdf(tilsagnData: TilsagnData): ByteArray = withContext(Dispatchers.IO) {
        val data = defaultJson.encodeToJsonElement(tilsagnData).jsonObject
        val bytes = pdf.renderPdf("tilskuddsbrev", data)
        check(bytes.hasPdfHeader()) { "Generert dokument for tilskuddsbrev er ikke en gyldig PDF" }
        bytes
    }

    suspend fun genererTilskuddsbrevHtml(tilsagnData: TilsagnData): String = withContext(Dispatchers.IO) {
        val data = defaultJson.encodeToJsonElement(tilsagnData).jsonObject
        pdf.renderHtml("tilskuddsbrev", data)
    }

    suspend fun genererArenaNotatPdf(
        saksnummer: String,
        tiltaksgjennomfoeringId: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val data: JsonObject = buildJsonObject {
            put("saksnummer", saksnummer)
            put("tiltaksgjennomfoeringId", tiltaksgjennomfoeringId)
        }
        val bytes = pdf.renderPdf("arenaNotat", data)
        check(bytes.hasPdfHeader()) { "Generert dokument for arenaNotat er ikke en gyldig PDF" }
        bytes
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
    val opprettetTidspunkt: String,
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
            opprettetTidspunkt = dto.opprettetTidspunkt ?: Clock.System.now()
                .toLocalDateTime(TimeZone.of("Europe/Oslo")).date.toString(),
        )
    }
}
