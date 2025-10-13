package no.nav.ekspertbistand

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.skjema.SkjemaTable
import no.nav.ekspertbistand.skjema.UtkastTable
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.UUID


suspend fun main() {
    val dbConfig = TestDatabase().config
    suspendTransaction(dbConfig.database) {
        SkjemaTable.insert {
            it[id] = UUID.randomUUID()
            it[virksomhetsnummer] = "1337"
            it[opprettetAv] = "42"

            it[kontaktpersonNavn] = ""
            it[kontaktpersonEpost] = ""
            it[kontaktpersonTelefon] = ""
            it[ansattFodselsnummer] = ""
            it[ansattNavn] = ""
            it[ekspertNavn] = ""
            it[ekspertVirksomhet] = ""
            it[ekspertKompetanse] = ""
            it[ekspertProblemstilling] = ""
            it[tiltakForTilrettelegging] = ""
            it[bestillingKostnad] = ""
            it[bestillingStartDato] = ""
            it[navKontakt] = ""
        }
        UtkastTable.insert {
            it[virksomhetsnummer] = "1337"
            it[opprettetAv] = "42"

            it[kontaktpersonNavn] = ""
            it[kontaktpersonEpost] = ""
            it[kontaktpersonTelefon] = ""
            it[ansattFodselsnummer] = ""
            it[ansattNavn] = ""
            it[ekspertNavn] = ""
            it[ekspertVirksomhet] = ""
            it[ekspertKompetanse] = ""
            it[ekspertProblemstilling] = ""
            it[tiltakForTilrettelegging] = ""
            it[bestillingKostnad] = ""
            it[bestillingStartDato] = ""
            it[navKontakt] = ""
        }
    }

    val mockAltinnTilgangerServer = HttpClient(MockEngine { request ->
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
    val mockAltinnTilgangerClient = AltinnTilgangerClient(
        httpClient = mockAltinnTilgangerServer,
        authClient = object : TokenExchanger {
            override suspend fun exchange(
                target: String,
                userToken: String
            ): TokenResponse = TokenResponse.Success("dummy", 3600)
        }
    )
    ktorServer {
        provide<TokenIntrospector> {
            MockTokenIntrospector {
                if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
            }
        }
        provide<DbConfig> {
            dbConfig
        }
        provide<AltinnTilgangerClient> {
            mockAltinnTilgangerClient
        }
    }.start(wait = true)
}

// language=JSON
const val altinnTilgangerResponse = """{
  "isError": false,
  "hierarki": [
    {
      "orgnr": "313199770",
      "altinn3Tilganger": [],
      "altinn2Tilganger": [
        "5384:1"
      ],
      "underenheter": [
        {
          "orgnr": "211511052",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [
            "5384:1"
          ],
          "underenheter": [],
          "navn": "ALLSIDIG UTMERKET TIGER AS",
          "organisasjonsform": "BEDR",
          "erSlettet": false
        },
        {
          "orgnr": "1337",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [
            "5384:1"
          ],
          "underenheter": [],
          "navn": "LOLWUT LTD",
          "organisasjonsform": "BEDR",
          "erSlettet": false
        }
      ],
      "navn": "ALLSIDIG UTMERKET TIGER AS",
      "organisasjonsform": "AS",
      "erSlettet": false
    },
    {
      "orgnr": "310276111",
      "altinn3Tilganger": [],
      "altinn2Tilganger": [
        "5384:1"
      ],
      "underenheter": [
        {
          "orgnr": "311601881",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [
            "5384:1"
          ],
          "underenheter": [],
          "navn": "BEGEISTRET VISSEN TIGER AS",
          "organisasjonsform": "BEDR",
          "erSlettet": false
        }
      ],
      "navn": "BEGEISTRET VISSEN TIGER AS",
      "organisasjonsform": "AS",
      "erSlettet": false
    },
    {
      "orgnr": "314279913",
      "altinn3Tilganger": [],
      "altinn2Tilganger": [
        "5384:1"
      ],
      "underenheter": [
        {
          "orgnr": "314569083",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [
            "5384:1"
          ],
          "underenheter": [],
          "navn": "DEMOKRATISK LILLA TIGER AS",
          "organisasjonsform": "BEDR",
          "erSlettet": false
        }
      ],
      "navn": "DEMOKRATISK LILLA TIGER AS",
      "organisasjonsform": "AS",
      "erSlettet": false
    },
    {
      "orgnr": "310614777",
      "altinn3Tilganger": [],
      "altinn2Tilganger": [
        "5384:1"
      ],
      "underenheter": [
        {
          "orgnr": "315784220",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [
            "5384:1"
          ],
          "underenheter": [],
          "navn": "DYNAMISK AVANSERT TIGER AS",
          "organisasjonsform": "BEDR",
          "erSlettet": false
        }
      ],
      "navn": "DYNAMISK AVANSERT TIGER AS",
      "organisasjonsform": "AS",
      "erSlettet": false
    },
    {
      "orgnr": "312593351",
      "altinn3Tilganger": [],
      "altinn2Tilganger": [
        "5384:1"
      ],
      "underenheter": [
        {
          "orgnr": "315291720",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [
            "5384:1"
          ],
          "underenheter": [],
          "navn": "FREDFULL URIMELIG TIGER AS",
          "organisasjonsform": "BEDR",
          "erSlettet": false
        }
      ],
      "navn": "FREDFULL URIMELIG TIGER AS",
      "organisasjonsform": "AS",
      "erSlettet": false
    },
    {
      "orgnr": "311750453",
      "altinn3Tilganger": [],
      "altinn2Tilganger": [
        "5384:1"
      ],
      "underenheter": [],
      "navn": "REDELIG UNGT TIGER AS",
      "organisasjonsform": "AS",
      "erSlettet": false
    },
    {
      "orgnr": "310740071",
      "altinn3Tilganger": [],
      "altinn2Tilganger": [
        "5384:1"
      ],
      "underenheter": [
        {
          "orgnr": "315801729",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [
            "5384:1"
          ],
          "underenheter": [],
          "navn": "TRÅDLØS SOLID TIGER AS",
          "organisasjonsform": "BEDR",
          "erSlettet": false
        }
      ],
      "navn": "TRÅDLØS SOLID TIGER AS",
      "organisasjonsform": "AS",
      "erSlettet": false
    },
    {
      "orgnr": "313990265",
      "altinn3Tilganger": [],
      "altinn2Tilganger": [
        "5384:1"
      ],
      "underenheter": [
        {
          "orgnr": "315538890",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [
            "5384:1"
          ],
          "underenheter": [],
          "navn": "ARITMETISK UTGÅTT TIGER AS",
          "organisasjonsform": "BEDR",
          "erSlettet": false
        }
      ],
      "navn": "ARITMETISK UTGÅTT TIGER AS",
      "organisasjonsform": "AS",
      "erSlettet": false
    }
  ],
  "orgNrTilTilganger": {
    "211511052": [
      "5384:1"
    ],
    "1337": [
      "5384:1"
    ],
    "310276111": [
      "5384:1"
    ],
    "310614777": [
      "5384:1"
    ],
    "310740071": [
      "5384:1"
    ],
    "311601881": [
      "5384:1"
    ],
    "311750453": [
      "5384:1"
    ],
    "312593351": [
      "5384:1"
    ],
    "313199770": [
      "5384:1"
    ],
    "313990265": [
      "5384:1"
    ],
    "314279913": [
      "5384:1"
    ],
    "314569083": [
      "5384:1"
    ],
    "315291720": [
      "5384:1"
    ],
    "315538890": [
      "5384:1"
    ],
    "315784220": [
      "5384:1"
    ],
    "315801729": [
      "5384:1"
    ]
  },
  "tilgangTilOrgNr": {
    "5384:1": [
      "211511052",
      "1337",
      "310276111",
      "310614777",
      "310740071",
      "311601881",
      "311750453",
      "312593351",
      "313199770",
      "313990265",
      "314279913",
      "314569083",
      "315291720",
      "315538890",
      "315784220",
      "315801729"
    ]
  }
}"""
