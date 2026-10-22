package no.nav.ekspertbistand.statistikk

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryException
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.StandardSQLTypeName.DATE
import com.google.cloud.bigquery.StandardSQLTypeName.INT64
import com.google.cloud.bigquery.StandardSQLTypeName.STRING
import com.google.cloud.bigquery.StandardSQLTypeName.TIMESTAMP
import com.google.cloud.bigquery.StandardTableDefinition
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.TableInfo
import com.google.cloud.bigquery.ViewDefinition
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.infrastruktur.logger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun interface SaksbehandlingsstatistikkSkriver {
    fun skriv(rad: SaksbehandlingsstatistikkRad)
}

/**
 * Behandlinger som holdes utenfor viewet vi deler, fordi deltakeren har eller har hatt adressebeskyttelse.
 * En behandling fjernes aldri fra listen.
 */
interface AdressebeskyttedeBehandlinger {
    fun registrer(behandlingIder: Collection<String>)

    /** Behandlinger med rader i rådatatabellen som ikke er registrert som adressebeskyttet, med deltakerens fnr. */
    fun utenAdressebeskyttelse(): Map<String, String>
}

/** Én rad i BigQuery-tabellen. Feltene er beskrevet i `README.md` i samme pakke. */
@OptIn(ExperimentalTime::class)
data class SaksbehandlingsstatistikkRad(
    val sekvensnummer: Long,
    val sakId: String,
    val sakNummer: String,
    val behandlingId: String,
    val fnr: String,
    val hendelseTid: Instant,
    val meldingTid: Instant,
    val mottattTid: Instant,
    val registrertTid: Instant,
    val ferdigbehandletTid: Instant?,
    val forventetOppstartTid: LocalDate?,
    val sakYtelse: String,
    val sakUtland: String,
    val behandlingType: String,
    val behandlingStatus: String,
    val behandlingResultat: String?,
    val resultatBegrunnelse: String?,
    val behandlingMetode: String,
    val opprettetAv: String,
    val saksbehandler: String?,
    val ansvarligBeslutter: String?,
    val ansvarligEnhet: String?,
    val vedtaksloesningNavn: String,
    val vedtaksloesningVersjon: String,
    val tiltakskodeArena: String,
    val arrangorOrgnr: String?,
    val estimertAntallTimer: Int?,
    val estimertKostnad: Int,
    val ekspertVirksomhetOrgnr: String?,
    val ekspertUtdanning: String?,
    val ekspertKompetanse: String?,
    val innvilgetBelop: Int?,
    val tiltaksperiodeFra: LocalDate?,
    val tiltaksperiodeTil: LocalDate?,
    val projeksjon: String,
) {
    /** BigQuery bruker id-en til å fjerne duplikater ved nye forsøk, så lenge forsøkene kommer tett. */
    val insertId: String get() = "$projeksjon-$sekvensnummer"

    override fun toString() = "SaksbehandlingsstatistikkRad(insertId=$insertId, behandlingStatus=$behandlingStatus)"
}

/**
 * Eier objektene i datasettet [DATASETT], som Nais oppretter fra `spec.gcp.bigQueryDatasets`:
 * - [TABELL]: rådata. Deles ikke.
 * - [ADRESSEBESKYTTEDE]: behandlinger som skal holdes utenfor. Deles ikke.
 * - [VIEW]: rådata uten adressebeskyttede behandlinger. Bare dette registreres på Datamarkedsplassen, som autoriserer
 *   viewet mot tabellene i samme datasett.
 *
 * Appen oppretter det som mangler første gang den bruker datasettet, og oppdaterer viewet slik at skjemaet følger
 * tabellen. Nye kolonner må legges til i BigQuery før appen skriver dem, fordi tabellen ikke oppdateres automatisk.
 */
