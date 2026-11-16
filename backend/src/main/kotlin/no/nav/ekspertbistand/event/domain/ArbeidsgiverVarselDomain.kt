package no.nav.ekspertbistand.event.domain

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.handlers.tilFødselsdato
import no.nav.ekspertbistand.notifikasjon.merkelapp
import no.nav.ekspertbistand.notifikasjon.sendevindu
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.kvitteringsLenke
import no.nav.ekspertbistand.tilsagndata.concat

/**
 * Et varsel til arbeidsgiver i notifikasjonsplattformen, slik vi bestilte det: eventuelt ny sak, beskjeden med
 * eventuelt eksternt varsel, og eventuelt ny status på saken. Har med det klienten bestemmer (merkelapp, mottaker og
 * sendevindu), fordi det avgjør hvem som får varselet og når.
 *
 * [sak] har deltakers navn og fødselsdato i [Sak.tittel], slik arbeidsgiver ser saken. Er [sak] satt, har objektet
 * personopplysninger og skal ikke logges. Ellers har det bare organisasjonsdata, søknads-id i lenkene og faste
 * tekster.
 *
 * [sak] har standardverdi fordi eldre eventer (bare i dev) mangler feltet.
 */
@Serializable
data class ArbeidsgiverVarselDomain(
    val merkelapp: String,
    val mottaker: Mottaker,
    val virksomhetsnummer: String,
    val grupperingsid: String,
    val sak: Sak? = null,
    val beskjed: Beskjed,
    val saksstatus: Saksstatus?,
) {
    @Serializable
    data class Mottaker(val altinnRessursId: String)

    @Serializable
    data class Sak(
        val tittel: String,
        val lenke: String,
    )

    @Serializable
    data class Beskjed(
        val eksternId: String,
        val tekst: String,
        val lenke: String,
        val eksterntVarsel: EksterntVarsel?,
    )

    @Serializable
    data class EksterntVarsel(
        val epostTittel: String,
        val epostHtmlBody: String,
        val smsTekst: String,
        val sendevindu: Sendevindu,
    )

    /** [nyLenkeTilSak] er `null` når lenken på saken skal stå uendret. */
    @Serializable
    data class Saksstatus(
        val status: Status,
        val statusTekst: String,
        val nyLenkeTilSak: String?,
    )

    @Serializable
    enum class Status { FERDIG }

    @Serializable
    enum class Sendevindu { NKS_AAPNINGSTID }

    companion object {
        /** Sak og beskjed om mottatt søknad. Likt for søknader som behandles i Arena og i ny løsning. */
        fun soknadMottatt(soknad: DTO.Soknad): ArbeidsgiverVarselDomain {
            val soknadId = requireNotNull(soknad.id) { "Søknaden mangler id" }
            return ArbeidsgiverVarselDomain(
                merkelapp = merkelapp,
                mottaker = Mottaker(altinnRessursId = altinn3Ressursid),
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                grupperingsid = soknadId,
                sak = Sak(
                    tittel = "Ekspertbistand ${soknad.ansatt.navn} f. ${soknad.ansatt.fnr.tilFødselsdato()}",
                    lenke = soknad.kvitteringsLenke,
                ),
                beskjed = Beskjed(
                    eksternId = "$soknadId-soknad-mottatt",
                    tekst = "Nav har mottatt deres søknad om ekspertbistand.",
                    lenke = soknad.kvitteringsLenke,
                    eksterntVarsel = null,
                ),
                saksstatus = null,
            )
        }

        /** Beskjed og ferdig sak når Arena har godkjent søknaden. Én per tilsagn. */
        fun soknadGodkjent(
            soknad: DTO.Soknad,
            tilsagnNummer: TilsagnData.TilsagnNummer,
            godkjentFlereGanger: Boolean,
        ): ArbeidsgiverVarselDomain {
            val soknadId = requireNotNull(soknad.id) { "Søknaden mangler id" }
            val eksterntVarselTekst = "${soknad.virksomhet.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. " +
                "Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det."
            return ArbeidsgiverVarselDomain(
                merkelapp = merkelapp,
                mottaker = Mottaker(altinnRessursId = altinn3Ressursid),
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                grupperingsid = soknadId,
                sak = null,
                beskjed = Beskjed(
                    eksternId = "$soknadId-godkjent-${tilsagnNummer.concat()}",
                    tekst = if (godkjentFlereGanger) {
                        "Søknaden er godkjent på nytt, se oppdaterte opplysninger."
                    } else {
                        "Søknaden er godkjent og ekspertbistand kan nå tas i bruk."
                    },
                    lenke = soknad.kvitteringsLenke,
                    eksterntVarsel = EksterntVarsel(
                        epostTittel = "Nav – angående søknad om ekspertbistand",
                        epostHtmlBody = eksterntVarselTekst,
                        smsTekst = eksterntVarselTekst,
                        sendevindu = sendevindu.tilDomain(),
                    ),
                ),
                saksstatus = Saksstatus(
                    status = Status.FERDIG,
                    statusTekst = "Søknad godkjent",
                    nyLenkeTilSak = null,
                ),
            )
        }

        /** Beskjed og ferdig sak når søknaden er avlyst i Arena. */
        fun soknadAvlyst(soknad: DTO.Soknad): ArbeidsgiverVarselDomain {
            val soknadId = requireNotNull(soknad.id) { "Søknaden mangler id" }
            val eksterntVarselTekst = "${soknad.virksomhet.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. " +
                "Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det."
            return ArbeidsgiverVarselDomain(
                merkelapp = merkelapp,
                mottaker = Mottaker(altinnRessursId = altinn3Ressursid),
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                grupperingsid = soknadId,
                sak = null,
                beskjed = Beskjed(
                    eksternId = "$soknadId-soknad-avlyst",
                    tekst = "Søknaden om ekspertbistand trukket eller avslått.",
                    lenke = soknad.kvitteringsLenke,
                    eksterntVarsel = EksterntVarsel(
                        epostTittel = "Nav – angående søknad om ekspertbistand",
                        epostHtmlBody = eksterntVarselTekst,
                        smsTekst = eksterntVarselTekst,
                        sendevindu = sendevindu.tilDomain(),
                    ),
                ),
                saksstatus = Saksstatus(
                    status = Status.FERDIG,
                    statusTekst = "Søknad trukket eller avslått",
                    nyLenkeTilSak = null,
                ),
            )
        }
    }
}
