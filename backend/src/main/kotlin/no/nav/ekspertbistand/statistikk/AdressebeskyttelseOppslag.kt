package no.nav.ekspertbistand.statistikk

import no.nav.ekspertbistand.pdl.NotFound
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering

/** Avgjør om en person har adressebeskyttelse nå (fortrolig, strengt fortrolig eller strengt fortrolig utland). */
fun interface AdressebeskyttelseOppslag {
    suspend fun harAdressebeskyttelse(fnr: String): Boolean
}

/**
 * Slår opp gjeldende gradering i PDL. Alt annet enn `UGRADERT` regnes som adressebeskyttelse, også ukjente graderinger.
 * Feil kastes videre, slik at projeksjonen prøver igjen i stedet for å skrive en rad som ikke er maskert.
 * Personer som ikke finnes i PDL behandles som ugradert, som i [no.nav.ekspertbistand.norg.BehandlendeEnhetUtleder].
 */
class PdlAdressebeskyttelseOppslag(
    private val pdlApiKlient: PdlApiKlient,
) : AdressebeskyttelseOppslag {
    override suspend fun harAdressebeskyttelse(fnr: String): Boolean =
        pdlApiKlient.hentAdressebeskyttelse(fnr).fold(
            onSuccess = { person -> person.adressebeskyttelse.any { it.gradering != AdressebeskyttelseGradering.UGRADERT } },
            onFailure = { error -> if (error is NotFound) false else throw error },
        )
}
