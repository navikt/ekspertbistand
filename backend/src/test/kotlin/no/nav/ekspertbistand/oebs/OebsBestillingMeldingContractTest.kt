@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.oebs.integration.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Kontraktstest mot faktiske eksempelmeldinger fra Team VALP (mottatt 2026-09-17), verifiserer at
 * vår lokale speiling serialiseres/deserialiseres nøyaktig som VALP sitt wire-format.
 *
 * Det viktige VALP-eksemplene avdekker:
 * - Nestede sealed classes bruker **fullkvalifiserte** `type`-diskriminatorer
 *   (`no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt` osv.), ikke korte navn.
 * - [OkonomiPart.part] serialiseres som eget felt ved siden av subklassefeltene (baseklassens
 *   konstruktør-property) — derfor har `NavAnsatt` både `part` og `navIdent`.
 * - Toppnivå-envelopen bruker korte diskriminatorer (`BESTILLING`/`FAKTURA`/…).
 *
 * Vi **produserer** bestillingsmeldinger, så denne testen dekker encode-retningen. Deserialisering av
 * statusmeldinger (konsum-retningen) ligger i `OebsStatusMeldingTest`.
 *
 * Merk: `bestilling`-eksempelet fra VALP kommer fra en annen kilde og bruker enum-verdier
 * (`TILTAK_DRIFTSTILSKUDD`/`ARBEIDSMARKEDSOPPLAERING`) som vår restriktive modell ikke har. Vi sender
 * kun `TILTAK_EKSPERTBISTAND`/`EKSPERTBISTAND`, så eksempelet er justert til våre enum-verdier — resten
 * av wire-formatet (feltnavn, diskriminatorer, `part`, `periode`, `Instant`) er uendret fra VALP.
 */
class OebsBestillingMeldingContractTest {

    private val json = OebsBestillingMelding.json

