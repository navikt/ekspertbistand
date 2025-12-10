package no.nav.ekspertbistand.pdl

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.*
import io.ktor.client.request.*
import no.nav.ekspertbistand.infrastruktur.TokenExchanger
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.pdl.graphql.generated.HentGeografiskTilknytning
import no.nav.ekspertbistand.pdl.graphql.generated.HentPerson
import no.nav.ekspertbistand.pdl.graphql.generated.hentgeografisktilknytning.GeografiskTilknytning
import no.nav.ekspertbistand.pdl.graphql.generated.hentperson.Person
import java.net.URI

// https://behandlingskatalog.intern.nav.no/process/purpose/SYFO/de1355ba-13b8-498d-8cdc-74463ba1a514
private const val behandlingsNummer = "B591"

class PdlApiKlient(
    private val subjectToken: String,
    private val tokenExchanger: TokenExchanger, // bruk tokenX OBO
    private val httpClient: HttpClient
) {
    private val client = GraphQLKtorClient(
        url = URI("$baseUrl/graphql").toURL(),
        httpClient = httpClient
    )

    private suspend fun token(): String {
        val target = "${targetCluster}.pdl.pdl-api"
        return tokenExchanger.exchange(
            target = target,
            userToken = subjectToken
        ).fold(
            onSuccess = { it.accessToken },
            onError = { throw Exception("Feil ved henting av token for pdl api: ${it.error.errorDescription}") }
        )
    }


    suspend fun hentAdressebeskyttelse(fnr: String): Person {
        val token = token()
        val response = client.execute(
            HentPerson(
                HentPerson.Variables(
                    ident = fnr,
                )
            )
        ) {
            bearerAuth(token)
            header("Behandlingsnummer", behandlingsNummer)
        }

        return when (val person = response.data?.hentPerson) {
            null -> throw PdlClientException("Uventet feil ved henting av person: person er null. errors: ${response.errors?.map { "${it.message}\n" }}")
            else -> person
        }
    }

    suspend fun hentGeografiskTilknytning(fnr: String): GeografiskTilknytning {
        val token = token()
        val response = client.execute(
            HentGeografiskTilknytning(
                HentGeografiskTilknytning.Variables(
                    ident = fnr,
                )
            )
        ) {
            bearerAuth(token)
            header("Behandlingsnummer", behandlingsNummer)
        }

        return when (val tilknytning = response.data?.hentGeografiskTilknytning) {
            null -> throw PdlClientException("Uventet feil ved henting av geografisk tilknytning: tilknytning er null. errors: ${response.errors?.map { "${it.message}\n" }}")
            else -> tilknytning
        }
    }

    companion object {
        val baseUrl = basedOnEnv(
            prod = { "https://pdl-api.prod-fss-pub.nais.io" },
            other = { "https://pdl-api.dev-fss-pub.nais.io" }
        )

        private val targetCluster = basedOnEnv(
            prod = { "prod-fss" },
            other = { "dev-fss" }
        )
    }
}

class PdlClientException(message: String) : Exception(message)
