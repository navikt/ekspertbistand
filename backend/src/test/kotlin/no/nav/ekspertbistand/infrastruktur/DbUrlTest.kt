package no.nav.ekspertbistand.infrastruktur

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DbUrlTest {

    @Test
    fun `parses jdbc url`() {
        with(DbUrl("jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword")) {
            assertEquals("mydb", database)
            assertEquals("localhost", this.host)
            assertEquals(5432, this.port)
            assertEquals("myuser", username)
            assertEquals("mypassword", password)
            assertEquals("jdbc:postgresql://localhost:5432/mydb", jdbcUrl)
        }
    }

    @Test
    fun `formats to r2dbcUrl`() {
        with(DbUrl("jdbc:postgresql://localhost:5432/mydb?user=myuser&password=mypassword")) {
            assertEquals("r2dbc:postgresql://localhost:5432/mydb", r2dbcUrl)
            assertEquals("myuser", username)
            assertEquals("mypassword", password)
        }
    }
}