package no.nav.ekspertbistand.soknad

/** Bekrefter alle deltakere. For tester som ikke handler om oppslaget. */
val bekreftendeDeltakerOppslag: DeltakerOppslag = FakeDeltakerOppslag()

class FakeDeltakerOppslag(
    var arbeidsforhold: (fnr: String, virksomhetsnummer: String) -> Boolean = { _, _ -> true },
    var navn: (fnr: String) -> String = { "Mona Moonlight" },
) : DeltakerOppslag {
    var antallArbeidsforholdOppslag = 0
        private set
    var antallNavneoppslag = 0
        private set

    override suspend fun harAktivtArbeidsforhold(fnr: String, virksomhetsnummer: String): Boolean {
        antallArbeidsforholdOppslag++
        return arbeidsforhold(fnr, virksomhetsnummer)
    }

    override suspend fun hentNavn(fnr: String): String {
        antallNavneoppslag++
        return navn(fnr)
    }
}
