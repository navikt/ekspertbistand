package no.nav.ekspertbistand.notifikasjon

import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.notifikasjon.graphql.generated.*
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.NyTidStrategi
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.SaksStatus
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.Sendevindu
import no.nav.ekspertbistand.notifikasjon.graphql.generated.harddeletesak.DefaultHardDeleteSakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.harddeletesak.HardDeleteSakVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.inputs.*
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.*
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.DefaultNyBeskjedResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.DuplikatEksternIdOgMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.DefaultNySakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.DuplikatGrupperingsid
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.DuplikatGrupperingsidEtterDelete
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.NySakVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.skedulerharddeletesak.DefaultSkedulerHardDeleteSakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.skedulerharddeletesak.SkedulerHardDeleteSakVellykket
import java.net.URI
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Duration.Companion.days
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMerkelapp as NyBeskjedUgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMottaker as NyBeskjedUgyldigMottaker
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UkjentProdusent as NyBeskjedUkjentProdusent
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UgyldigMerkelapp as NySakUgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UgyldigMottaker as NySakUgyldigMottaker
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UkjentProdusent as NySakUkjentProdusent
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UkjentRolle as NySakUkjentRolle
import no.nav.ekspertbistand.notifikasjon.graphql.generated.skedulerharddeletesak.SakFinnesIkke as SkedulerHardDeleteSakFinnesIkke
import no.nav.ekspertbistand.notifikasjon.graphql.generated.skedulerharddeletesak.UgyldigMerkelapp as SkedulerHardDeleteUgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.skedulerharddeletesak.UkjentProdusent as SkedulerHardDeleteUkjentProdusent

private const val merkelapp = "Ekspertbistand"
private const val notifikasjonBaseUrl = "http://notifikasjon-produsent-api.fager/api/graphql"

/**
 * Notifikasjonsplattformen hard-sletter saken (og beskjeder med samme grupperingsid) så lenge etter
 * siste oppdatering. nyStatusSak overskriver fristen, så siste statusoppdatering bestemmer.
 */
val hardDeleteINotifikasjonsplattformenOm = 455.days

