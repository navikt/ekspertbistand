package no.nav.ekspertbistand

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.di.resolve
import io.ktor.utils.io.*
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.ereg.configureEregApiV1
import no.nav.ekspertbistand.event.configureEventHandlers
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.internal.configureInternal
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
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
    }) {
        install(ContentNegotiation) {
            json()
        }
    }
    val mockEregServer = HttpClient(MockEngine {
        respond(
            content = ByteReadChannel(eregResponse),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }) {
        install(ContentNegotiation) {
            json()
        }
    }
    val mockAltinnTilgangerClient = AltinnTilgangerClient(
        httpClient = mockAltinnTilgangerServer,
        authClient = object : TokenExchanger {
            override suspend fun exchange(
                target: String,
                userToken: String
            ): TokenResponse = TokenResponse.Success("dummy", 3600)
        }
    )
    val mockEregClient = EregClient(httpClient = mockEregServer)
    val mockDokgenClient = DokgenClient(
        httpClient = HttpClient(MockEngine {
            respond(
                content = "%PDF-mock".toByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
            )
        }) {
            install(ContentNegotiation) {
                json()
            }
        },
        baseUrl = "http://dokgen"
    )
    val mockDokArkivClient = DokArkivClient(
        authClient = object : TokenProvider {
            override suspend fun token(target: String): TokenResponse = TokenResponse.Success("token", 3600)
        },
        httpClient = HttpClient(MockEngine {
            respond(
                content = ByteReadChannel(journalpostResponse),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }) {
            install(ContentNegotiation) { json() }
        }
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
            provide<TokenIntrospector>(IdentityProvider.TOKEN_X.alias) {
                MockTokenIntrospector {
                    if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                }
            }
            provide<TokenProvider>(IdentityProvider.AZURE_AD.alias) {
                object : TokenProvider {
                    override suspend fun token(target: String): TokenResponse {
                        return TokenResponse.Success(
                            accessToken = "faketoken",
                            expiresInSeconds = 3600
                        )
                    }
                }
            }
            provide {
                mockAltinnTilgangerClient
            }
            provide {
                mockEregClient
            }
            provide { mockDokgenClient }
            provide { mockDokArkivClient }
            provide<IdempotencyGuard> { IdempotencyGuard(resolve<Database>()) }
            provide<ProdusentApiKlient> {
                ProdusentApiKlient(
                    tokenProvider = resolve<TokenProvider>(IdentityProvider.AZURE_AD.alias),
                    httpClient = defaultHttpClient(customizeMetrics = {
                        clientName = "notifikasjon.produsent.api.klient"
                    }) {
                        install(HttpTimeout) {
                            requestTimeoutMillis = 5_000
                        }
                    }
                )
            }
            provide<ArenaClient> {
                ArenaClient(
                    tokenProvider = resolve<TokenProvider>(IdentityProvider.AZURE_AD.alias),
                    httpClient = defaultHttpClient(customizeMetrics = {
                        clientName = "arena-api.client"
                    }) {
                        install(HttpTimeout) {
                            requestTimeoutMillis = 15_000
                        }
                    }
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
