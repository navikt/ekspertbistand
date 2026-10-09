package no.nav.ekspertbistand.mocks

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.entraproxy.EntraProxyClient

fun ApplicationTestBuilder.mockEntraProxy(
    responseProvider: (navIdent: String) -> String
) {
    externalServices {
        hosts(EntraProxyClient.ingress) {
            routing {
                get("${EntraProxyClient.API_PATH}/{navIdent}") {
                    val navIdent = call.parameters["navIdent"]!!
                    val response = responseProvider(navIdent)
                    call.respondText(response, contentType = io.ktor.http.ContentType.Application.Json)
                }
            }
        }
    }
}

fun ApplicationTestBuilder.mockEntraProxyAnsatt(
    responseProvider: (navIdent: String) -> String
) {
    externalServices {
        hosts(EntraProxyClient.ingress) {
            routing {
                get("${EntraProxyClient.ANSATT_API_PATH}/{navIdent}") {
                    val navIdent = call.parameters["navIdent"]!!
                    val response = responseProvider(navIdent)
                    call.respondText(response, contentType = io.ktor.http.ContentType.Application.Json)
                }
            }
        }
    }
}

fun ApplicationTestBuilder.mockEntraProxyGrupper(
    responseProvider: (navIdent: String) -> String
) {
    externalServices {
        hosts(EntraProxyClient.ingress) {
            routing {
                get("${EntraProxyClient.GRUPPER_API_PATH}/{navIdent}") {
                    val navIdent = call.parameters["navIdent"]!!
                    val response = responseProvider(navIdent)
                    call.respondText(response, contentType = io.ktor.http.ContentType.Application.Json)
                }
            }
        }
    }
}

fun testAnsattJson(
    navIdent: String,
    navn: String = "Tore Tang",
    enhetnummer: String = "1234",
) = """
    {
        "navIdent": "$navIdent",
        "visningNavn": "$navn",
        "epost": "${navIdent.lowercase()}@nav.no",
        "enhet": { "enhetnummer": "$enhetnummer", "navn": "Nav Avdeling Sydpolen" },
        "tIdent": "T${navIdent.drop(1)}"
    }
""".trimIndent()

fun ApplicationTestBuilder.mockEntraProxyFull(
    ansattProvider: (navIdent: String) -> String = { testAnsattJson(it) },
    enheterProvider: (navIdent: String) -> String = { """[{ "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" }]""" },
    grupperProvider: (navIdent: String) -> String = { "[]" },
) {
    externalServices {
        hosts(EntraProxyClient.ingress) {
            routing {
                get("${EntraProxyClient.GRUPPER_API_PATH}/{navIdent}") {
                    val navIdent = call.parameters["navIdent"]!!
                    call.respondText(grupperProvider(navIdent), contentType = io.ktor.http.ContentType.Application.Json)
                }
                get("${EntraProxyClient.ANSATT_API_PATH}/{navIdent}") {
                    val navIdent = call.parameters["navIdent"]!!
                    call.respondText(ansattProvider(navIdent), contentType = io.ktor.http.ContentType.Application.Json)
                }
                get("${EntraProxyClient.API_PATH}/{navIdent}") {
                    val navIdent = call.parameters["navIdent"]!!
                    call.respondText(enheterProvider(navIdent), contentType = io.ktor.http.ContentType.Application.Json)
                }
            }
        }
    }
}

