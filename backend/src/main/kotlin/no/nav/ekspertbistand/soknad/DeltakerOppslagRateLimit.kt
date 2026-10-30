package no.nav.ekspertbistand.soknad

import no.nav.ekspertbistand.infrastruktur.logger
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object DeltakerOppslagTable : LongIdTable("deltaker_oppslag") {
    /** SHA-256 av innlogget brukers fødselsnummer. */
    val innloggetBrukerHash = text("innlogget_bruker_hash")

    /** SHA-256 av deltakerens fødselsnummer. */
    val fnrHash = text("fnr_hash")
    val tidspunkt = timestamp("tidspunkt").defaultExpression(CurrentTimestamp)
}

/**
 * Begrenser hvor mange ulike fødselsnummer en innlogget bruker kan slå opp innenfor et rullerende vindu.
 * Et fødselsnummer som allerede er slått opp i vinduet, kan slås opp igjen (f.eks. mot en annen virksomhet).
 */
@OptIn(ExperimentalTime::class)
class DeltakerOppslagRateLimit(
    private val database: Database,
    private val maksUlikeFnr: Int = 5,
    private val vindu: Duration = 5.minutes,
    private val clock: Clock = Clock.System,
) {
    private val log = logger()

    /**
     * Registrerer oppslaget og returnerer true hvis det er innenfor grensen.
     * Begge fødselsnumrene lagres kun som SHA-256-hash.
     */
    fun registrer(innloggetBruker: String, fnr: String): Boolean = transaction(database) {
        val innloggetBrukerHash = sha256(innloggetBruker)
        exec("SELECT pg_advisory_xact_lock(hashtext(?))", listOf(TextColumnType() to innloggetBrukerHash))

        val naa = clock.now()
        val fnrHash = sha256(fnr)
        val ulikeFnrIVinduet = DeltakerOppslagTable
            .select(DeltakerOppslagTable.fnrHash)
            .where {
                (DeltakerOppslagTable.innloggetBrukerHash eq innloggetBrukerHash) and
                        (DeltakerOppslagTable.tidspunkt greater (naa - vindu))
            }
            .withDistinct()
            .map { it[DeltakerOppslagTable.fnrHash] }
            .toSet()

        val innenforGrensen = fnrHash in ulikeFnrIVinduet || ulikeFnrIVinduet.size < maksUlikeFnr
        if (innenforGrensen) {
            DeltakerOppslagTable.insert {
                it[DeltakerOppslagTable.innloggetBrukerHash] = innloggetBrukerHash
                it[DeltakerOppslagTable.fnrHash] = fnrHash
                it[tidspunkt] = naa
            }
        }
        innenforGrensen
    }

    fun slettGamleOppslag(ttl: Duration = 1.hours) = transaction(database) {
        DeltakerOppslagTable.deleteWhere {
            tidspunkt lessEq (clock.now() - ttl)
        }.let {
            log.info("Slettet $it deltakeroppslag eldre enn $ttl")
        }
    }

    private fun sha256(verdi: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(verdi.toByteArray())
            .toHexString()
}
