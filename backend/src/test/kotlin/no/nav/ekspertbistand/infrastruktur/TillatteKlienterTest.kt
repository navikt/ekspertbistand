package no.nav.ekspertbistand.infrastruktur

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TillatteKlienterTest {

    private val saksbehandlingDev = "aaaaaaaa-0000-0000-0000-000000000001"
    private val tokenGeneratorDev = "aaaaaaaa-0000-0000-0000-000000000002"
    private val saksbehandlingProd = "bbbbbbbb-0000-0000-0000-000000000001"
    private val tokenGeneratorProd = "bbbbbbbb-0000-0000-0000-000000000002"
    private val frontend = "cccccccc-0000-0000-0000-000000000001"
    private val e2eMock = "cccccccc-0000-0000-0000-000000000002"

    private fun app(name: String, clientId: String) = """{"name": "$name", "clientId": "$clientId"}"""

    @Test
    fun `dev godtar frontend for TokenX og saksbehandling og token-generator for Entra`() {
        val json = listOf(
            app("dev-gcp:fager:ekspertbistand-saksbehandling", saksbehandlingDev),
            app("dev-gcp:nais:azure-token-generator", tokenGeneratorDev),
            app("dev-gcp:fager:ekspertbistand-frontend", frontend),
            app("dev-gcp:fager:ekspertbistand-saksbehandling-mock", e2eMock),
        ).joinToString(prefix = "[", postfix = "]")

        val klienter = TillatteKlienter.fra("dev-gcp", json)

        assertEquals(setOf("dev-gcp:fager:ekspertbistand-frontend"), klienter.tokenX)
        assertEquals(setOf(saksbehandlingDev, tokenGeneratorDev), klienter.azureAd)
    }

    @Test
    fun `prod godtar aldri azure-token-generator`() {
        val json = listOf(
            app("prod-gcp:fager:ekspertbistand-saksbehandling", saksbehandlingProd),
            app("prod-gcp:nais:azure-token-generator", tokenGeneratorProd),
            app("dev-gcp:nais:azure-token-generator", tokenGeneratorDev),
        ).joinToString(prefix = "[", postfix = "]")

        val klienter = TillatteKlienter.fra("prod-gcp", json)

        assertEquals(setOf("prod-gcp:fager:ekspertbistand-frontend"), klienter.tokenX)
        assertEquals(setOf(saksbehandlingProd), klienter.azureAd)
    }

    @Test
    fun `prod uten saksbehandling i inbound godtar ingen Entra-klienter`() {
        val klienter = TillatteKlienter.fra("prod-gcp", "[]")

        assertEquals(emptySet(), klienter.azureAd)
    }

    @Test
    fun `ukjente felt i AZURE_APP_PRE_AUTHORIZED_APPS ignoreres`() {
        val json = """[{"name": "dev-gcp:fager:ekspertbistand-saksbehandling", "clientId": "$saksbehandlingDev", "nytt": 1}]"""

        assertEquals(setOf(saksbehandlingDev), TillatteKlienter.fra("dev-gcp", json).azureAd)
    }

    @Test
    fun `ukjent eller manglende cluster stopper oppstarten`() {
        assertFailsWith<IllegalStateException> { TillatteKlienter.fra("local", "[]") }
        assertFailsWith<IllegalStateException> { TillatteKlienter.fra(null, "[]") }
    }

    @Test
    fun `manglende AZURE_APP_PRE_AUTHORIZED_APPS stopper oppstarten`() {
        assertFailsWith<IllegalArgumentException> { TillatteKlienter.fra("dev-gcp", null) }
    }

    @Test
    fun `ugyldig AZURE_APP_PRE_AUTHORIZED_APPS stopper oppstarten`() {
        assertFailsWith<IllegalArgumentException> { TillatteKlienter.fra("dev-gcp", "ikke json") }
        assertFailsWith<IllegalArgumentException> { TillatteKlienter.fra("dev-gcp", """[{"name": "x"}]""") }
        assertFailsWith<IllegalArgumentException> {
            TillatteKlienter.fra("dev-gcp", """[{"name": "dev-gcp:fager:ekspertbistand-saksbehandling", "clientId": " "}]""")
        }
    }
}
