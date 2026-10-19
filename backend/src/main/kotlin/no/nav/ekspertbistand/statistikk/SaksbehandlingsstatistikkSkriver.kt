package no.nav.ekspertbistand.statistikk

import com.google.cloud.bigquery.BigQuery
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
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun interface SaksbehandlingsstatistikkSkriver {
    fun skriv(rad: SaksbehandlingsstatistikkRad)
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
 * Skriver til tabellen [TABELL] i datasettet [DATASETT], som Nais oppretter fra `spec.gcp.bigQueryDatasets`.
 * Appen oppretter tabellen selv første gang den skriver. Nye kolonner må legges til i BigQuery før
 * appen skriver dem, fordi tabellen ikke oppdateres automatisk.
 */
class BigQuerySaksbehandlingsstatistikkSkriver(
    private val bigQuery: BigQuery,
    private val tabell: TableId,
) : SaksbehandlingsstatistikkSkriver {

    private val tabellFinnes by lazy {
        if (bigQuery.getTable(tabell) == null) {
            bigQuery.create(TableInfo.of(tabell, StandardTableDefinition.of(skjema)))
        }
    }

    override fun skriv(rad: SaksbehandlingsstatistikkRad) {
        tabellFinnes
        val svar = bigQuery.insertAll(
            InsertAllRequest.newBuilder(tabell).addRow(rad.insertId, rad.tilBigQuery()).build()
        )
        check(!svar.hasErrors()) {
            val feil = svar.insertErrors.values.flatten().map { "${it.reason} (${it.location})" }
            "Kunne ikke skrive ${rad.insertId} til $tabell: $feil"
        }
    }

    companion object {
        const val DATASETT = "ekspertbistand_statistikk"
        const val TABELL = "saksbehandlingsstatistikk"

        fun fraNaisMiljo(): BigQuerySaksbehandlingsstatistikkSkriver {
            val prosjekt = System.getenv("GCP_TEAM_PROJECT_ID")
                ?: error("Mangler GCP_TEAM_PROJECT_ID")
            return BigQuerySaksbehandlingsstatistikkSkriver(
                bigQuery = BigQueryOptions.newBuilder().setProjectId(prosjekt).build().service,
                tabell = TableId.of(prosjekt, DATASETT, TABELL),
            )
        }
    }
}

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
