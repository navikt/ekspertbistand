package no.nav.ekspertbistand.oebs

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * 🔴 Kontraktstest — verifiserer at vår lokale speiling av [OebsBestillingMelding] serialiseres
 * eksakt likt som Team VALP forventer (feltnavn, `type`-diskriminator og verdityper som
 * [Periode]/[Organisasjonsnummer]).
 *
 * Skjelett inntil vi har en kjent-god melding fra VALP å sammenligne mot. Denne må være grønn før
 * produksjonssetting — feil wire-format gir avviste økonomimeldinger.
 */
class OebsBestillingMeldingContractTest {

    @Ignore // TODO: fyll ut mot kjent-god melding fra Team VALP (mulighetsrommet)
    @Test
    fun `bestilling serialiseres identisk med VALP-kontrakten`() {
        TODO("Sammenlign OebsBestillingMelding.json.encodeToString(...) med forventet JSON fra VALP.")
    }
}
