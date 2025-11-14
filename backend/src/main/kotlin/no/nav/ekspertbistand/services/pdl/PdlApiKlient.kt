package no.nav.ekspertbistand.services.pdl

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.*
import io.ktor.client.request.bearerAuth
import no.nav.ekspertbistand.infrastruktur.NaisEnvironment
import no.nav.ekspertbistand.infrastruktur.TokenExchanger
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenXPrincipal
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.services.pdl.graphql.generated.HentGeografiskTilknytning
import no.nav.ekspertbistand.services.pdl.graphql.generated.HentPerson
import no.nav.ekspertbistand.services.pdl.graphql.generated.hentgeografisktilknytning.GeografiskTilknytning
import no.nav.ekspertbistand.services.pdl.graphql.generated.hentperson.Person
import org.jetbrains.exposed.v1.jdbc.Except
import java.net.URI

private val pdlBaseUrl = basedOnEnv(
    prod = { "https://pdl-api.prod-fss-pub.nais.io/graphql" },
    dev = { "https://pdl-api.dev-fss-pub.nais.io/graphql" },
    other = { "Dette bør kanskje mockes?" }
)

private val targetCluster = basedOnEnv(
    prod = { "prod-fss" },
    dev = { "dev-fss" },
    other = { "Dette bør kanskje mockes?" }
)

private const val behandlingsNummer =
    "B591" // https://behandlingskatalog.intern.nav.no/process/purpose/SYFO/de1355ba-13b8-498d-8cdc-74463ba1a514


class PdlApiKlient(
    private val principal: TokenXPrincipal,
    private val tokenExchanger: TokenExchanger, // bruk tokenX OBO
    private val httpClient: HttpClient
) {
    private val client = GraphQLKtorClient(
        url = URI(pdlBaseUrl).toURL(),
        httpClient = httpClient
    )

    private suspend fun token(): String {
        val target = "${targetCluster}.pdl.pdl-api"
        return tokenExchanger.exchange(
            target = target,
            userToken = principal.subjectToken
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
        }

        return when (val tilknytning = response.data?.hentGeografiskTilknytning) {
            null -> throw PdlClientException("Uventet feil ved henting av geografisk tilknytning: tilknytning er null. errors: ${response.errors?.map { "${it.message}\n" }}")
            else -> tilknytning
        }
    }
}


class PdlClientException(message: String) : Exception(message)

