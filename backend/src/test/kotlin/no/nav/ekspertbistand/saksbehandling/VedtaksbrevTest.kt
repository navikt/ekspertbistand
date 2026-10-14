package no.nav.ekspertbistand.saksbehandling

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.infrastruktur.defaultJson
import no.nav.ekspertbistand.soknad.DTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class VedtaksbrevTest {

    private val fnr = "01017012345"

    private val soknad = DTO.Soknad(
        virksomhet = DTO.Virksomhet(
            virksomhetsnummer = "123456780",
            virksomhetsnavn = "Eksempel Bedrift AS",
            kontaktperson = DTO.Kontaktperson("Kontaktperson NN", "kontakt@bedrift.no", "41519999"),
        ),
        ansatt = DTO.Ansatt(fnr = fnr, navn = "Ansatt NN"),
        ekspert = DTO.Ekspert(
            navn = "Ekspert NN",
            virksomhet = "ErgoConsult AS",
            godkjentUtdanningEllerAutorisasjon = listOf("Ergoterapeut"),
            relevantKompetanse = emptyList(),
        ),
        behovForBistand = DTO.BehovForBistand(
            begrunnelse = "Skulderplage",
            behov = "Arbeidsplassvurdering",
            estimertKostnad = " 22000 ",
            timer = "16",
            tilrettelegging = "Høydejustert bord",
            startdato = LocalDate(2026, 11, 22),
        ),
        nav = DTO.Nav(kontaktperson = "Navkontaktperson NN"),
    )

    private fun data(
        utfall: ForelopigUtfall,
        mottaker: Mottaker,
        belop: Int? = null,
        begrunnelseArbeidsgiver: String? = "Til arbeidsgiver",
        fra: LocalDate? = LocalDate(2026, 11, 22),
        til: LocalDate? = LocalDate(2026, 12, 31),
    ) = vedtaksbrevData(
        soknad = soknad,
        utfall = utfall,
        belop = belop,
        begrunnelseDeltaker = "Til deltaker",
        begrunnelseArbeidsgiver = begrunnelseArbeidsgiver,
        tiltaksperiodeFra = fra,
        tiltaksperiodeTil = til,
        mottaker = mottaker,
        navEnhet = "Nav Test",
        saksbehandler = "Saksbehandler NN",
        dato = LocalDate(2026, 10, 13),
    )

    @Test
    fun `hver kombinasjon av utfall og mottaker har sin egen mal`() {
        val maler = ForelopigUtfall.entries.flatMap { utfall ->
            Mottaker.entries.map { vedtaksbrevMal(utfall, it) }
        }
        assertEquals(6, maler.toSet().size)
        assertEquals("vedtakDeltakerInnvilget", vedtaksbrevMal(ForelopigUtfall.INNVILGE, Mottaker.DELTAKER))
        assertEquals(
            "vedtakArbeidsgiverInnvilgetRedusert",
            vedtaksbrevMal(ForelopigUtfall.INNVILGE_REDUSERT, Mottaker.ARBEIDSGIVER),
        )
        assertEquals("vedtakArbeidsgiverAvslag", vedtaksbrevMal(ForelopigUtfall.AVSLAA, Mottaker.ARBEIDSGIVER))
    }

    @Test
    fun `brevet har bare begrunnelsen til mottakeren`() {
        val tilDeltaker = data(ForelopigUtfall.AVSLAA, Mottaker.DELTAKER, fra = null, til = null)
        val tilArbeidsgiver = data(ForelopigUtfall.AVSLAA, Mottaker.ARBEIDSGIVER, fra = null, til = null)

        assertEquals("Til deltaker", tilDeltaker.begrunnelse)
        assertFalse(defaultJson.encodeToString(tilDeltaker).contains("Til arbeidsgiver"))
        assertEquals("Til arbeidsgiver", tilArbeidsgiver.begrunnelse)
        assertFalse(defaultJson.encodeToString(tilArbeidsgiver).contains("Til deltaker"))
    }

    @Test
    fun `innvilge bruker søknadssummen som beløp`() {
        val data = data(ForelopigUtfall.INNVILGE, Mottaker.ARBEIDSGIVER, begrunnelseArbeidsgiver = null)

        assertEquals("22\u00A0000", data.innvilgetBelop)
        assertEquals("22\u00A0000", data.soknadssum)
        assertNull(data.begrunnelse)
    }

    @Test
    fun `innvilge redusert bruker beløpet fra vedtaket`() {
        assertEquals(
            "15\u00A0000",
            data(ForelopigUtfall.INNVILGE_REDUSERT, Mottaker.ARBEIDSGIVER, belop = 15000).innvilgetBelop,
        )
    }

    @Test
    fun `datoer og beløp formateres for brev`() {
        val data = data(ForelopigUtfall.INNVILGE, Mottaker.DELTAKER)

        assertEquals("13. oktober 2026", data.dato)
        assertEquals(VedtaksbrevData.Periode("22. november 2026", "31. desember 2026"), data.tiltaksperiode)
        assertEquals("1\u00A0234\u00A0567", 1234567.somBelop())
        assertEquals("500", 500.somBelop())
    }

    @Test
    fun `refusjonsfristen er seks måneder etter sluttdato, og mangler ved avslag`() {
        assertEquals("30. juni 2027", data(ForelopigUtfall.INNVILGE, Mottaker.ARBEIDSGIVER).refusjonsfrist)
        assertEquals(
            "30. juni 2027",
            data(ForelopigUtfall.INNVILGE_REDUSERT, Mottaker.ARBEIDSGIVER, belop = 15000).refusjonsfrist,
        )
        val avslag = data(ForelopigUtfall.AVSLAA, Mottaker.ARBEIDSGIVER, fra = null, til = null)
        assertNull(avslag.refusjonsfrist)
        assertNull(avslag.innvilgetBelop)
        assertNull(avslag.tiltaksperiode)
    }

    @Test
    fun `payloaden har ikke fødselsnummer`() {
        Mottaker.entries.forEach { mottaker ->
            assertFalse(defaultJson.encodeToString(data(ForelopigUtfall.INNVILGE, mottaker)).contains(fnr))
        }
    }

    @Test
    fun `mottaker tolkes uavhengig av store og små bokstaver`() {
        assertEquals(Mottaker.DELTAKER, Mottaker.fra("deltaker"))
        assertEquals(Mottaker.ARBEIDSGIVER, Mottaker.fra("ARBEIDSGIVER"))
        assertNull(Mottaker.fra("ekspert"))
        assertNull(Mottaker.fra(null))
    }
}
