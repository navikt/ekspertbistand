package no.nav.ekspertbistand.clamav

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.logger

class ClamAvClient(
    defaultHttpClient: HttpClient,
) {
    private val log = logger()

    private val httpClient = defaultHttpClient.config {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "clamav.client"
        }
    }

    companion object {
        val endpointUrl: String? = basedOnEnv(
            prod = "http://clamav.nais-system/scan",
            dev = "http://clamav.nais-system/scan",
            other = null,
        )
    }

    suspend fun scan(filnavn: String, innhold: ByteArray): ScanResultat {
        val url = endpointUrl ?: run {
            log.warn("ClamAV ikke konfigurert (lokalt miljø) — hopper over virusskanning for fil: {}", filnavn)
            return ScanResultat(filnavn, ScanStatus.OK)
        }

        log.info("Virusscanner fil: filnavn={}, størrelse={}B", filnavn, innhold.size)

        val response = httpClient.submitFormWithBinaryData(
            url = url,
            formData = formData {
                append(
                    "file0",
                    innhold,
                    Headers.build {
                        append(HttpHeaders.ContentType, "application/pdf")
                        append(HttpHeaders.ContentDisposition, "filename=\"${filnavn.replace("\n", "")}\"")
                    }
                )
            }
        )

        val resultater = response.body<List<ScanResultatDto>>()
        val resultat = resultater.firstOrNull()
            ?: return ScanResultat(filnavn, ScanStatus.ERROR).also {
                log.error("ClamAV returnerte tomt svar for fil: {}", filnavn)
            }

        return ScanResultat(
            filnavn = resultat.Filename,
            status = when (resultat.Result) {
                "OK" -> ScanStatus.OK
                "FOUND" -> ScanStatus.FOUND.also {
                    log.warn("Virus funnet i fil: {}", filnavn)
                }
                else -> ScanStatus.ERROR.also {
                    log.error("Uventet ClamAV-status '{}' for fil: {}", resultat.Result, filnavn)
                }
            }
        )
    }

    data class ScanResultat(val filnavn: String, val status: ScanStatus)

    enum class ScanStatus { OK, FOUND, ERROR }
}

@Serializable
private data class ScanResultatDto(
    val Filename: String,
    val Result: String,
)
