package no.nav.ekspertbistand.ereg

import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.infrastruktur.teamLogger

class EregService(private val eregClient: EregClient) {
    private val log = logger()
    private val teamLog = teamLogger()

    /**
     * Sektorkoden til juridisk enhet på toppen av kjeden over [virksomhetsnummer], som er en underenhet (`BEDR`).
     *
     * Henter virksomheten med `inkluderHierarki=true`, følger kjeden av organisasjonsledd til toppen, og henter
     * toppen for å lese sektorkoden. Enhetene i hierarkiet har ikke detaljer, så det blir alltid to kall. Er
     * kjeden brutt, slik at toppen er et organisasjonsledd, brukes leddets sektorkode.
     *
     * Gir null og logger error når virksomheten eller toppen ikke finnes (404), virksomheten ikke har enhet over,
     * toppen mangler sektorkode eller kjeden er dypere enn [MAKS_NIVAER]. Kaster ved andre feil fra Ereg, som
     * [hentVirksomhetsnavn], så kalleren kan skille «finnes ikke» fra «Ereg er nede».
     *
     * Se `specifications/kategorisering_virksomhetstype.md`.
     */
    suspend fun hentSektorkode(virksomhetsnummer: String): String? {
        val virksomhet = eregClient.hentOrganisasjonMedHierarkiEllerNull(virksomhetsnummer)
            ?: return manglerSektorkode(virksomhetsnummer, toppOrgnr = null, aarsak = "virksomheten finnes ikke i Ereg")

        val toppOrgnr = when (val topp = finnTopp(virksomhet)) {
            is Topp.Funnet -> topp.orgnr
            is Topp.IkkeFunnet -> return manglerSektorkode(virksomhetsnummer, toppOrgnr = null, aarsak = topp.aarsak)
        }

        val topp = eregClient.hentOrganisasjonEllerNull(toppOrgnr)
            ?: return manglerSektorkode(virksomhetsnummer, toppOrgnr, aarsak = "toppen finnes ikke i Ereg")

        return (topp.juridiskEnhetDetaljer?.sektorkode ?: topp.organisasjonsleddDetaljer?.sektorkode)
            ?.takeIf { it.isNotBlank() }
            ?: manglerSektorkode(virksomhetsnummer, toppOrgnr, aarsak = "toppen mangler sektorkode")
    }

    private fun manglerSektorkode(virksomhetsnummer: String, toppOrgnr: String?, aarsak: String): String? {
        teamLog.error("Fant ikke sektorkode for virksomhet {}, topp {}: {}", virksomhetsnummer, toppOrgnr, aarsak)
        log.error("Fant ikke sektorkode for virksomhet: {}. Detaljer i teamLogs.", aarsak)
        return null
    }

    /**
     * Søk etter organisasjoner på (deler av) navn. Returnerer en forenklet liste med orgnr + navn
     * som brukes til forslag i skjemaet. ENK inkluderes. Ingen tilgangssjekk — kun offentlig
     * Ereg-navnedata eksponeres.
     */
    suspend fun finnOrganisasjonerForNavn(navn: String): List<OrganisasjonSok> {
        return runCatching {
            eregClient.finnOrganisasjon(navn).organisasjonSammendrag.mapNotNull { it.tilOrganisasjonSok() }
        }.onFailure { feil ->
            log.warn("Klarte ikke søke etter organisasjoner i EREG", feil)
        }.getOrDefault(emptyList())
    }

    suspend fun finnOrganisasjonForOrgnr(orgnr: String): List<OrganisasjonSok> {
        return runCatching {
            eregClient.hentOrganisasjon(orgnr).let { organisasjon ->
                val navn = organisasjon.navn?.sammensattnavn ?: organisasjon.navn?.navnelinje1
                val organisasjonsnummer = organisasjon.organisasjonsnummer ?: orgnr
                if (navn == null) emptyList() else listOf(OrganisasjonSok(organisasjonsnummer, navn))
            }
        }.onFailure { feil ->
            log.warn("Klarte ikke hente organisasjon fra EREG for {}", orgnr, feil)
        }.getOrDefault(emptyList())
    }

    /**
     * Navnet på virksomheten i Ereg, eller null når Ereg ikke kjenner orgnr (404) eller organisasjonen mangler navn.
     * Kaster ved andre feil, i motsetning til [finnOrganisasjonForOrgnr], så kalleren kan skille «finnes ikke»
     * fra «Ereg er nede».
     */
    suspend fun hentVirksomhetsnavn(orgnr: String): String? {
        val organisasjon = eregClient.hentOrganisasjonEllerNull(orgnr) ?: return null
        return organisasjon.navn?.sammensattnavn ?: organisasjon.navn?.navnelinje1
    }

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
        /** Vern mot sykluser i Ereg. Dypeste kjede i Brønnøysundregistrene er tre organisasjonsledd. */
        const val MAKS_NIVAER = 10

        private sealed interface Topp {
            data class Funnet(val orgnr: String) : Topp
            data class IkkeFunnet(val aarsak: String) : Topp
        }

        /**
         * Går oppover i det nestede hierarkiet: juridisk enhet i `inngaarIJuridiskEnheter` er toppen, ellers følges
         * første organisasjonsledd i `bestaarAvOrganisasjonsledd` (`BEDR`) eller `organisasjonsleddOver` (`ORGL`).
         * Et organisasjonsledd uten noe over er toppen i en brutt kjede.
         */
        private fun finnTopp(virksomhet: OrganisasjonResponse): Topp {
            var node = virksomhet
            repeat(MAKS_NIVAER) {
                node.inngaarIJuridiskEnheter.firstNotNullOfOrNull { it.organisasjonsnummer }
                    ?.let { return Topp.Funnet(it) }
                val over = (node.bestaarAvOrganisasjonsledd + node.organisasjonsleddOver)
                    .firstNotNullOfOrNull { it.organisasjonsledd }
                if (over == null) {
                    if (node === virksomhet) return Topp.IkkeFunnet("virksomheten har ingen enhet over")
                    return node.organisasjonsnummer?.let { Topp.Funnet(it) }
                        ?: Topp.IkkeFunnet("toppen mangler orgnr")
                }
                node = over
            }
            return Topp.IkkeFunnet("kjeden er dypere enn $MAKS_NIVAER nivåer")
        }

        private fun OrganisasjonSammendrag.tilOrganisasjonSok(): OrganisasjonSok? {
            val orgnr = organisasjonsnummer ?: return null
            val organisasjonsnavn = sammensattnavn ?: return null
            return OrganisasjonSok(organisasjonsnummer = orgnr, navn = organisasjonsnavn)
        }

        private val postadresser = Json.decodeFromString<Map<String, String>>(
            this::class.java.getResource("/poststeder.json")!!.readText()
        )

        private fun String?.orFromMapping(postnummer: String?): String? =
            if (isNullOrEmpty() && !postnummer.isNullOrEmpty()) {
                postadresser[postnummer]
            } else {
                this
            }
        
        private fun Postadresse.toSingleLine(): String? = adresseTilSingleLine(
            adresselinje1,
            adresselinje2,
            adresselinje3,
            postnummer,
            poststed.orFromMapping(postnummer)
        )

        private fun Forretningsadresse.toSingleLine(): String? = adresseTilSingleLine(
            adresselinje1,
            adresselinje2,
            adresselinje3,
            postnummer,
            poststed.orFromMapping(postnummer)
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


@Serializable
data class OrganisasjonSok(
    val organisasjonsnummer: String,
    val navn: String,
)
