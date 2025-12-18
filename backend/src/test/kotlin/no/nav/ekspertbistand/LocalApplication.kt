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
import no.nav.ekspertbistand.ereg.configureEregApiV1
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.event.configureEventHandlers
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.internal.configureInternal
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.norg.NorgKlient
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.skjema.SkjemaTable
import no.nav.ekspertbistand.skjema.UtkastTable
import no.nav.ekspertbistand.skjema.configureSkjemaApiV1
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
        defaultHttpClient = HttpClient(MockEngine {
            respond(
                content = "%PDF-mock".toByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
            )
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
    transaction(testDb.config.jdbcDatabase) {
        SkjemaTable.insert {
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
            provide<AzureAdTokenProvider> {
                successAzureAdTokenProvider
            }
            provide {
                mockAltinnTilgangerClient
            }
            provide {
                mockEregClient
            }
            provide { mockDokgenClient }
            provide { mockDokArkivClient }
            provide(NorgKlient::class)
            provide(BehandlendeEnhetService::class)
            provide(PdlApiKlient::class)
            provide<IdempotencyGuard> { IdempotencyGuard(resolve<Database>()) }
            provide<ProdusentApiKlient> {
                ProdusentApiKlient(
                    tokenProvider = resolve<AzureAdTokenProvider>(),
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
        configureSkjemaApiV1()
        configureOrganisasjonerApiV1()
        configureEregApiV1()

        // event manager and event handlers
        configureEventHandlers()

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
