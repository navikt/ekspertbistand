package no.nav.ekspertbistand.dokument.pdf

import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
import java.awt.color.ColorSpace
import java.awt.color.ICC_Profile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Konverterer et komplett (X)HTML-dokument til PDF/A-2b via openhtmltopdf-pdfbox.
 *
 * PDF/A krever innebygde fonter og en innebygd ICC-fargeprofil. NAV-logoen i header
 * rendres som SVG via Batik.
 */
class PdfaRenderer(
    private val conformance: PdfRendererBuilder.PdfAConformance =
        PdfRendererBuilder.PdfAConformance.PDFA_2_B,
) {
    companion object {
        init {
            System.setProperty("java.awt.headless", "true")
        }
    }

    // sRGB ICC-profil fra JDK-en – kreves for PDF/A-konformitet.
    private val sRgbColorProfile: ByteArray =
        ICC_Profile.getInstance(ColorSpace.CS_sRGB).data

    fun render(html: String, fonts: List<FontResource>, baseUri: String = ""): ByteArray {
        val output = ByteArrayOutputStream()
        try {
            val builder = PdfRendererBuilder()
                .usePdfAConformance(conformance)
                .useColorProfile(sRgbColorProfile)
                .useSVGDrawer(BatikSVGDrawer())

            fonts.forEach { font ->
                builder.useFont(
                    FSSupplier<InputStream> { ByteArrayInputStream(font.bytes) },
                    font.family,
                    font.weight,
                    if (font.italic) BaseRendererBuilder.FontStyle.ITALIC else BaseRendererBuilder.FontStyle.NORMAL,
                    false,
                )
            }

            builder.withHtmlContent(html, baseUri)
            builder.toStream(output)
            builder.run()
        } catch (e: Exception) {
            throw PdfGenerationException("Klarte ikke å rendre HTML til PDF/A", e)
        }
        return output.toByteArray()
    }
}

