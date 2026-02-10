package no.nav.ekspertbistand

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.utils.io.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.ereg.configureEregApiV1
import no.nav.ekspertbistand.event.configureEventHandlers
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.internal.configureInternal
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.projections.configureProjectionBuilders
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.norg.NorgKlient
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.UtkastTable
import no.nav.ekspertbistand.soknad.configureSoknadApiV1
import no.nav.ekspertbistand.tilsagndata.configureTilsagnDataApiV1
import no.nav.ekspertbistand.tilsagndata.insertTilsagndata
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*


fun main() {
    val testDb = TestDatabase()
    val mockAltinnTilgangerServer = HttpClient(MockEngine {
        respond(
            content = ByteReadChannel(altinnTilgangerResponse),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    })
    val mockEregServer = HttpClient(MockEngine {
        respond(
            content = ByteReadChannel(eregResponse),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    })
    val mockAltinnTilgangerClient = AltinnTilgangerClient(
        defaultHttpClient = mockAltinnTilgangerServer,
        tokenExchanger = successTokenXTokenExchanger
    )
    val mockEregClient = EregClient(defaultHttpClient = mockEregServer)
    val mockDokgenClient = DokgenClient(
        defaultHttpClient = HttpClient(MockEngine { request ->
            val isHtml = request.url.encodedPath.endsWith("/create-html")
            if (isHtml) {
                respond(
                    content = "<p>Mock tilskuddsbrev HTML</p>",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Text.Html.toString()
                    )
                )
            } else {
                respond(
                    content = "%PDF-mock".toByteArray(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Pdf.toString()
                    )
                )
            }
        }),
    )
    val mockDokArkivClient = DokArkivClient(
        azureAdTokenProvider = successAzureAdTokenProvider,
        defaultHttpClient = HttpClient(MockEngine {
            respond(
                content = ByteReadChannel(journalpostResponse),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        })
    )

    testDb.cleanMigrate()
    val godkjentSoknadId = UUID.fromString("f8f48c1f-9a5c-4a75-9d1a-2fb0a3a2eaa1")
    val avlystSoknadId = UUID.fromString("2f3f8f6d-4f7e-4a6b-bb32-7b44c0b3f589")
    val godkjentTilsagnData = TilsagnData(
        tilsagnNummer = TilsagnData.TilsagnNummer(
            aar = 2024,
            loepenrSak = 123,
            loepenrTilsagn = 1
        ),
        tilsagnDato = "2024-08-12",
        periode = TilsagnData.Periode(
            fraDato = "2024-09-01",
            tilDato = "2024-12-31"
        ),
        tiltakKode = "EKS",
        tiltakNavn = "Ekspertbistand",
        administrasjonKode = "NAV",
        refusjonfristDato = "2025-01-31",
        tiltakArrangor = TilsagnData.TiltakArrangor(
            arbgiverNavn = "Eksempel Bedrift AS",
            landKode = "NO",
            postAdresse = "Testveien 1",
            postNummer = "0557",
            postSted = "Oslo",
            orgNummerMorselskap = 123456789,
            orgNummer = 123456780,
            kontoNummer = "1234.56.78901",
            maalform = "B",
        ),
        totaltTilskuddbelop = 150000,
        valutaKode = "NOK",
        tilskuddListe = listOf(
            TilsagnData.Tilskudd(
                tilskuddType = "Tilskudd",
                tilskuddBelop = 120000,
                visTilskuddProsent = false,
                tilskuddProsent = null
            ),
            TilsagnData.Tilskudd(
                tilskuddType = "Administrasjon",
                tilskuddBelop = 30000,
                visTilskuddProsent = true,
                tilskuddProsent = 20.0
            )
        ),
        deltaker = TilsagnData.Deltaker(
            fodselsnr = "01020312345",
            fornavn = "Ola",
            etternavn = "Nordmann",
            landKode = "NO",
            postAdresse = "Deltakergata 2",
            postNummer = "0155",
            postSted = "Oslo",
        ),
        antallDeltakere = 1,
        antallTimeverk = 80,
        navEnhet = TilsagnData.NavEnhet(
            navKontor = "0315",
            navKontorNavn = "Nav Oslo",
            postAdresse = "Navgata 1",
            postNummer = "0101",
            postSted = "Oslo",
            telefon = "55553333",
            faks = null,
        ),
        beslutter = TilsagnData.Person(
            fornavn = "Kari",
            etternavn = "Saksen",
        ),
        saksbehandler = TilsagnData.Person(
            fornavn = "Per",
            etternavn = "Handler",
        ),
        kommentar = "Mock-tilsagn for local testing."
    )
    transaction(testDb.config.jdbcDatabase) {
        SoknadTable.insert {
            it[id] = godkjentSoknadId
            it[virksomhetsnummer] = "123456780"
            it[virksomhetsnavn] = "Eksempel Bedrift AS Avd. Oslo"
            it[beliggenhetsadresse] = "Testveien 1, 0557 Oslo"
            it[opprettetAv] = "42"

            it[kontaktpersonNavn] = "Kontaktperson NN"
            it[kontaktpersonEpost] = "kontaktperson@bedrift.no"
            it[kontaktpersonTelefon] = "415199999"
            it[ansattFnr] = "12058512345"
            it[ansattNavn] = "Asnatt NN"
            it[ekspertNavn] = "Ekspert NN"
            it[ekspertVirksomhet] = "ErgoConsult AS"
            it[ekspertKompetanse] = "Ergoterapeut, autorisasjon HPR 1337"
            it[behovForBistand] = "Arbeidsplassvurdering og ergonomisk veiledning"
            it[behovForBistandBegrunnelse] = "Langvarig skulderplage med 50% sykefravær"
            it[behovForBistandEstimertKostnad] = "9999"
            it[behovForBistandTimer] = "16"
            it[behovForBistandTilrettelegging] = "Høydejustert bord testet, noe bedring"
            it[behovForBistandStartdato] = CurrentDate

            it[navKontaktPerson] = "Navkontaktperson NN"
            it[status] = "godkjent"
        }
        SoknadTable.insert {
            it[id] = avlystSoknadId
            it[virksomhetsnummer] = "123456780"
            it[virksomhetsnavn] = "Eksempel Bedrift AS Avd. Oslo"
            it[opprettetAv] = "42"
            it[beliggenhetsadresse] = "Testveien 1, 0557 Oslo"

            it[kontaktpersonNavn] = "Kontaktperson NN"
            it[kontaktpersonEpost] = "kontaktperson@bedrift.no"
            it[kontaktpersonTelefon] = "415199999"
            it[ansattFnr] = "12058512345"
            it[ansattNavn] = "Asnatt NN"
            it[ekspertNavn] = "Ekspert NN"
            it[ekspertVirksomhet] = "ErgoConsult AS"
            it[ekspertKompetanse] = "Ergoterapeut, autorisasjon HPR 1337"
            it[behovForBistand] = "Arbeidsplassvurdering og ergonomisk veiledning"
            it[behovForBistandBegrunnelse] = "Langvarig skulderplage med 50% sykefravær"
            it[behovForBistandEstimertKostnad] = "9999"
            it[behovForBistandTimer] = "16"
            it[behovForBistandTilrettelegging] = "Høydejustert bord testet, noe bedring"
            it[behovForBistandStartdato] = CurrentDate

            it[navKontaktPerson] = "Navkontaktperson NN"
            it[status] = "avlyst"
        }
        insertTilsagndata(godkjentSoknadId, godkjentTilsagnData)
        UtkastTable.insert {
            it[id] = UUID.randomUUID()
            it[virksomhetsnummer] = "123456780"
            it[virksomhetsnavn] = "Eksempel Bedrift AS Avd. Oslo"
            it[opprettetAv] = "42"

            it[kontaktpersonNavn] = "Kontaktperson NN"
            it[kontaktpersonEpost] = "kontaktperson@bedrift.no"
            it[kontaktpersonTelefon] = "415199999"
            it[ansattFnr] = "12058512345"
            it[ansattNavn] = "Asnatt NN"
            it[ekspertNavn] = "Ekspert NN"
            it[ekspertVirksomhet] = "ErgoConsult AS"
            it[ekspertKompetanse] = "Ergoterapeut, autorisasjon HPR 1337"
            it[behovForBistand] = "Arbeidsplassvurdering og ergonomisk veiledning"
            it[behovForBistandBegrunnelse] = "Langvarig skulderplage med 50% sykefravær"
            it[behovForBistandEstimertKostnad] = "9999"
            it[behovForBistandTimer] = "16"
            it[behovForBistandTilrettelegging] = "Høydejustert bord testet, noe bedring"
            it[behovForBistandStartdato] = CurrentDate

            it[navKontaktPerson] = "Navkontaktperson NN"
        }
    }

    ktorServer {
        dependencies {
            provide<Database> {
                testDb.config.jdbcDatabase
            }
            provide<TokenXTokenIntrospector> {
                MockTokenIntrospector {
                    if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                }
            }
            provide<HttpClient> { defaultHttpClient() }
            provide<AzureAdTokenProvider> {
                successAzureAdTokenProvider
            }
            provide {
                mockAltinnTilgangerClient
            }
            provide {
                mockEregClient
            }
            provide<EregService> { EregService(resolve()) }
            provide { mockDokgenClient }
            provide { mockDokArkivClient }
            provide(NorgKlient::class)
            provide(BehandlendeEnhetService::class)
            provide(PdlApiKlient::class)
            provide<ProdusentApiKlient> {
                ProdusentApiKlient(
                    azureAdTokenProvider = resolve<AzureAdTokenProvider>(),
                    defaultHttpClient = defaultHttpClient()
                )
            }
            provide<ArenaClient> {
                ArenaClient(
                    tokenProvider = resolve<AzureAdTokenProvider>(),
                    defaultHttpClient = defaultHttpClient()
                )
            }
        }
        configureServer()
        configureTokenXAuth()

        // application modules
        configureSoknadApiV1()
        configureOrganisasjonerApiV1()
        configureTilsagnDataApiV1()
        configureEregApiV1()

        // event manager and event handlers
        configureEventHandlers()

        configureProjectionBuilders()

        configureAppMetrics()

        // internal endpoints and lifecycle hooks
        configureInternal()
        registerShutdownListener()
    }
}

// language=JSON
const val eregResponse = """{
  "organisasjonsnummer": "123456780",
  "navn": { "sammensattnavn": "Eksempel Bedrift AS Avd. Oslo" },
  "organisasjonDetaljer": {
    "forretningsadresser": [
      {
        "adresselinje1": "Testveien 1",
        "postnummer": "0557",
        "poststed": "Oslo"
      }
    ]
  }
}"""

// language=JSON
const val journalpostResponse = """{
  "dokumenter": [
    {
      "dokumentInfoId": "1111"
    }
  ],
  "journalpostId": "2222",
  "journalpostferdigstilt": true
}"""

// language=JSON
const val altinnTilgangerResponse = """{
  "isError": false,
  "hierarki": [
    {
      "erSlettet": false,
      "orgnr": "123456789",
      "organisasjonsform": "AS",
      "navn": "Eksempel Bedrift AS",
      "underenheter": [
        {
          "erSlettet": false,
          "orgnr": "123456780",
          "organisasjonsform": "BEDR",
          "navn": "Eksempel Bedrift AS Avd. Oslo",
          "underenheter": [],
          "altinn3Tilganger": [
            "nav_tiltak_ekspertbistand"
          ],
          "altinn2Tilganger": []
        },
        {
          "erSlettet": false,
          "orgnr": "123456781",
          "organisasjonsform": "BEDR",
          "navn": "Eksempel Bedrift AS Avd. Bergen",
          "underenheter": [],
          "altinn3Tilganger": [
            "nav_tiltak_ekspertbistand"
          ],
          "altinn2Tilganger": []
        }
      ],
      "altinn3Tilganger": [
        "nav_tiltak_ekspertbistand"
      ],
      "altinn2Tilganger": []
    },
    {
      "erSlettet": false,
      "orgnr": "987654321",
      "organisasjonsform": "AS",
      "navn": "Testfirma Norge AS",
      "underenheter": [],
      "altinn3Tilganger": [
        "nav_tiltak_ekspertbistand"
      ],
      "altinn2Tilganger": []
    },
    {
      "erSlettet": false,
      "orgnr": "111222333",
      "organisasjonsform": "AS",
      "navn": "Demo Solutions AS",
      "underenheter": [],
      "altinn3Tilganger": [
        "nav_tiltak_ekspertbistand"
      ],
      "altinn2Tilganger": []
    },
    {
      "erSlettet": false,
      "orgnr": "444555666",
      "organisasjonsform": "AS",
      "navn": "Navn & Co AS",
      "underenheter": [],
      "altinn3Tilganger": [
        "nav_tiltak_ekspertbistand"
      ],
      "altinn2Tilganger": []
    }
  ],
  "orgNrTilTilganger": {
    "123456789": [
      "nav_tiltak_ekspertbistand"
    ],
    "123456780": [
      "nav_tiltak_ekspertbistand"
    ],
    "123456781": [
      "nav_tiltak_ekspertbistand"
    ],
    "987654321": [
      "nav_tiltak_ekspertbistand"
    ],
    "111222333": [
      "nav_tiltak_ekspertbistand"
    ],
    "444555666": [
      "nav_tiltak_ekspertbistand"
    ]
  },
  "tilgangTilOrgNr": {
    "nav_tiltak_ekspertbistand": [
      "123456789",
      "123456780",
      "123456781",
      "987654321",
      "111222333",
      "444555666"
    ]
  }
}
"""
