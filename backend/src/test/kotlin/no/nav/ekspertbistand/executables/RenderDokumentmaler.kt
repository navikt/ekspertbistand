package no.nav.ekspertbistand.executables

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.nav.ekspertbistand.dokument.gotenberg.GotenbergClient
import no.nav.ekspertbistand.dokument.pdf.DokumentRenderer
import no.nav.ekspertbistand.dokument.pdf.Format
import java.io.File

/**
 * Rendrer alle testdata-filene for hver mal via lokal Gotenberg til
 * `backend/target/dokumentmaler-preview/gotenberg/`, så PDF-ene kan sammenlignes visuelt med dagens
 * dokgen-PDF-er (§7.2, §5).
 *
 * Krever Gotenberg på `http://localhost:3003`:
 * ```
 * docker compose -f backend/docker-compose.yml up -d gotenberg
 * ```
 * Legg dagens dokgen-PDF-er ved siden av ved å kjøre [RenderDokumentmalerDokgenLokal] (henter fra
 * lokal dokgen på port 9000 til `dokumentmaler-preview/dokgen/`). Kan kjøres fra repo-roten eller fra `backend/`.
 */
fun main() {
    val backendDir = listOf(File("backend"), File("."))
        .firstOrNull { File(it, "src/main/kotlin").isDirectory }
        ?: error("Fant ikke backend/src/main/kotlin fra ${File(".").absolutePath}")

    val templatesDir = File(backendDir, "src/main/resources/dokumentmaler/templates")
    val utDir = File(backendDir, "target/dokumentmaler-preview/gotenberg").apply { mkdirs() }

    val renderer = DokumentRenderer()
    HttpClient(CIO).use { httpClient ->
        val client = GotenbergClient(httpClient)
        val maler = templatesDir.listFiles { f -> f.isDirectory && File(f, "template.hbs").exists() }
            ?.sortedBy { it.name } ?: emptyList()

        runBlocking {
            maler.forEach { mal ->
                val testdata = File(mal, "testdata").listFiles { f -> f.extension == "json" }
                    ?.sortedBy { it.name } ?: emptyList()
                testdata.forEach { fil ->
                    val data = Json.parseToJsonElement(fil.readText()) as JsonObject
                    val html = renderer.renderHtml(mal.name, data, Format.PDF)
                    val pdf = client.tilPdfA(html, renderer.assetsForPdf())
                    val ut = File(utDir, "${mal.name}-${fil.nameWithoutExtension}.pdf")
                    ut.writeBytes(pdf)
                    println("Skrev ${ut.relativeTo(backendDir)}")
                }
            }
        }
    }
    println("Ferdig. PDF-er ligger i ${utDir.relativeTo(backendDir)}")
}