@OptIn(ExperimentalTime::class)
class BigQuerySaksbehandlingsstatistikk(
    private val bigQuery: BigQuery,
    prosjekt: String,
) : SaksbehandlingsstatistikkSkriver, AdressebeskyttedeBehandlinger {

    private val tabell = TableId.of(prosjekt, DATASETT, TABELL)
    private val adressebeskyttede = TableId.of(prosjekt, DATASETT, ADRESSEBESKYTTEDE)
    private val view = TableId.of(prosjekt, DATASETT, VIEW)

    private val log = logger()

    private val datasettKlart by lazy {
        opprettEllerUtvid(tabell, StandardTableDefinition.of(skjema))
        opprettEllerUtvid(adressebeskyttede, StandardTableDefinition.of(adressebeskyttedeSkjema))
        opprettEllerOppdaterView()
    }

    /**
     * Datamarkedsplassen autoriserer viewet mot datasettet. Etter det krever BigQuery `bigquery.datasets.update` for å
     * endre viewet, og den rettigheten får ikke appen fra Nais. Derfor endrer vi viewet bare når noe er annerledes.
     */
    private fun opprettEllerOppdaterView() {
        val sql = viewSql(tabell, adressebeskyttede)
        val definisjon = TableInfo.of(view, ViewDefinition.of(sql))
        val eksisterende = bigQuery.getTable(view) ?: return run { bigQuery.create(definisjon) }
        when (viewEndring(eksisterende.getDefinition(), sql, skjema)) {
            ViewEndring.INGEN -> {}
            ViewEndring.SPORRING -> bigQuery.update(definisjon)
            ViewEndring.KOLONNER -> try {
                bigQuery.update(definisjon)
            } catch (e: BigQueryException) {
                // Viewet leser r.* når det spørres, så mottakerne får de nye kolonnene. Bare skjemaet viewet viser er utdatert.
                log.error("Kunne ikke oppdatere kolonnene i $view. Lagre viewet på nytt med en bruker som har tilgang.", e)
            }
        }
    }

    private fun opprettEllerUtvid(id: TableId, definisjon: StandardTableDefinition) {
        val eksisterende = bigQuery.getTable(id) ?: return run { bigQuery.create(TableInfo.of(id, definisjon)) }
        val felter = eksisterende.getDefinition<StandardTableDefinition>().schema?.fields.orEmpty()
        val nye = definisjon.schema!!.fields.filter { ny -> felter.none { it.name == ny.name } }
        if (nye.isNotEmpty()) {
            // BigQuery tillater bare å legge til valgfrie kolonner i en eksisterende tabell.
            check(nye.none { it.mode == Field.Mode.REQUIRED }) { "Kan ikke legge til påkrevde kolonner i $id: $nye" }
            eksisterende.toBuilder()
                .setDefinition(StandardTableDefinition.of(Schema.of(felter + nye)))
                .build()
                .update()
        }
    }

    override fun skriv(rad: SaksbehandlingsstatistikkRad) {
        datasettKlart
        settInn(tabell, listOf(rad.insertId to rad.tilBigQuery()))
    }

    override fun registrer(behandlingIder: Collection<String>) {
        if (behandlingIder.isEmpty()) return
        datasettKlart
        val tidspunkt = Clock.System.now().toString()
        settInn(adressebeskyttede, behandlingIder.map { it to mapOf("behandling_id" to it, "registrert_tid" to tidspunkt) })
    }

    override fun utenAdressebeskyttelse(): Map<String, String> {
        datasettKlart
        val allerede = bigQuery.listTableData(adressebeskyttede, adressebeskyttedeSkjema).iterateAll()
            .map { it.get("behandling_id").stringValue }
            .toSet()
        return bigQuery.listTableData(tabell, skjema).iterateAll()
            .associate { it.get("behandling_id").stringValue to it.get("fnr").stringValue }
            .filterKeys { it !in allerede }
    }

    private fun settInn(id: TableId, rader: List<Pair<String, Map<String, Any>>>) {
        val svar = bigQuery.insertAll(
            InsertAllRequest.newBuilder(id).apply { rader.forEach { (insertId, verdier) -> addRow(insertId, verdier) } }.build()
        )
        check(!svar.hasErrors()) {
            val feil = svar.insertErrors.values.flatten().map { "${it.reason} (${it.location})" }
            "Kunne ikke skrive ${svar.insertErrors.size} av ${rader.size} rader til $id: $feil"
        }
    }

    companion object {
        const val DATASETT = "ekspertbistand_statistikk"
        const val TABELL = "saksbehandlingsstatistikk"
        const val ADRESSEBESKYTTEDE = "adressebeskyttede_behandlinger"
        const val VIEW = "saksbehandlingsstatistikk_delt"

        fun fraNaisMiljo(): BigQuerySaksbehandlingsstatistikk {
            val prosjekt = System.getenv("GCP_TEAM_PROJECT_ID")
                ?: error("Mangler GCP_TEAM_PROJECT_ID")
            return BigQuerySaksbehandlingsstatistikk(
                bigQuery = BigQueryOptions.newBuilder().setProjectId(prosjekt).build().service,
                prosjekt = prosjekt,
            )
        }
    }
}

private fun viewSql(tabell: TableId, adressebeskyttede: TableId): String =
    """
    SELECT r.*
    FROM `${tabell.project}.${tabell.dataset}.${tabell.table}` r
    WHERE r.behandling_id NOT IN (
      SELECT behandling_id FROM `${adressebeskyttede.project}.${adressebeskyttede.dataset}.${adressebeskyttede.table}`
    )
    """.trimIndent()

internal enum class ViewEndring { INGEN, KOLONNER, SPORRING }

