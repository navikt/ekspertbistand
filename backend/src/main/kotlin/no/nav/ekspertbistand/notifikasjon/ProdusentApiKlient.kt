package no.nav.ekspertbistand.notifikasjon

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.notifikasjon.graphql.generated.HardDeleteSak
import no.nav.ekspertbistand.notifikasjon.graphql.generated.ISO8601DateTime
import no.nav.ekspertbistand.notifikasjon.graphql.generated.NyStatusSak
import no.nav.ekspertbistand.notifikasjon.graphql.generated.OpprettNyBeskjed
import no.nav.ekspertbistand.notifikasjon.graphql.generated.OpprettNySak
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.SaksStatus
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.Sendevindu
import no.nav.ekspertbistand.notifikasjon.graphql.generated.harddeletesak.DefaultHardDeleteSakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.harddeletesak.HardDeleteSakVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.inputs.AltinnRessursMottakerInput
import no.nav.ekspertbistand.notifikasjon.graphql.generated.inputs.EksterntVarselAltinnressursInput
import no.nav.ekspertbistand.notifikasjon.graphql.generated.inputs.MottakerInput
import no.nav.ekspertbistand.notifikasjon.graphql.generated.inputs.SendetidspunktInput
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.DefaultNyStatusSakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.Konflikt
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.NyStatusSakVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.SakFinnesIkke
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.UgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.UkjentProdusent
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.DefaultNyBeskjedResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.DuplikatEksternIdOgMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.DefaultNySakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.DuplikatGrupperingsid
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.DuplikatGrupperingsidEtterDelete
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.NySakVellykket
import java.net.URI
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMerkelapp as NyBeskjedUgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMottaker as NyBeskjedUgyldigMottaker
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UkjentProdusent as NyBeskjedUkjentProdusent
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UgyldigMerkelapp as NySakUgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UgyldigMottaker as NySakUgyldigMottaker
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UkjentProdusent as NySakUkjentProdusent
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UkjentRolle as NySakUkjentRolle

private const val merkelapp = "Ekspertbistand"
private const val notifikasjonBaseUrl = "http://notifikasjon-produsent-api.fager/api/graphql"

class ProdusentApiKlient(
    private val azureAdTokenProvider: AzureAdTokenProvider,
    defaultHttpClient: HttpClient
) {
    private val log = logger()
    private val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "notifikasjon.produsent.api.klient"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
        }
    }

    private val client = GraphQLKtorClient(
        url = URI(notifikasjonBaseUrl).toURL(),
        httpClient = httpClient
    )
    private val ressursId = "nav_tiltak_ekspertbistand"

    private val mottaker = MottakerInput(
        altinnRessurs = AltinnRessursMottakerInput(ressursId = ressursId),
    )

    private suspend fun hentEntraIdToken(): String {
        val scope = "api://${NaisEnvironment.clusterName}.fager.notifikasjon-produsent-api/.default"
        return azureAdTokenProvider.token(scope).fold(
            onSuccess = { it.accessToken },
            onError = {
                throw Exception("Feil ved henting av token for Notifikasjon Produsent API: ${it.error.errorDescription}")
            }
        )
    }

    suspend fun opprettNySak(
        skjemaId: String,
        virksomhetsnummer: String,
        tittel: String,
        lenke: String,
        tidspunkt: ISO8601DateTime? = null
    ) {
        val token = hentEntraIdToken()
        val resultat = client.execute(
            OpprettNySak(
                variables = OpprettNySak.Variables(
                    skjemaId,
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
        skjemaId: String,
        virksomhetsnummer: String,
        tekst: String,
        lenke: String,
        tidspunkt: ISO8601DateTime? = null,
        eksternVarsel: EksterntVarsel? = null
    ) {
        val token = hentEntraIdToken()
        val resultat = client.execute(
            OpprettNyBeskjed(
                variables = OpprettNyBeskjed.Variables(
                    skjemaId,
                    merkelapp,
                    virksomhetsnummer,
                    tekst,
                    lenke,
                    tidspunkt,
                    mottaker,
                    eksternVarsel?.tilVarsel()
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

    suspend fun nyStatusSak(
        skjemaId: String,
        status: SaksStatus,
        statusTekst: String,
        tidspunkt: ISO8601DateTime? = null,
    ) {
        val token = hentEntraIdToken()
        val resultat = client.execute(
            NyStatusSak(
                variables = NyStatusSak.Variables(
                    idempotencyKey = "$skjemaId-$statusTekst",
                    id = skjemaId,
                    nyStatus = status,
                    tidspunkt = tidspunkt,
                    overstyrStatustekstMed = statusTekst
                )
            )
        ) {
            bearerAuth(token)
        }
        when (val nyStatusSak = resultat.data?.nyStatusSak) {
            null -> throw NyStatusSakException("Uventet feil: NyStatusSak er null, resultat: $resultat")

            is NyStatusSakVellykket -> log.info("Oppdaterte status på sak med id ${nyStatusSak.id}}")

            is Konflikt -> throw NyStatusSakException(nyStatusSak.feilmelding)
            is SakFinnesIkke -> throw NyStatusSakException(nyStatusSak.feilmelding)
            is UgyldigMerkelapp -> throw NyStatusSakException(nyStatusSak.feilmelding)
            is UkjentProdusent -> throw NyStatusSakException(nyStatusSak.feilmelding)
            is DefaultNyStatusSakResultatImplementation -> throw NyStatusSakException("Uventet feil: $resultat")
        }
    }

    suspend fun hardDeleteSak(skjemaId: String) {
        val token = hentEntraIdToken()
        val resultat = client.execute(
            HardDeleteSak(
                variables = HardDeleteSak.Variables(
                    id = skjemaId,
                )
            )
        ) {
            bearerAuth(token)
        }
        when (val hardDeleteSak = resultat.data?.hardDeleteSak) {
            is HardDeleteSakVellykket -> log.info("Harddeleted sak med id $skjemaId")
            is DefaultHardDeleteSakResultatImplementation -> throw HardDeleteSakException("Uventet feil: $resultat")
            is no.nav.ekspertbistand.notifikasjon.graphql.generated.harddeletesak.SakFinnesIkke -> throw (HardDeleteSakException(
                hardDeleteSak.feilmelding
            ))

            is no.nav.ekspertbistand.notifikasjon.graphql.generated.harddeletesak.UgyldigMerkelapp -> throw (HardDeleteSakException(
                hardDeleteSak.feilmelding
            ))

            is no.nav.ekspertbistand.notifikasjon.graphql.generated.harddeletesak.UkjentProdusent -> throw (HardDeleteSakException(
                hardDeleteSak.feilmelding
            ))

            null -> throw HardDeleteSakException("Uventet feil: HardDeleteSak er null, $resultat")
        }
    }

    private fun EksterntVarsel.tilVarsel() =
        EksterntVarselAltinnressursInput(
            mottaker.altinnRessurs!!,
            epostTittel,
            epostHtmlBody,
            smsTekst,
            SendetidspunktInput(sendevindu = Sendevindu.NKS_AAPNINGSTID)
        )
}

data class EksterntVarsel(
    val epostTittel: String,
    val epostHtmlBody: String,
    val smsTekst: String,
)

class SakOpprettetException(message: String) : Exception(message)
class BeskjedOpprettetException(message: String) : Exception(message)
class NyStatusSakException(message: String) : Exception(message)
class HardDeleteSakException(message: String) : Exception(message)