package no.nav.ekspertbistand.event.domain

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.sak.TEST_ANSATT_FNR
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.soknad.findSoknadDomainV1ById
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Eventene i prod er lagret med `Json.Default`. Hver form i [lagredeFormer] skal kunne leses og
 * skrives tilbake uten at JSON-en endres.
 */
class SoknadDomainV1Test {

    @Test
    fun `lagret JSON leses og skrives uendret`() {
        lagredeFormer.forEach { (navn, json) ->
            val skrevet = Json.encodeToString(Json.decodeFromString<SoknadDomainV1>(json))

            assertEquals(Json.parseToJsonElement(json), Json.parseToJsonElement(skrevet), navn)
        }
    }

    @Test
    fun `manglende status blir innsendt`() {
        val soknad = Json.decodeFromString<SoknadDomainV1>(soknadJson(ekspertMedLister))

        assertEquals(SoknadDomainV1.Status.innsendt, soknad.status)
    }

    @Test
    fun `fritekstkompetanse gir tomme lister`() {
        val ekspert = Json.decodeFromString<SoknadDomainV1>(soknadJson(ekspertMedFritekst)).ekspert

        assertEquals("Ergonomi", ekspert.kompetanse)
        assertEquals(emptyList(), ekspert.godkjentUtdanningEllerAutorisasjon)
        assertEquals(emptyList(), ekspert.relevantKompetanse)
        assertEquals(null, ekspert.virksomhetOrgnr)
    }

    @Test
    fun `innsendt søknad fra databasen blir SoknadDomainV1`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val lagret = lagreSoknadOgSak(database)

        val soknad = transaction(database) {
            findSoknadDomainV1ById(lagret.soknadId, filterForArbeidsgiver = false)
        }!!

        assertEquals(lagret.soknadId.toString(), soknad.id)
        assertEquals("987654321", soknad.virksomhet.virksomhetsnummer)
        assertEquals("Testbedrift AS", soknad.virksomhet.virksomhetsnavn)
        assertEquals(TEST_ANSATT_FNR, soknad.ansatt.fnr)
        assertEquals(listOf("Ergoterapeut"), soknad.ekspert.godkjentUtdanningEllerAutorisasjon)
        assertEquals(listOf("Tilrettelegging"), soknad.ekspert.relevantKompetanse)
        assertEquals("42", soknad.opprettetAv)
        assertEquals(SoknadDomainV1.Status.innsendt, soknad.status)
    }

    companion object {
        private const val ekspertMedFritekst = """
            {"navn":"Ekspert Navn","virksomhet":"Ekspertselskap","kompetanse":"Ergonomi"}
        """

        private const val ekspertMedLister = """
            {
              "navn":"Ekspert Navn",
              "virksomhet":"Ekspertselskap",
              "godkjentUtdanningEllerAutorisasjon":["Ergoterapeut"],
              "relevantKompetanse":["Tilrettelegging"]
            }
        """

        private const val ekspertMedOrgnr = """
            {
              "navn":"Ekspert Navn",
              "virksomhet":"Ekspertselskap AS",
              "virksomhetNavn":"Ekspertselskap AS",
              "virksomhetOrgnr":"912345678",
              "godkjentUtdanningEllerAutorisasjon":["Ergoterapeut"],
              "relevantKompetanse":["Tilrettelegging"]
            }
        """

        private fun soknadJson(ekspert: String, status: String? = null): String {
            val statusFelt = status?.let { ""","status":"$it"""" } ?: ""
            return """
                {
                  "id":"8f3f7a1e-4c55-4d0c-9a51-7c2b1d1f0e11",
                  "virksomhet":{
                    "virksomhetsnummer":"987654321",
                    "virksomhetsnavn":"Testbedrift AS",
                    "kontaktperson":{"navn":"Kontakt Person","epost":"kontakt@testbedrift.no","telefonnummer":"12345678"},
                    "beliggenhetsadresse":"Testveien 1"
                  },
                  "ansatt":{"fnr":"12345678910","navn":"Ansatt Navn"},
                  "ekspert":$ekspert,
                  "behovForBistand":{
                    "begrunnelse":"Begrunnelse",
                    "behov":"Behov",
                    "estimertKostnad":"9000",
                    "timer":"12",
                    "tilrettelegging":"Tilrettelegging",
                    "startdato":"2026-12-01"
                  },
                  "nav":{"kontaktperson":"Veileder Navn"},
                  "opprettetAv":"42",
                  "opprettetTidspunkt":"2026-11-20T08:00:00Z"$statusFelt
                }
            """
        }

        private val lagredeFormer = listOf(
            "fritekst uten status" to soknadJson(ekspertMedFritekst),
            "lister uten status" to soknadJson(ekspertMedLister),
            "lister med orgnr uten status" to soknadJson(ekspertMedOrgnr),
            "lister med orgnr og status godkjent" to soknadJson(ekspertMedOrgnr, "godkjent"),
            "fritekst med status avlyst" to soknadJson(ekspertMedFritekst, "avlyst"),
        )
    }
}
