package no.nav.ekspertbistand.ereg

import no.nav.ekspertbistand.infrastruktur.logger

class EregService(private val eregClient: EregClient) {
    private val log = logger()

    suspend fun hentPostAdresse(orgnr: String): String? {
        return runCatching {
            eregClient.hentPostAdresse(orgnr).firstNotNullOfOrNull { it.toSingleLine() }
        }.onFailure { feil ->
            log.warn("Klarte ikke hente postadresse fra EREG for {}", orgnr, feil)
        }.getOrNull()
    }

    suspend fun hentForretningsadresse(orgnr: String): String? {
        return runCatching {
            eregClient.hentForretningsadresse(orgnr).firstNotNullOfOrNull { it.toSingleLine() }
        }.onFailure { feil ->
            log.warn("Klarte ikke hente forretningsadresse fra EREG for {}", orgnr, feil)
        }.getOrNull()
    }

    companion object {
        private fun Postadresse.toSingleLine(): String? = adresseTilSingleLine(
            adresselinje1,
            adresselinje2,
            adresselinje3,
            postnummer,
            poststed
        )

        private fun Forretningsadresse.toSingleLine(): String? = adresseTilSingleLine(
            adresselinje1,
            adresselinje2,
            adresselinje3,
            postnummer,
            poststed
        )

        private fun adresseTilSingleLine(
            linje1: String?,
            linje2: String?,
            linje3: String?,
            postnummer: String?,
            poststed: String?
        ): String? {
            val poststedDel = listOfNotNull(postnummer, poststed)
                .joinToString(" ")
                .trim()
                .ifBlank { null }

            val deler = buildList {
                listOf(linje1, linje2, linje3)
                    .mapNotNull { it?.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { add(it) }
                poststedDel?.let { add(it) }
            }

            return deler.joinToString(", ").ifBlank { null }
        }
    }
}