@OptIn(ExperimentalTime::class)
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

    private val mottaker = MottakerInput(
        altinnRessurs = AltinnRessursMottakerInput(ressursId = altinn3Ressursid),
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
        grupperingsid: String,
        virksomhetsnummer: String,
        tittel: String,
        lenke: String,
        tidspunkt: ISO8601DateTime? = null
    ) {
        val token = hentEntraIdToken()
        val resultat = client.execute(
            OpprettNySak(
                variables = OpprettNySak.Variables(
                    grupperingsid = grupperingsid,
                    merkelapp = merkelapp,
                    virksomhetsnummer = virksomhetsnummer,
                    tittel = tittel,
                    lenke = lenke,
                    tidspunkt = tidspunkt,
                    mottaker = mottaker,
                    hardDelete = FutureTemporalInput(om = hardDeleteINotifikasjonsplattformenOm.toIsoString()),
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
        eksternId: String,
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
                    grupperingsid = grupperingsid,
                    eksternId = eksternId,
                    merkelapp = merkelapp,
                    virksomhetsnummer = virksomhetsnummer,
                    tekst = tekst,
                    lenke = lenke,
                    tidspunkt = tidspunkt,
                    mottaker = mottaker,
                    eksterneVarsler = listOfNotNull(eksternVarsel?.tilVarsel())
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
        grupperingsid: String,
        status: SaksStatus,
        statusTekst: String,
        tidspunkt: ISO8601DateTime? = null,
    ) {
        val token = hentEntraIdToken()
        val resultat = client.execute(
            NyStatusSak(
                variables = NyStatusSak.Variables(
                    idempotencyKey = "$grupperingsid-$statusTekst",
                    grupperingsid = grupperingsid,
                    merkelapp = merkelapp,
                    nyStatus = status,
                    overstyrStatustekstMed = statusTekst,
                    tidspunkt = tidspunkt,
                    hardDelete = HardDeleteUpdateInput(
                        nyTid = FutureTemporalInput(om = hardDeleteINotifikasjonsplattformenOm.toIsoString()),
                        strategi = NyTidStrategi.OVERSKRIV
                    )
                )
            )
        ) {
            bearerAuth(token)
        }
        when (val nyStatusSak = resultat.data?.nyStatusSakByGrupperingsid) {
            null -> throw NyStatusSakException("Uventet feil: NyStatusSak er null, resultat: $resultat")

            is NyStatusSakVellykket -> log.info("Oppdaterte status på sak med id ${nyStatusSak.id}}")

            is Konflikt -> throw NyStatusSakException(nyStatusSak.feilmelding)
            is SakFinnesIkke -> throw NyStatusSakException(nyStatusSak.feilmelding)
            is UgyldigMerkelapp -> throw NyStatusSakException(nyStatusSak.feilmelding)
            is UkjentProdusent -> throw NyStatusSakException(nyStatusSak.feilmelding)
            is DefaultNyStatusSakResultatImplementation -> throw NyStatusSakException("Uventet feil: $resultat")
        }
    }

    suspend fun hardDeleteSak(grupperingsid: String) {
        val token = hentEntraIdToken()
        val resultat = client.execute(
            HardDeleteSak(
                variables = HardDeleteSak.Variables(
                    grupperingsid = grupperingsid,
                    merkelapp = merkelapp,
                )
            )
        ) {
            bearerAuth(token)
        }
        when (val hardDeleteSak = resultat.data?.hardDeleteSakByGrupperingsid) {
            is HardDeleteSakVellykket -> log.info("Harddeleted sak med id $grupperingsid")
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

    /**
     * Setter (eller endrer) når notifikasjonsplattformen skal hard-slette saken, på tidspunktet [den].
     * Tidspunktet beregnes alltid her i appen. Kaster [SkedulerHardDeleteSakFinnesIkkeException] hvis
     * saken ikke finnes i plattformen.
     */
    suspend fun skedulerHardDeleteSak(
        grupperingsid: String,
        den: Instant,
        strategi: NyTidStrategi = NyTidStrategi.OVERSKRIV,
    ) {
        val token = hentEntraIdToken()
        val nyTid = FutureTemporalInput(den = den.tilOsloLocalDateTime())
        val resultat = client.execute(
            SkedulerHardDeleteSak(
                variables = SkedulerHardDeleteSak.Variables(
                    grupperingsid = grupperingsid,
                    merkelapp = merkelapp,
                    hardDelete = HardDeleteUpdateInput(nyTid = nyTid, strategi = strategi),
                )
            )
        ) {
            bearerAuth(token)
        }
        when (val skedulert = resultat.data?.skedulerHardDeleteSakByGrupperingsid) {
            is SkedulerHardDeleteSakVellykket -> log.info("Skedulerte hard delete {} ({}) for sak med id {}", nyTid.den, strategi, skedulert.id)
            is SkedulerHardDeleteSakFinnesIkke -> throw SkedulerHardDeleteSakFinnesIkkeException(skedulert.feilmelding)
            is SkedulerHardDeleteUgyldigMerkelapp -> throw SkedulerHardDeleteSakException(skedulert.feilmelding)
            is SkedulerHardDeleteUkjentProdusent -> throw SkedulerHardDeleteSakException(skedulert.feilmelding)
            is DefaultSkedulerHardDeleteSakResultatImplementation -> throw SkedulerHardDeleteSakException("Uventet feil: $resultat")
            null -> throw SkedulerHardDeleteSakException("Uventet feil: SkedulerHardDeleteSak er null, $resultat")
        }
    }

    private fun EksterntVarsel.tilVarsel() =
        EksterntVarselInput(
            altinnressurs =
                EksterntVarselAltinnressursInput(
                    mottaker.altinnRessurs!!,
                    epostTittel,
                    epostHtmlBody,
                    smsTekst,
                    SendetidspunktInput(sendevindu = Sendevindu.NKS_AAPNINGSTID)
                )
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
open class SkedulerHardDeleteSakException(message: String) : Exception(message)
class SkedulerHardDeleteSakFinnesIkkeException(message: String) : SkedulerHardDeleteSakException(message)

private val osloTidssone = TimeZone.of("Europe/Oslo")

/** Notifikasjonsplattformen tolker `ISO8601LocalDateTime` i Europe/Oslo. Sekundpresisjon holder. */
@OptIn(ExperimentalTime::class)
internal fun Instant.tilOsloLocalDateTime(): ISO8601LocalDateTime =
    Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(osloTidssone).format(LocalDateTime.Formats.ISO)
