package no.nav.ekspertbistand.soknad

import io.ktor.client.plugins.*
import io.ktor.http.*
import no.nav.ekspertbistand.aareg.AaregClient
import no.nav.ekspertbistand.pdl.PdlApiKlient

/**
 * Oppslag mot eksterne registre når arbeidsgiver legger inn en deltaker.
 * Funksjonene kaster ved feil mot registrene.
 */
interface DeltakerOppslag {
    /** True hvis personen har minst ett aktivt arbeidsforhold i virksomheten. */
    suspend fun harAktivtArbeidsforhold(fnr: String, virksomhetsnummer: String): Boolean

    suspend fun hentNavn(fnr: String): String
}

class AaregPdlDeltakerOppslag(
    private val aaregClient: AaregClient,
    private val pdlApiKlient: PdlApiKlient,
) : DeltakerOppslag {
    override suspend fun harAktivtArbeidsforhold(fnr: String, virksomhetsnummer: String): Boolean {
        val arbeidsforhold = try {
            aaregClient.hentArbeidsforhold(fnr, virksomhetsnummer)
        } catch (e: ClientRequestException) {
            // Aa-registeret svarer 404 når personen ikke finnes der.
            if (e.response.status == HttpStatusCode.NotFound) return false
            throw e
        }
        return arbeidsforhold.any { forhold ->
            forhold.arbeidssted?.identer.orEmpty().any { it.ident == virksomhetsnummer }
        }
    }

    override suspend fun hentNavn(fnr: String): String = pdlApiKlient.hentNavn(fnr).getOrThrow()
}
