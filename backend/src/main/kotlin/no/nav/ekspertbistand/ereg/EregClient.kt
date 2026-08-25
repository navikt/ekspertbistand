package no.nav.ekspertbistand.ereg

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultJson

class EregClient(
    val defaultHttpClient: HttpClient
) {
    companion object {
        val ingress = basedOnEnv(
            prod = "https://ereg-services.prod-fss-pub.nais.io",
            dev = "https://ereg-services.dev-fss-pub.nais.io",
            other = "http://ereg-services.mock.svc.cluster.local",
        )
        const val API_PATH = "/v2/organisasjon/"
        const val FINN_API_PATH = "/v2/organisasjon/finn"
    }

    private val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "ereg.client"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    suspend fun hentOrganisasjon(orgnr: String): OrganisasjonResponse {
        return httpClient.get {
            url {
                takeFrom(ingress)
                path(API_PATH + orgnr)
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body()
    }

    /**
     * Søk etter organisasjoner på (deler av) navn mot Ereg `GET /v2/organisasjon/finn`.
     * Returnerer et sammendrag (subsett) av organisasjonene som matcher [organisasjonsnavn].
     *
     * @param antall maks antall responsobjekter (typeahead trenger ikke mange treff).
     */
    suspend fun finnOrganisasjon(
        organisasjonsnavn: String,
        antall: Int = 20,
    ): OrganisasjonSammendragResultat {
        return httpClient.get {
            url {
                takeFrom(ingress)
                path(FINN_API_PATH)
                parameters.append("organisasjonsnavn", organisasjonsnavn)
                parameters.append("antall", antall.toString())
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body()
    }

    suspend fun hentPostAdresse(orgnr: String): List<Postadresse> {
        val organisasjon = hentOrganisasjon(orgnr)
        return organisasjon.organisasjonDetaljer?.postadresser ?: emptyList()
    }

    suspend fun hentForretningsadresse(orgnr: String): List<Forretningsadresse> {
        val organisasjon = hentOrganisasjon(orgnr)
        return organisasjon.organisasjonDetaljer?.forretningsadresser ?: emptyList()
    }
}

/**
 * Svar fra Ereg `GET /v2/organisasjon/finn` — et sammendrag av matchende organisasjoner.
 */
@Serializable
data class OrganisasjonSammendragResultat(
    val totalAntallTreff: Int? = null,
    val organisasjonSammendrag: List<OrganisasjonSammendrag> = emptyList(),
)

/**
 * Sammendrag (subsett) av en organisasjon fra søk.
 */
@Serializable
data class OrganisasjonSammendrag(
    val organisasjonsnummer: String? = null,
    val enhetstype: String? = null,
    val sammensattnavn: String? = null,
    val navnelinje1: String? = null,
    val juridiskEnhetOrganisasjonsnummer: String? = null,
    val adresselinje1: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
    val kommunenummer: String? = null,
    val kommunenavn: String? = null,
    val landkode: String? = null,
    val opphoert: Boolean? = null,
)

@Serializable
data class OrganisasjonResponse(
    val organisasjonsnummer: String? = null,
    val navn: Navn? = null,
    val type: String? = null,
    val organisasjonDetaljer: OrganisasjonDetaljer? = null,
    val juridiskEnhetDetaljer: JuridiskEnhetDetaljer? = null,
    val virksomhetDetaljer: VirksomhetDetaljer? = null,
    val organisasjonsleddDetaljer: OrganisasjonsleddDetaljer? = null,
    val driverVirksomheter: List<DriverVirksomhet> = emptyList(),
    val knytninger: List<JuridiskEnhetKnytning> = emptyList(),
    val fisjoner: List<JuridiskEnhetFisjon> = emptyList(),
    val fusjoner: List<JuridiskEnhetFusjon> = emptyList(),
    val bestaarAvOrganisasjonsledd: List<BestaarAvOrganisasjonsledd> = emptyList(),
    val inngaarIJuridiskEnheter: List<InngaarIJuridiskEnhet> = emptyList(),
    val organisasjonsleddUnder: List<BestaarAvOrganisasjonsledd> = emptyList(),
    val organisasjonsleddOver: List<BestaarAvOrganisasjonsledd> = emptyList(),
)

@Serializable
data class OrganisasjonDetaljer(
    val registreringsdato: String? = null,
    val stiftelsesdato: String? = null,
    val opphoersdato: String? = null,
    val enhetstyper: List<Enhetstype> = emptyList(),
    val navn: List<Navn> = emptyList(),
    val naeringer: List<Naering> = emptyList(),
    val statuser: List<Status> = emptyList(),
    val forretningsadresser: List<Forretningsadresse> = emptyList(),
    val postadresser: List<Postadresse> = emptyList(),
    val epostadresser: List<Epostadresse> = emptyList(),
    val internettadresser: List<Internettadresse> = emptyList(),
    val telefonnummer: List<Telefonnummer> = emptyList(),
    val mobiltelefonnummer: List<Telefonnummer> = emptyList(),
    val telefaksnummer: List<Telefonnummer> = emptyList(),
    val formaal: List<Formaal> = emptyList(),
    val registrertMVA: List<MVA> = emptyList(),
    val underlagtHjemlandLovgivningForetaksform: List<UnderlagtHjemlandLovgivningForetaksform> = emptyList(),
    val hjemlandregistre: List<Hjemlandregister> = emptyList(),
    val ansatte: List<Ansatte> = emptyList(),
    val navSpesifikkInformasjon: NAVSpesifikkInformasjon? = null,
    val maalform: String? = null,
    val sistEndret: String? = null,
)

@Serializable
data class JuridiskEnhetDetaljer(
    val enhetstype: String? = null,
    val harAnsatte: Boolean? = null,
    val sektorkode: String? = null,
    val registrertStiftelsesregisteret: Boolean? = null,
    val kapitalopplysninger: List<Kapitalopplysninger> = emptyList(),
    val foretaksregisterRegistreringer: List<Foretaksregister> = emptyList(),
)

@Serializable
data class JuridiskEnhetKnytning(
    val knytning: String? = null,
    val juridiskEnhet: OrganisasjonResponse? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class JuridiskEnhetFisjon(
    val juridiskEnhet: OrganisasjonResponse? = null,
    val virkningsdato: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class JuridiskEnhetFusjon(
    val juridiskEnhet: OrganisasjonResponse? = null,
    val virkningsdato: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class VirksomhetDetaljer(
    val enhetstype: String? = null,
    val ubemannetVirksomhet: Boolean? = null,
    val oppstartsdato: String? = null,
    val eierskiftedato: String? = null,
    val nedleggelsesdato: String? = null,
)

@Serializable
data class OrganisasjonsleddDetaljer(
    val enhetstype: String? = null,
    val sektorkode: String? = null,
)

@Serializable
data class DriverVirksomhet(
    val organisasjonsnummer: String? = null,
    val navn: Navn? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class BestaarAvOrganisasjonsledd(
    val organisasjonsledd: OrganisasjonResponse? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class InngaarIJuridiskEnhet(
    val organisasjonsnummer: String? = null,
    val navn: Navn? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Enhetstype(
    val enhetstype: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Navn(
    val sammensattnavn: String? = null,
    val navnelinje1: String? = null,
    val navnelinje2: String? = null,
    val navnelinje3: String? = null,
    val navnelinje4: String? = null,
    val navnelinje5: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Naering(
    val naeringskode: String? = null,
    val hjelpeenhet: Boolean? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Status(
    val kode: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Adresse(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
    val landkode: String? = null,
    val kommunenummer: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
    val type: String? = null,
)

@Serializable
data class Postadresse(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
    val landkode: String? = null,
    val kommunenummer: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
    val type: String? = null,
)

@Serializable
data class Forretningsadresse(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
    val landkode: String? = null,
    val kommunenummer: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
    val type: String? = null,
)

@Serializable
data class Epostadresse(
    val adresse: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Internettadresse(
    val adresse: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Telefonnummer(
    val nummer: String? = null,
    val telefontype: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Formaal(
    val formaal: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class MVA(
    val registrertIMVA: Boolean? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class UnderlagtHjemlandLovgivningForetaksform(
    val landkode: String? = null,
    val foretaksform: String? = null,
    val beskrivelseHjemland: String? = null,
    val beskrivelseNorge: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Hjemlandregister(
    val registernummer: String? = null,
    val navn1: String? = null,
    val navn2: String? = null,
    val navn3: String? = null,
    val postadresse: Adresse? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Ansatte(
    val antall: Long? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class NAVSpesifikkInformasjon(
    val erIA: Boolean? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Kapitalopplysninger(
    val kapital: Double? = null,
    val kapitalInnbetalt: Double? = null,
    val kapitalBundetKs: String? = null,
    val valuta: String? = null,
    val fritekst: String? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Foretaksregister(
    val registrert: Boolean? = null,
    val bruksperiode: Bruksperiode? = null,
    val gyldighetsperiode: Gyldighetsperiode? = null,
)

@Serializable
data class Bruksperiode(
    val fom: String? = null,
    val tom: String? = null,
)

@Serializable
data class Gyldighetsperiode(
    val fom: String? = null,
    val tom: String? = null,
)
