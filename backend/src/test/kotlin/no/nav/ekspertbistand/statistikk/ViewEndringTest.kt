package no.nav.ekspertbistand.statistikk

import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName.STRING
import com.google.cloud.bigquery.ViewDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewEndringTest {
    private val sql = "SELECT r.* FROM t r"

    private fun view(query: String, kolonner: Schema) = ViewDefinition.newBuilder(query).setSchema(kolonner).build()

    @Test
    fun `uendret view skal ikke oppdateres`() {
        assertEquals(ViewEndring.INGEN, viewEndring(view(sql, skjema), sql, skjema))
    }

    @Test
    fun `ny kolonne i tabellen gir oppdatering av kolonner`() {
        val utenSisteKolonne = Schema.of(skjema.fields.dropLast(1))
        assertEquals(ViewEndring.KOLONNER, viewEndring(view(sql, utenSisteKolonne), sql, skjema))
    }

    @Test
    fun `endret spørring gir oppdatering av spørringen også når kolonnene er endret`() {
        val annenKolonne = Schema.of(Field.of("annen", STRING))
        assertEquals(ViewEndring.SPORRING, viewEndring(view("SELECT * FROM t", annenKolonne), sql, skjema))
    }
}
