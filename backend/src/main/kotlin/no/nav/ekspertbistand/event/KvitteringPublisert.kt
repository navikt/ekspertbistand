package no.nav.ekspertbistand.event

import no.nav.ekspertbistand.arena.Saksnummer
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.tilsagndata.concat
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Hvilke kvitteringshendelser ([EventData.ArbeidsgiverVarslet], [EventData.ArenaNotatJournalfoert]) som er
 * publisert. Nøkkelen er den samme som det eksterne kallet bruker, så én varsling eller ett notat gir én
 * kvittering, også ved retry og når samme hendelse kommer flere ganger.
 *
 * Rader for hendelser fra før tabellen fantes, backfilles av
 * [no.nav.ekspertbistand.event.projections.KvitteringPublisertProjection].
 */
@OptIn(ExperimentalTime::class)
object KvitteringPublisert : Table("kvittering_publisert") {
    val nokkel = text("nokkel")
    val soknadId = text("soknad_id")
    val type = enumerationByName<Type>("type", 64)
    val registrert = timestamp("registrert").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(nokkel)

    enum class Type {
        ARBEIDSGIVER_VARSLET_SOKNAD_MOTTATT,
        ARBEIDSGIVER_VARSLET_SOKNAD_GODKJENT,
        ARBEIDSGIVER_VARSLET_SOKNAD_AVLYST,
        ARENA_NOTAT_JOURNALFOERT,
    }

    /** Samme verdi som `eksternId` på beskjeden i Notifikasjonsplattform. */
    fun soknadMottattNokkel(soknadId: String) = "$soknadId-soknad-mottatt"

    /** Samme verdi som `eksternId` på beskjeden i Notifikasjonsplattform. Én per tilsagn. */
    fun soknadGodkjentNokkel(soknadId: String, tilsagnNummer: TilsagnData.TilsagnNummer) =
        "$soknadId-godkjent-${tilsagnNummer.concat()}"

    /** Samme verdi som `eksternId` på beskjeden i Notifikasjonsplattform. */
    fun soknadAvlystNokkel(soknadId: String) = "$soknadId-soknad-avlyst"

    /** Samme verdi som `eksternReferanseId` på journalposten i DokArkiv. */
    fun arenaNotatNokkel(saksnummer: Saksnummer, tiltaksgjennomfoeringId: Int) =
        "notat-$saksnummer-$tiltaksgjennomfoeringId"

    /**
     * Registrerer at kvitteringen for [nokkel] er publisert. Returnerer `true` bare hvis raden ble satt inn,
     * altså hvis kvitteringen ikke er publisert fra før. Må kalles i en pågående transaksjon, og i samme
     * transaksjon som `publishEventQueue`, slik at registreringen og publiseringen committes eller rulles
     * tilbake sammen.
     */
    fun registrer(
        nokkel: String,
        soknadId: String,
        type: Type,
        registrert: Instant? = null,
    ): Boolean =
        insertIgnore {
            it[KvitteringPublisert.nokkel] = nokkel
            it[KvitteringPublisert.soknadId] = soknadId
            it[KvitteringPublisert.type] = type
            if (registrert != null) it[KvitteringPublisert.registrert] = registrert
        }.insertedCount > 0
}
