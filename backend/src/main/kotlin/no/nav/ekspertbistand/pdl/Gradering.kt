package no.nav.ekspertbistand.pdl

import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering
import no.nav.ekspertbistand.saksbehandling.Adressebeskyttelse

/**
 * Den strengeste graderingen. Tom liste er ugradert. Ruting og lagret adressebeskyttelse bruker samme rangering, slik at
 * flagget på saken stemmer med enheten saken rutes til.
 */
fun List<AdressebeskyttelseGradering>.hoyesteGradering(): AdressebeskyttelseGradering =
    maxByOrNull { gradering ->
        when (gradering) {
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> 3
            AdressebeskyttelseGradering.STRENGT_FORTROLIG -> 2
            AdressebeskyttelseGradering.FORTROLIG -> 1
            AdressebeskyttelseGradering.UGRADERT -> 0
            AdressebeskyttelseGradering.__UNKNOWN_VALUE -> -1
        }
    } ?: AdressebeskyttelseGradering.UGRADERT

/** `__UNKNOWN_VALUE` finnes ikke i PDL-skjemaet, og gir null som i rutingen. */
fun AdressebeskyttelseGradering.tilAdressebeskyttelse(): Adressebeskyttelse? =
    when (this) {
        AdressebeskyttelseGradering.STRENGT_FORTROLIG,
        AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> Adressebeskyttelse.STRENGT_FORTROLIG

        AdressebeskyttelseGradering.FORTROLIG -> Adressebeskyttelse.FORTROLIG
        AdressebeskyttelseGradering.UGRADERT,
        AdressebeskyttelseGradering.__UNKNOWN_VALUE -> null
    }

/** Deltakerens adressebeskyttelse nå. Null betyr ingen adressebeskyttelse. */
fun interface GraderingOppslag {
    suspend fun hentAdressebeskyttelse(fnr: String): Adressebeskyttelse?
}

/** Feil kastes videre. Personer som ikke finnes i PDL har ingen adressebeskyttelse, som i rutingen. */
class PdlGraderingOppslag(
    private val pdlApiKlient: PdlApiKlient,
) : GraderingOppslag {
    override suspend fun hentAdressebeskyttelse(fnr: String): Adressebeskyttelse? =
        pdlApiKlient.hentAdressebeskyttelse(fnr).fold(
            onSuccess = { person -> person.adressebeskyttelse.map { it.gradering }.hoyesteGradering().tilAdressebeskyttelse() },
            onFailure = { error -> if (error is NotFound) null else throw error },
        )
}
