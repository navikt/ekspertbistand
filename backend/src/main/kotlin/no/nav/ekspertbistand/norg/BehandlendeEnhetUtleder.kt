package no.nav.ekspertbistand.norg

import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.pdl.NotFound
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering
import no.nav.ekspertbistand.pdl.graphql.generated.enums.GtType
import no.nav.ekspertbistand.pdl.graphql.generated.hentgeografisktilknytning.GeografiskTilknytning
import no.nav.ekspertbistand.soknad.DTO

/**
 * Utleder behandlende enhet for en søknad (Norg-enhetsnummer), og kommunen saken ble rutet på.
 *
 * - Strengt fortrolig adresse: geografisk tilknytning fra PDL, ellers [BehandlendeEnhetService.NAV_VIKAFOSSEN].
 *   Kommunenummeret er null, fordi vi ikke lagrer deltakerens bosted.
 * - Ellers (og når personen ikke finnes i PDL): kommunenummer fra virksomhetens forretningsadresse i Ereg.
 *
 * Kaster ved feil mot PDL, Ereg eller Norg, og [ManglerDataForBehandlendeEnhetException] når
 * virksomheten mangler kommunenummer.
 */
class BehandlendeEnhetUtleder(
    private val pdlApiKlient: PdlApiKlient,
    private val eregClient: EregClient,
    private val behandlendeEnhetService: BehandlendeEnhetService,
) {
    suspend fun utled(soknad: DTO.Soknad): Ruting =
        pdlApiKlient.hentAdressebeskyttelse(soknad.ansatt.fnr).fold(
            onSuccess = { person ->
                when (val gradering = person.adressebeskyttelse.map { it.gradering }.hoyesteGradering()) {
                    AdressebeskyttelseGradering.STRENGT_FORTROLIG,
                    AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> {
                        val geografiskTilknytning = pdlApiKlient.hentGeografiskTilknytning(soknad.ansatt.fnr).fold(
                            onSuccess = { it.geografiskTilknytning() ?: BehandlendeEnhetService.NAV_VIKAFOSSEN },
                            onFailure = { throw it },
                        )
                        Ruting(
                            enhetNr = behandlendeEnhetService.hentBehandlendeEnhet(gradering, geografiskTilknytning),
                            virksomhetKommunenummer = null,
                        )
                    }

                    AdressebeskyttelseGradering.FORTROLIG,
                    AdressebeskyttelseGradering.UGRADERT,
                    AdressebeskyttelseGradering.__UNKNOWN_VALUE -> rutingPaaVirksomhet(gradering, soknad)
                }
            },
            onFailure = { error ->
                when (error) {
                    is NotFound -> rutingPaaVirksomhet(AdressebeskyttelseGradering.__UNKNOWN_VALUE, soknad)
                    else -> throw error
                }
            },
        )

    private suspend fun rutingPaaVirksomhet(gradering: AdressebeskyttelseGradering, soknad: DTO.Soknad): Ruting {
        val kommunenummer = virksomhetensKommunenummer(soknad)
        return Ruting(
            enhetNr = behandlendeEnhetService.hentBehandlendeEnhet(gradering, kommunenummer),
            virksomhetKommunenummer = kommunenummer,
        )
    }

    private suspend fun virksomhetensKommunenummer(soknad: DTO.Soknad): String {
        val virksomhetsnummer = soknad.virksomhet.virksomhetsnummer
        return eregClient.hentOrganisasjon(virksomhetsnummer)
            .organisasjonDetaljer
            ?.forretningsadresser
            ?.firstNotNullOfOrNull { it.kommunenummer }
            ?: throw ManglerDataForBehandlendeEnhetException("Fant ikke kommunenummer for virksomhet $virksomhetsnummer")
    }

    private fun List<AdressebeskyttelseGradering>.hoyesteGradering(): AdressebeskyttelseGradering =
        maxByOrNull { gradering ->
            when (gradering) {
                AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> 3
                AdressebeskyttelseGradering.STRENGT_FORTROLIG -> 2
                AdressebeskyttelseGradering.FORTROLIG -> 1
                AdressebeskyttelseGradering.UGRADERT -> 0
                AdressebeskyttelseGradering.__UNKNOWN_VALUE -> -1
            }
        } ?: AdressebeskyttelseGradering.UGRADERT

    private fun GeografiskTilknytning.geografiskTilknytning(): String? =
        when (gtType) {
            GtType.KOMMUNE -> gtKommune
            GtType.BYDEL -> gtBydel ?: gtKommune
            GtType.UTLAND -> gtLand
            GtType.UDEFINERT, GtType.__UNKNOWN_VALUE -> null
        }
}

data class Ruting(
    val enhetNr: String,
    /** Kommunen til virksomheten som ble brukt mot Norg. Null for strengt fortrolig adresse. */
    val virksomhetKommunenummer: String?,
)

class ManglerDataForBehandlendeEnhetException(message: String) : Exception(message)
