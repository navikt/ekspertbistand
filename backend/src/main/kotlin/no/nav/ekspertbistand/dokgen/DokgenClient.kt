package no.nav.ekspertbistand.dokgen

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultJson
import no.nav.ekspertbistand.soknad.DTO
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DokgenClient(
    defaultHttpClient: HttpClient,
) {
    companion object {
        val baseUrl: String = basedOnEnv(
            prod = "http://ekspertbistand-dokgen",
            dev = "http://ekspertbistand-dokgen",
            other = "http://localhost:9000",
        )
    }

    private val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "dokgen.client"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
        }
    }

    suspend fun genererSoknadPdf(soknad: DTO.Soknad): ByteArray {
        val payload = SoknadRequest.from(soknad)

        val bytes: ByteArray = httpClient.post {
            url {
                takeFrom(baseUrl)
                path("template", "soknad", "create-pdf")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Pdf)
            setBody(payload)
        }.body()

        check(bytes.hasPdfHeader()) {
            "Dokgen returnerte ikke en gyldig PDF for soknad/create-pdf"
        }

        return bytes
    }

    suspend fun genererTilskuddsbrevPdf(tilsagnData: TilsagnData): ByteArray {
        val bytes: ByteArray = httpClient.post {
            url {
                takeFrom(baseUrl)
                path("template", "tilskuddsbrev", "create-pdf")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Pdf)
            setBody(tilsagnData)
        }.body()

        check(bytes.hasPdfHeader()) {
            "Dokgen returnerte ikke en gyldig PDF for tilskuddbrev/create-pdf"
        }

        return bytes
    }

    suspend fun genererTilskuddsbrevHtml(tilsagnData: TilsagnData): String {
        return httpClient.post {
            url {
                takeFrom(baseUrl)
                path("template", "tilskuddsbrev", "create-html")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.Html)
            setBody(tilsagnData)
        }.body()
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
        val kompetanse: String,
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
                kompetanse = dto.ekspert.kompetanse,
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
