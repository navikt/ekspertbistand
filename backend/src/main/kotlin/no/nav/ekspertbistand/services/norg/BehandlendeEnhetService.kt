package no.nav.ekspertbistand.services.norg

import no.nav.ekspertbistand.services.pdl.graphql.generated.enums.AdressebeskyttelseGradering
import no.nav.ekspertbistand.services.pdl.graphql.generated.hentgeografisktilknytning.GeografiskTilknytning

class BehandlendeEnhetService(
    private val norgKlient: NorgKlient
) {
    private val norgTilArenaEnhetNrMap = mapOf(
        NAV_ARBEIDSLIVSSENTER_NORDLAND_NORG to NAV_ARBEIDSLIVSSENTER_NORDLAND_ARENA,
    )

    suspend fun hentBehandlendeEnhet(
        adressebeskyttelse: AdressebeskyttelseGradering,
        geografiskTilknytning: GeografiskTilknytning
    ): String {
        return when (adressebeskyttelse) {
            AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> norgKlient.hentBehandlendeEnhetAdresseBeskyttet(
                geografiskTilknytning.gtKommune!!
            ).let {
                if (it == null) {
                    NAV_VIKAFOSSEN
                }
                norgTilArenaEnhetNrMap.getOrDefault(it!!.enhetNr, it.enhetNr)
            }

            AdressebeskyttelseGradering.FORTROLIG,
            AdressebeskyttelseGradering.UGRADERT,
            AdressebeskyttelseGradering.__UNKNOWN_VALUE ->
                norgKlient.hentBehandlendeEnhet(
                    geografiskTilknytning.gtKommune!!
                ).let {
                    if (it == null) {
                        NAV_ARBEIDSLIVSSENTER_OSLO //TODO: hva skal være fallback her?
                    }
                    norgTilArenaEnhetNrMap.getOrDefault(it!!.enhetNr, it.enhetNr)
                }
        }
    }

    companion object {
        private const val NAV_VIKAFOSSEN = "2103"
        private const val NAV_ARBEIDSLIVSSENTER_NORDLAND_NORG = "1891"
        private const val NAV_ARBEIDSLIVSSENTER_NORDLAND_ARENA = "1899"
        private const val NAV_ARBEIDSLIVSSENTER_OSLO = "0391"
    }
}