    private fun assertJsonEquals(expected: String, actual: String) =
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual))

    @Test
    fun `bestilling serialiseres identisk med VALP-kontrakten`() {
        val melding = OebsBestillingMelding.Bestilling(
            OpprettBestilling(
                bestillingsnummer = "A-2026/10315-1",
                tilskuddstype = Tilskuddstype.TILTAK_EKSPERTBISTAND,
                tiltakskode = Tiltakskode.EKSPERTBISTAND,
                arrangor = OpprettBestilling.Arrangor.Norsk(Organisasjonsnummer("925236594")),
                kostnadssted = NavEnhetNummer("0315"),
                avtalenummer = null,
                belop = 566,
                periode = Periode(LocalDate.parse("2026-04-09"), LocalDate.parse("2026-04-24")),
                behandletAv = OkonomiPart.NavAnsatt("Z993433"),
                behandletTidspunkt = Instant.parse("2026-06-26T11:48:46.703448Z"),
                besluttetAv = OkonomiPart.NavAnsatt("Z990079"),
                besluttetTidspunkt = Instant.parse("2026-09-16T08:27:08.067824Z"),
                valuta = Valuta.NOK,
            ),
        )

        val expected = """
            {
              "type": "BESTILLING",
              "payload": {
                "bestillingsnummer": "A-2026/10315-1",
                "tilskuddstype": "TILTAK_EKSPERTBISTAND",
                "tiltakskode": "EKSPERTBISTAND",
                "arrangor": {
                  "type": "no.nav.tiltak.okonomi.OpprettBestilling.Arrangor.Norsk",
                  "organisasjonsnummer": "925236594"
                },
                "kostnadssted": "0315",
                "avtalenummer": null,
                "belop": 566,
                "periode": { "start": "2026-04-09", "slutt": "2026-04-24" },
                "behandletAv": {
                  "type": "no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt",
                  "part": "Z993433",
                  "navIdent": "Z993433"
                },
                "behandletTidspunkt": "2026-06-26T11:48:46.703448Z",
                "besluttetAv": {
                  "type": "no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt",
                  "part": "Z990079",
                  "navIdent": "Z990079"
                },
                "besluttetTidspunkt": "2026-09-16T08:27:08.067824Z",
                "valuta": "NOK"
              }
            }
        """.trimIndent()

        assertJsonEquals(expected, json.encodeToString(melding as OebsBestillingMelding))
    }

    @Test
    fun `annullering serialiseres identisk med VALP-kontrakten`() {
        val melding = OebsBestillingMelding.Annullering(
            AnnullerBestilling(
                bestillingsnummer = "A-2026/19891-7",
                behandletAv = OkonomiPart.NavAnsatt("Z990079"),
                behandletTidspunkt = Instant.parse("2026-09-16T09:37:44.623036Z"),
                besluttetAv = OkonomiPart.NavAnsatt("L164122"),
                besluttetTidspunkt = Instant.parse("2026-09-16T09:37:59.523500Z"),
            ),
        )

        val expected = """
            {
              "type": "ANNULLERING",
              "payload": {
                "bestillingsnummer": "A-2026/19891-7",
                "behandletAv": {
                  "type": "no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt",
                  "part": "Z990079",
                  "navIdent": "Z990079"
                },
                "behandletTidspunkt": "2026-09-16T09:37:44.623036Z",
                "besluttetAv": {
                  "type": "no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt",
                  "part": "L164122",
                  "navIdent": "L164122"
                },
                "besluttetTidspunkt": "2026-09-16T09:37:59.523500Z"
              }
            }
        """.trimIndent()

        assertJsonEquals(expected, json.encodeToString(melding as OebsBestillingMelding))
    }

    @Test
    fun `faktura serialiseres identisk med VALP-kontrakten`() {
        val melding = OebsBestillingMelding.Faktura(
            OpprettFaktura(
                fakturanummer = "A-2026/19891-6-1",
                bestillingsnummer = "A-2026/19891-6",
                betalingsinformasjon = OpprettFaktura.Betalingsinformasjon.BBan(
                    kontonummer = Kontonummer("10002427740"),
                    kid = null,
                ),
                belop = 5001,
                periode = Periode(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-07-01")),
                behandletAv = OkonomiPart.NavAnsatt("Z990079"),
                behandletTidspunkt = Instant.parse("2026-09-16T07:28:35.282349Z"),
                besluttetAv = OkonomiPart.NavAnsatt("L164122"),
                besluttetTidspunkt = Instant.parse("2026-09-16T07:41:12.060013Z"),
                gjorOppBestilling = false,
                beskrivelse = "Tiltakstype: Arbeidsforberedende trening\nPeriode: 01.06.2026 - 30.06.2026\nTilsagnsnummer: A-2026/19891-6",
                valuta = Valuta.NOK,
            ),
        )

        val expected = """
            {
              "type": "FAKTURA",
              "payload": {
                "fakturanummer": "A-2026/19891-6-1",
                "bestillingsnummer": "A-2026/19891-6",
                "betalingsinformasjon": {
                  "type": "no.nav.tiltak.okonomi.OpprettFaktura.Betalingsinformasjon.BBan",
                  "kontonummer": "10002427740",
                  "kid": null
                },
                "belop": 5001,
                "periode": { "start": "2026-06-01", "slutt": "2026-07-01" },
                "behandletAv": {
                  "type": "no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt",
                  "part": "Z990079",
                  "navIdent": "Z990079"
                },
                "behandletTidspunkt": "2026-09-16T07:28:35.282349Z",
                "besluttetAv": {
                  "type": "no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt",
                  "part": "L164122",
                  "navIdent": "L164122"
                },
                "besluttetTidspunkt": "2026-09-16T07:41:12.060013Z",
                "gjorOppBestilling": false,
                "beskrivelse": "Tiltakstype: Arbeidsforberedende trening\nPeriode: 01.06.2026 - 30.06.2026\nTilsagnsnummer: A-2026/19891-6",
                "valuta": "NOK"
              }
            }
        """.trimIndent()

        assertJsonEquals(expected, json.encodeToString(melding as OebsBestillingMelding))
    }

    @Test
    fun `gjor-opp-bestilling serialiseres identisk med VALP-kontrakten`() {
        val melding = OebsBestillingMelding.GjorOppBestilling(
            GjorOppBestilling(
                bestillingsnummer = "A-2025/15920-1",
                behandletAv = OkonomiPart.NavAnsatt("B123456"),
                behandletTidspunkt = Instant.parse("2026-07-03T07:14:48.433329Z"),
                besluttetAv = OkonomiPart.NavAnsatt("L164122"),
                besluttetTidspunkt = Instant.parse("2026-07-03T07:21:04.840257Z"),
            ),
        )

        val expected = """
            {
              "type": "GJOR_OPP_BESTILLING",
              "payload": {
                "bestillingsnummer": "A-2025/15920-1",
                "behandletAv": {
                  "type": "no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt",
                  "part": "B123456",
                  "navIdent": "B123456"
                },
                "behandletTidspunkt": "2026-07-03T07:14:48.433329Z",
                "besluttetAv": {
                  "type": "no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt",
                  "part": "L164122",
                  "navIdent": "L164122"
                },
                "besluttetTidspunkt": "2026-07-03T07:21:04.840257Z"
              }
            }
        """.trimIndent()

        assertJsonEquals(expected, json.encodeToString(melding as OebsBestillingMelding))
    }
}
