package no.nav.ekspertbistand.pdl

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.ktor.client.*
import io.ktor.client.request.*
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.pdl.graphql.generated.HentGeografiskTilknytning
import no.nav.ekspertbistand.pdl.graphql.generated.HentPerson
import no.nav.ekspertbistand.pdl.graphql.generated.hentgeografisktilknytning.GeografiskTilknytning
import no.nav.ekspertbistand.pdl.graphql.generated.hentperson.Person
import java.net.URI

// https://behandlingskatalog.intern.nav.no/process/purpose/SYFO/de1355ba-13b8-498d-8cdc-74463ba1a514
private const val behandlingsNummer = "B591"

class PdlApiKlient(
    private val azureAdTokenProvider: AzureAdTokenProvider,
    defaultHttpClient: HttpClient
) {
    private val client = GraphQLKtorClient(
        url = URI("$baseUrl/graphql").toURL(),
        httpClient = defaultHttpClient.config {

        }
    )

    private suspend fun token(): String {
        return azureAdTokenProvider.token(
            target = targetAudience,
        ).fold(
            onSuccess = { it.accessToken },
            onError = { throw Exception("Feil ved henting av token for pdl api: ${it.error.errorDescription}") }
        )
    }


    suspend fun hentAdressebeskyttelse(fnr: String): Result<Person> {
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

        if (response.data !== null && response.data!!.hentPerson != null) {
            return Result.success(response.data!!.hentPerson!!)
        }

        return Result.failure(response.getErrors())
    }

    suspend fun hentGeografiskTilknytning(fnr: String): Result<GeografiskTilknytning> {
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

        if (response.data != null && response.data!!.hentGeografiskTilknytning != null) {
            return Result.success(response.data!!.hentGeografiskTilknytning!!)
        }

        return Result.failure(response.getErrors())
    }

    private fun <T> GraphQLClientResponse<T>.getErrors(): Exception {
        val error = errors?.let { errors ->
            val codes = errors.map {
                it.extensions?.get("code") as String? ?: it.message
            }
            if (codes.size != 1) {
                UnknownError(this)
            }
            when (codes.first()) {
                "not_found" -> NotFound()
                "bad_request" -> BadRequest()
                "unauthorized" -> Unauthorized()
                "unauthenticated" -> Unauthenticated()
                "server_error" -> ServerError()
                else -> UnknownError(this)
            }
        }
        return error ?: UnknownError(this)
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

        val targetAudience = "api://${targetCluster}.pdl.pdl-api/.default"
    }
}

class Unauthenticated : Exception()

class Unauthorized : Exception()

class NotFound : Exception()

class BadRequest : Exception()

class ServerError : Exception()

data class UnknownError(
    val response: GraphQLClientResponse<*>
) : Exception()