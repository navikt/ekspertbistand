package no.nav.ekspertbistand.statistikk

import no.nav.ekspertbistand.pdl.NotFound
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering

/** Avgjør om en person har strengt fortrolig adresse (kode 6 eller kode 6 utland) nå. */
fun interface AdressebeskyttelseOppslag {
    suspend fun harStrengtFortroligAdresse(fnr: String): Boolean
}

/**
 * Slår opp gjeldende gradering i PDL. Feil kastes videre, slik at projeksjonen prøver igjen i stedet for å
 * skrive en rad uten skjerming. Personer som ikke finnes i PDL behandles som ugradert, som i
 * [no.nav.ekspertbistand.norg.BehandlendeEnhetUtleder].
 */
class PdlAdressebeskyttelseOppslag(
    private val pdlApiKlient: PdlApiKlient,
) : AdressebeskyttelseOppslag {
    override suspend fun harStrengtFortroligAdresse(fnr: String): Boolean =
        pdlApiKlient.hentAdressebeskyttelse(fnr).fold(
            onSuccess = { person -> person.adressebeskyttelse.any { it.gradering in STRENGT_FORTROLIG } },
            onFailure = { error -> if (error is NotFound) false else throw error },
        )

    private companion object {
        val STRENGT_FORTROLIG = setOf(
            AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
        )
    }
}
