package no.nav.ekspertbistand.executables

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Henter dagens dokgen-PDF-er for alle maler og testdata, så de kan sammenlignes visuelt med de nye
 * Gotenberg-PDF-ene fra [main] i `RenderDokumentmaler` (§5). PDF-ene skrives til
 * `backend/target/dokumentmaler-preview/dokgen/` med samme filnavn som Gotenberg-varianten, slik at
 * de kan legges side om side eller diffes.
 *
 * Forutsetter at lokal dokgen kjører på `http://localhost:9000`:
 * ```
 * docker compose -f backend/docker-compose.yml up -d dokgen
 * ```
 * Testdataene postes som de er til `/template/{mal}/create-pdf`, samme kontrakt som den tidligere
 * `DokgenClient` brukte. Kan kjøres fra repo-roten eller fra `backend/`.
 */
private const val dokgenBaseUrl = "http://localhost:9000"

fun main() {
    val backendDir = listOf(File("backend"), File("."))
        .firstOrNull { File(it, "src/main/kotlin").isDirectory }
        ?: error("Fant ikke backend/src/main/kotlin fra ${File(".").absolutePath}")

    val templatesDir = File(backendDir, "src/main/resources/dokumentmaler/templates")
    val utDir = File(backendDir, "target/dokumentmaler-preview/dokgen").apply { mkdirs() }

    HttpClient(CIO).use { httpClient ->
        val maler = templatesDir.listFiles { f -> f.isDirectory && File(f, "template.hbs").exists() }
            ?.sortedBy { it.name } ?: emptyList()

        runBlocking {
            maler.forEach { mal ->
                val testdata = File(mal, "testdata").listFiles { f -> f.extension == "json" }
                    ?.sortedBy { it.name } ?: emptyList()
                testdata.forEach { fil ->
                    val pdf: ByteArray = httpClient.post {
                        url("$dokgenBaseUrl/template/${mal.name}/create-pdf")
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Pdf)
                        setBody(fil.readText())
                    }.body()

                    check(pdf.harPdfHeader()) {
                        "Dokgen returnerte ikke en gyldig PDF for ${mal.name}/${fil.name}"
                    }

                    val ut = File(utDir, "${mal.name}-${fil.nameWithoutExtension}.pdf")
                    ut.writeBytes(pdf)
                    println("Skrev ${ut.relativeTo(backendDir)}")
                }
            }
        }
    }
    println("Ferdig. Dokgen-PDF-er ligger i ${utDir.relativeTo(backendDir)}")
}

private val pdfMagicNumber = "%PDF".toByteArray()

private fun ByteArray.harPdfHeader() =
    size >= pdfMagicNumber.size && sliceArray(pdfMagicNumber.indices).contentEquals(pdfMagicNumber)
