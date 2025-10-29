package no.nav.ekspertbistand.services.notifikasjon

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import no.nav.ekspertbistand.infrastruktur.NaisEnvironment
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.defaultHttpClient
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.ISO8601DateTime
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.OpprettNyBeskjed
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.OpprettNySak
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.inputs.AltinnRessursMottakerInput
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.inputs.MottakerInput
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.DefaultNyBeskjedResultatImplementation
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.DuplikatEksternIdOgMerkelapp
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedVellykket
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.DefaultNySakResultatImplementation
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.DuplikatGrupperingsid
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.DuplikatGrupperingsidEtterDelete
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.NySakVellykket
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMerkelapp as NyBeskjedUgyldigMerkelapp
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMottaker as NyBeskjedUgyldigMottaker
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.UkjentProdusent as NyBeskjedUkjentProdusent
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.UgyldigMerkelapp as NySakUgyldigMerkelapp
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.UgyldigMottaker as NySakUgyldigMottaker
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.UkjentProdusent as NySakUkjentProdusent
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.UkjentRolle as NySakUkjentRolle
import java.net.URI

class ProdusentApiKlient(
    private val tokenProvider: TokenProvider,
    private val httpClient: HttpClient
) {
    private val url = URI("https://notifikasjonsplatform.com/api").toURL()

    private val log = logger()
    private val client = GraphQLKtorClient(
        url = url,
        httpClient = httpClient
    )
    private val ressursId = "eksperbistandRessurs"

    private val mottaker = MottakerInput(
        altinn = null,
        altinnRessurs = AltinnRessursMottakerInput(ressursId = ressursId),
        naermesteLeder = null
    )

    private suspend fun hentEntraIdToken(): String {
        val scope = "api://${NaisEnvironment.clusterName}.fager.notifikasjon-produsent-api/.default"
        return tokenProvider.token(scope).fold(
            onSuccess = { it.accessToken },
            onError = {
                throw Exception("Feil ved henting av token for Notifikasjon Produsent API: ${it.error.errorDescription}")
            }
        )
    }

    suspend fun opprettNySak(
        grupperingsid: String,
        merkelapp: String,
        virksomhetsnummer: String,
        tittel: String,
        lenke: String,
        tidspunkt: ISO8601DateTime? = null
    ) {
        val token = hentEntraIdToken()
        val resultat = client.execute(
            OpprettNySak(
                variables = OpprettNySak.Variables(
                    grupperingsid,
                    merkelapp,
                    virksomhetsnummer,
                    tittel,
                    lenke,
                    tidspunkt,
                    mottaker
                )
            )
        ) {
            bearerAuth(token)
        }

        when (val nySak = resultat.data?.nySak) {
            null -> throw SakOpprettetException("Uventet feil: Ny sak er null, resultat: $resultat")

            is NySakVellykket -> log.info("Opprettet ny sak {}", nySak.id)

            is DuplikatGrupperingsid -> log.info("Sak finnes allerede. hopper over. {}", nySak.feilmelding)
            is DuplikatGrupperingsidEtterDelete -> log.info("Sak finnes allerede. hopper over. {}", nySak.feilmelding)

            is NySakUgyldigMerkelapp -> throw SakOpprettetException(nySak.feilmelding)
            is NySakUgyldigMottaker -> throw SakOpprettetException(nySak.feilmelding)
            is NySakUkjentProdusent -> throw SakOpprettetException(nySak.feilmelding)
            is NySakUkjentRolle -> throw SakOpprettetException(nySak.feilmelding)

            is DefaultNySakResultatImplementation -> throw SakOpprettetException("Uventet feil: $resultat")
        }
    }

    suspend fun opprettNyBeskjed(
        grupperingsid: String,
        merkelapp: String,
        virksomhetsnummer: String,
        tekst: String,
        lenke: String,
        tidspunkt: ISO8601DateTime? = null
    ) {
        val token = hentEntraIdToken()
        val resultat = client.execute(
            OpprettNyBeskjed(
                variables = OpprettNyBeskjed.Variables(
                    grupperingsid,
                    merkelapp,
                    virksomhetsnummer,
                    tekst,
                    lenke,
                    tidspunkt,
                    mottaker
                )
            )
        ) {
            bearerAuth(token)
        }

        when (val nyBeskjed = resultat.data?.nyBeskjed) {
            null -> throw BeskjedOpprettetException("Uventet feil: Ny beskjed er null, resultat: $resultat")

            is NyBeskjedVellykket -> log.info("Opprettet ny beskjed {}", nyBeskjed.id)

            is DuplikatEksternIdOgMerkelapp -> log.info(
                "Beskjed finnes allerede. hopper over. {}",
                nyBeskjed.feilmelding
            )

            is NyBeskjedUgyldigMerkelapp -> throw BeskjedOpprettetException(nyBeskjed.feilmelding)
            is NyBeskjedUgyldigMottaker -> throw BeskjedOpprettetException(nyBeskjed.feilmelding)
            is NyBeskjedUkjentProdusent -> throw BeskjedOpprettetException(nyBeskjed.feilmelding)

            is DefaultNyBeskjedResultatImplementation -> throw BeskjedOpprettetException("Uventet feil: $resultat")
        }
    }
}

class SakOpprettetException(message: String) : Exception(message)
class BeskjedOpprettetException(message: String) : Exception(message)