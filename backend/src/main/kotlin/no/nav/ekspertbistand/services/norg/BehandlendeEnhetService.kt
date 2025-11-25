package no.nav.ekspertbistand.services.norg

import no.nav.ekspertbistand.services.pdl.graphql.generated.enums.AdressebeskyttelseGradering

class BehandlendeEnhetService(
    private val norgKlient: NorgKlient
) {
    private val norgTilArenaEnhetNrMap = mapOf(
        NAV_ARBEIDSLIVSSENTER_NORDLAND_NORG to NAV_ARBEIDSLIVSSENTER_NORDLAND_ARENA,
    )

    suspend fun hentBehandlendeEnhet(
        adressebeskyttelse: AdressebeskyttelseGradering,
        geografiskTilknytning: String
    ): String {
        return when (adressebeskyttelse) {
            AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> norgKlient.hentBehandlendeEnhetAdresseBeskyttet(
                geografiskTilknytning
            ).let {
                if (it == null)
                    NAV_VIKAFOSSEN
                else
                    norgTilArenaEnhetNrMap.getOrDefault(it.enhetNr, it.enhetNr)
            }

            AdressebeskyttelseGradering.FORTROLIG,
            AdressebeskyttelseGradering.UGRADERT,
            AdressebeskyttelseGradering.__UNKNOWN_VALUE ->
                norgKlient.hentBehandlendeEnhet(
                    geografiskTilknytning
                ).let {
                    if (it == null)
                        NAV_ARBEIDSLIVSSENTER_OSLO //TODO: hva skal være fallback her?
                    else
                        norgTilArenaEnhetNrMap.getOrDefault(it.enhetNr, it.enhetNr)
                }
        }
    }

    companion object {
        const val NAV_VIKAFOSSEN = "2103"
        const val NAV_ARBEIDSLIVSSENTER_NORDLAND_NORG = "1891"
        const val NAV_ARBEIDSLIVSSENTER_NORDLAND_ARENA = "1899"
        const val NAV_ARBEIDSLIVSSENTER_OSLO = "0391"
    }
}