internal fun viewEndring(eksisterende: ViewDefinition, sql: String, skjema: Schema): ViewEndring {
    val kolonnerIViewet = eksisterende.schema?.fields.orEmpty().map { it.name }.toSet()
    return when {
        eksisterende.query != sql -> ViewEndring.SPORRING
        kolonnerIViewet != skjema.fields.map { it.name }.toSet() -> ViewEndring.KOLONNER
        else -> ViewEndring.INGEN
    }
}

private val adressebeskyttedeSkjema: Schema = Schema.of(
    Field.newBuilder("behandling_id", STRING).setMode(Field.Mode.REQUIRED).build(),
    Field.newBuilder("registrert_tid", TIMESTAMP).setMode(Field.Mode.REQUIRED).build(),
)

private class Kolonne(
    val navn: String,
    val type: StandardSQLTypeName,
    val paakrevd: Boolean,
    val verdi: (SaksbehandlingsstatistikkRad) -> Any?,
)

@OptIn(ExperimentalTime::class)
private val kolonner = listOf(
    Kolonne("sekvensnummer", INT64, true) { it.sekvensnummer },
    Kolonne("sak_id", STRING, true) { it.sakId },
    Kolonne("sak_nummer", STRING, true) { it.sakNummer },
    Kolonne("behandling_id", STRING, true) { it.behandlingId },
    Kolonne("fnr", STRING, true) { it.fnr },
    Kolonne("hendelse_tid", TIMESTAMP, true) { it.hendelseTid },
    Kolonne("melding_tid", TIMESTAMP, true) { it.meldingTid },
    Kolonne("mottatt_tid", TIMESTAMP, true) { it.mottattTid },
    Kolonne("registrert_tid", TIMESTAMP, true) { it.registrertTid },
    Kolonne("ferdigbehandlet_tid", TIMESTAMP, false) { it.ferdigbehandletTid },
    Kolonne("forventet_oppstart_tid", DATE, false) { it.forventetOppstartTid },
    Kolonne("sak_ytelse", STRING, true) { it.sakYtelse },
    Kolonne("sak_utland", STRING, true) { it.sakUtland },
    Kolonne("behandling_type", STRING, true) { it.behandlingType },
    Kolonne("behandling_status", STRING, true) { it.behandlingStatus },
    Kolonne("behandling_resultat", STRING, false) { it.behandlingResultat },
    Kolonne("resultat_begrunnelse", STRING, false) { it.resultatBegrunnelse },
    Kolonne("behandling_metode", STRING, true) { it.behandlingMetode },
    Kolonne("opprettet_av", STRING, true) { it.opprettetAv },
    Kolonne("saksbehandler", STRING, false) { it.saksbehandler },
    Kolonne("ansvarlig_beslutter", STRING, false) { it.ansvarligBeslutter },
    Kolonne("ansvarlig_enhet", STRING, false) { it.ansvarligEnhet },
    Kolonne("vedtaksloesning_navn", STRING, true) { it.vedtaksloesningNavn },
    Kolonne("vedtaksloesning_versjon", STRING, true) { it.vedtaksloesningVersjon },
    Kolonne("tiltakskode_arena", STRING, true) { it.tiltakskodeArena },
    Kolonne("arrangor_orgnr", STRING, false) { it.arrangorOrgnr },
    Kolonne("estimert_antall_timer", INT64, false) { it.estimertAntallTimer },
    Kolonne("estimert_kostnad", INT64, true) { it.estimertKostnad },
    Kolonne("ekspert_virksomhet_orgnr", STRING, false) { it.ekspertVirksomhetOrgnr },
    Kolonne("ekspert_utdanning", STRING, false) { it.ekspertUtdanning },
    Kolonne("ekspert_kompetanse", STRING, false) { it.ekspertKompetanse },
    Kolonne("innvilget_belop", INT64, false) { it.innvilgetBelop },
    Kolonne("tiltaksperiode_fra", DATE, false) { it.tiltaksperiodeFra },
    Kolonne("tiltaksperiode_til", DATE, false) { it.tiltaksperiodeTil },
    Kolonne("projeksjon", STRING, true) { it.projeksjon },
)

internal val skjema: Schema = Schema.of(
    kolonner.map {
        Field.newBuilder(it.navn, it.type)
            .setMode(if (it.paakrevd) Field.Mode.REQUIRED else Field.Mode.NULLABLE)
            .build()
    }
)

/** Verdiene slik BigQuery tar imot dem i JSON. Tidspunkt og datoer blir ISO 8601. Tomme felter utelates. */
@OptIn(ExperimentalTime::class)
internal fun SaksbehandlingsstatistikkRad.tilBigQuery(): Map<String, Any> =
    kolonner.mapNotNull { kolonne ->
        when (val verdi = kolonne.verdi(this)) {
            null -> null
            is Instant, is LocalDate -> kolonne.navn to verdi.toString()
            else -> kolonne.navn to verdi
        }
    }.toMap()
