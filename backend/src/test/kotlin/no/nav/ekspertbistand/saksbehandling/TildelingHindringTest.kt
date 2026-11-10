package no.nav.ekspertbistand.saksbehandling

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TildelingHindringTest {

    private val meg = "A123456"
    private val kollega = "Z999999"
    private val saksbehandler = principal(meg, Role.SAKSBEHANDLER)
    private val ny = KildeTilBehandling.EKSPERTBISTAND
    private val ingen = MottatteInnsendinger.INGEN
    private val mottatt = Instant.parse("2026-11-01T10:00:00Z")

    @Test
    fun `sak som venter på saksbehandler kan tildeles, ledig eller hos en kollega`() {
        listOf(Saksstatus.OPPRETTET, Saksstatus.UNDER_BEHANDLING).forEach { status ->
            KildeTilBehandling.entries.forEach { kilde ->
                listOf(null, kollega).forEach { ident ->
                    assertNull(tildelingHindring(status, kilde, ingen, ident, saksbehandler), "status=$status kilde=$kilde ident=$ident")
                }
            }
        }
    }

    @Test
    fun `sak som ikke venter på saksbehandler gir UGYLDIG_STATUS`() {
        listOf(
            Saksstatus.TIL_BESLUTNING,
            Saksstatus.INNVILGET,
            Saksstatus.AVSLATT,
            Saksstatus.AVSLUTTET,
        ).forEach {
            assertEquals(TildelingHindring.UGYLDIG_STATUS, tildelingHindring(it, ny, ingen, kollega, saksbehandler), "status=$it")
        }
    }

    @Test
    fun `innvilget sak fra ny løsning med mottatt refusjonskrav eller sluttrapport kan tildeles`() {
        listOf(
            MottatteInnsendinger(refusjonskrav = mottatt, sluttrapport = null),
            MottatteInnsendinger(refusjonskrav = null, sluttrapport = mottatt),
            MottatteInnsendinger(refusjonskrav = mottatt, sluttrapport = mottatt),
        ).forEach { innsendinger ->
            listOf(null, kollega).forEach { ident ->
                assertNull(tildelingHindring(Saksstatus.INNVILGET, ny, innsendinger, ident, saksbehandler), "$innsendinger ident=$ident")
            }
        }
    }

    @Test
    fun `innsending gjør ikke Arena-saker eller saker som ikke er innvilget tildelbare`() {
        val begge = MottatteInnsendinger(refusjonskrav = mottatt, sluttrapport = mottatt)
        assertEquals(
            TildelingHindring.UGYLDIG_STATUS,
            tildelingHindring(Saksstatus.INNVILGET, KildeTilBehandling.ARENA, begge, kollega, saksbehandler),
        )
        listOf(Saksstatus.TIL_BESLUTNING, Saksstatus.AVSLATT, Saksstatus.AVSLUTTET).forEach {
            assertEquals(TildelingHindring.UGYLDIG_STATUS, tildelingHindring(it, ny, begge, kollega, saksbehandler), "status=$it")
        }
    }

    @Test
    fun `uten saksbehandlerrolle gir MANGLER_ROLLE`() {
        assertEquals(
            TildelingHindring.MANGLER_ROLLE,
            tildelingHindring(Saksstatus.OPPRETTET, ny, ingen, null, principal(meg, Role.BESLUTTER)),
        )
    }

    @Test
    fun `innlogget har saken allerede gir HAR_SAKEN_ALLEREDE`() {
        assertEquals(
            TildelingHindring.HAR_SAKEN_ALLEREDE,
            tildelingHindring(Saksstatus.UNDER_BEHANDLING, ny, ingen, meg, saksbehandler),
        )
        assertEquals(
            TildelingHindring.HAR_SAKEN_ALLEREDE,
            tildelingHindring(Saksstatus.INNVILGET, ny, MottatteInnsendinger(mottatt, null), meg, saksbehandler),
        )
    }
}
