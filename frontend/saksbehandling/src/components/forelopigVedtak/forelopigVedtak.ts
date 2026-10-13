import type { ForelopigVedtakInput } from "../../hooks/useForelopigVedtak";
import type { Standardtekst } from "../../hooks/useForelopigVedtakOppsett";
import type { ForelopigUtfall, SakDetaljer } from "../../hooks/useSak";
import { formatKroner, formatTusenskille, tolkSoknadssum } from "../../utils/belop";
import { gjeldendeMaksSats, type MaksSats } from "../../utils/maksSats";

export const UTFALL_TEKST: Record<ForelopigUtfall, string> = {
  INNVILGE: "Innvilge",
  INNVILGE_REDUSERT: "Innvilge med redusert beløp",
  AVSLAA: "Avslå",
};

export type Mottaker = "deltaker" | "arbeidsgiver";

export const harBegrunnelseTilArbeidsgiver = (utfall: ForelopigUtfall) => utfall !== "INNVILGE";

/** «Tilpass» bare der feltet starter med en standardtekst, dvs. til arbeidsgiver ved redusert beløp. */
export const begrunnelseLabel = (utfall: ForelopigUtfall, mottaker: Mottaker) =>
  mottaker === "arbeidsgiver" && utfall === "INNVILGE_REDUSERT"
    ? "Arbeidsgiver - tilpass begrunnelse"
    : `Begrunnelse til ${mottaker}`;

export const harTiltaksperiode = (utfall: ForelopigUtfall) => utfall !== "AVSLAA";

/** Skjemaet holdes i `SakPage`, slik at fanebytte og «Forrige» ikke mister det som er skrevet. */
export type ForelopigVedtakSkjemaTilstand = {
  sakId: string;
  utfall: ForelopigUtfall | null;
  tiltaksperiodeFra: string | null;
  tiltaksperiodeTil: string | null;
  sluttdatoEndret: boolean;
  nyttBelop: string;
  /** Bare tekster saksbehandler har endret. Resten flettes fra standardteksten ved visning. */
  redigerteBegrunnelser: Partial<Record<ForelopigUtfall, Partial<Record<Mottaker, string>>>>;
  /** Satt når skjemaet er validert og forhåndsvises (steg 2). */
  forhandsvisning: ForelopigVedtakInput | null;
};

const ISO_DATO = /^(\d{4})-(\d{2})-(\d{2})$/;

export function fraIsoDato(iso: string): Date | undefined {
  const treff = ISO_DATO.exec(iso);
  if (!treff) return undefined;
  const [, år, måned, dag] = treff.map(Number);
  return new Date(år, måned - 1, dag);
}

export function tilIsoDato(dato: Date): string {
  const måned = String(dato.getMonth() + 1).padStart(2, "0");
  const dag = String(dato.getDate()).padStart(2, "0");
  return `${dato.getFullYear()}-${måned}-${dag}`;
}

export const årFra = (iso: string) => Number(iso.slice(0, 4));

/** Startdato + 3 måneder, men aldri senere enn 31.12 samme år. */
export function standardSluttdato(startdato: string): string | null {
  const start = fraIsoDato(startdato);
  if (!start) return null;
  const målMåned = start.getMonth() + 3;
  const sisteDagIMåned = new Date(start.getFullYear(), målMåned + 1, 0).getDate();
  const slutt = new Date(start.getFullYear(), målMåned, Math.min(start.getDate(), sisteDagIMåned));
  const sisteDagIÅret = new Date(start.getFullYear(), 11, 31);
  return tilIsoDato(slutt > sisteDagIÅret ? sisteDagIÅret : slutt);
}

export function startTilstand(
  sakId: string,
  soknadStartdato: string
): ForelopigVedtakSkjemaTilstand {
  const startdato = fraIsoDato(soknadStartdato) ? soknadStartdato : null;
  return {
    sakId,
    utfall: null,
    tiltaksperiodeFra: startdato,
    tiltaksperiodeTil: startdato ? standardSluttdato(startdato) : null,
    sluttdatoEndret: false,
    nyttBelop: "",
    redigerteBegrunnelser: {},
    forhandsvisning: null,
  };
}

const datoFormat = new Intl.DateTimeFormat("nb-NO", {
  day: "numeric",
  month: "short",
  year: "numeric",
});

export function formatDato(iso: string) {
  const dato = fraIsoDato(iso);
  return dato ? datoFormat.format(dato) : iso;
}

export function formatPeriode(fra: string | null, til: string | null) {
  return fra && til ? `${formatDato(fra)} – ${formatDato(til)}` : null;
}

/** Tolker «Nytt beløp». Mellomrom (også tusenskille) godtas. */
export function tolkNyttBelop(tekst: string): number | null {
  const utenMellomrom = tekst.replace(/\s/g, "");
  if (!/^\d+$/.test(utenMellomrom)) return null;
  const belop = Number(utenMellomrom);
  return Number.isSafeInteger(belop) ? belop : null;
}

/**
 * Fletter `{soknadssum}` og `{innvilgetBelop}` inn i standardteksten. Mangler et beløp, står
 * plassholderen igjen, slik at saksbehandler ser at teksten ikke er ferdig.
 */
export function flettStandardtekst(
  mal: string,
  soknadssum: number | null,
  innvilgetBelop: number | null
) {
  let tekst = mal;
  if (soknadssum !== null) {
    tekst = tekst.replaceAll("{soknadssum}", formatTusenskille(soknadssum));
  }
  if (innvilgetBelop !== null && innvilgetBelop > 0) {
    tekst = tekst.replaceAll("{innvilgetBelop}", formatTusenskille(innvilgetBelop));
  }
  return tekst;
}

/**
 * Teksten saksbehandler har skrevet. Ellers standardteksten til arbeidsgiver flettet med dagens
 * beløp, eller tom tekst der det ikke finnes standardtekst.
 */
export function begrunnelse(
  tilstand: ForelopigVedtakSkjemaTilstand,
  utfall: ForelopigUtfall,
  mottaker: Mottaker,
  standardtekster: Partial<Record<ForelopigUtfall, Standardtekst>>,
  soknadssum: number | null
) {
  const redigert = tilstand.redigerteBegrunnelser[utfall]?.[mottaker];
  if (redigert !== undefined) return redigert;
  if (mottaker === "deltaker") return "";
  const mal = standardtekster[utfall]?.arbeidsgiver ?? "";
  const innvilgetBelop = utfall === "INNVILGE_REDUSERT" ? tolkNyttBelop(tilstand.nyttBelop) : null;
  return flettStandardtekst(mal, soknadssum, innvilgetBelop);
}

export type Rad = { label: string; verdi: string };

/** Radene i infokortet. `tilskudd` utelates når det ikke er kjent (redusert beløp i skjemaet). */
export function vurderingRader(
  utfall: ForelopigUtfall,
  sak: SakDetaljer,
  tilskudd: number | null,
  periode: string | null
): Rad[] {
  const { estimertKostnad } = sak.soknad.behovForBistand;
  const soknadssum = tolkSoknadssum(estimertKostnad);
  const rader: Rad[] = [];
  if (utfall === "INNVILGE_REDUSERT") {
    rader.push({
      label: "Søknadssum",
      verdi: soknadssum !== null ? formatKroner(soknadssum) : estimertKostnad,
    });
  }
  if (tilskudd !== null) rader.push({ label: "Tilskudd", verdi: formatKroner(tilskudd) });
  rader.push({ label: "Deltakere", verdi: sak.soknad.ansatt.navn });
  rader.push({ label: "Arbeidsgiver", verdi: sak.soknad.virksomhet.virksomhetsnavn });
  if (periode) rader.push({ label: "Tiltaksperiode", verdi: periode });
  return rader;
}

export const innvilgeSperretTekst = (sats: MaksSats) =>
  `Søknadssummen er høyere enn maks sats (${formatKroner(sats.belop)}). Velg innvilge med redusert beløp.`;

export const INGEN_SATS_TEKST = "Det finnes ingen maks sats for denne datoen.";

const OVER_ÅRSSKIFTET_TEKST =
  "Tiltaksperioden kan ikke gå over årsskiftet. Velg en sluttdato i samme år som startdatoen.";

export type FeltId = "utfall" | "nyttBelop" | "startdato" | "sluttdato" | Mottaker;

export type Feil = { felt: FeltId; melding: string };

// Samme grenser og tegnsjekker som `standardTekstSjekker` i backend.
const MAKS_TEKST_LENGDE = 10_000;
const FORMATTEGN = /\p{Cf}/u;

const erKontrolltegn = (tegn: string) => {
  const kode = tegn.charCodeAt(0);
  const erTillattWhitespace = kode === 0x09 || kode === 0x0a || kode === 0x0d;
  return (kode <= 0x1f && !erTillattWhitespace) || (kode >= 0x7f && kode <= 0x9f);
};

function tekstfeil(tekst: string, mottaker: Mottaker): string | null {
  if (!tekst.trim()) return `Du må skrive en begrunnelse til ${mottaker}.`;
  if (tekst.length > MAKS_TEKST_LENGDE) {
    return `Begrunnelsen kan ikke være lengre enn ${formatTusenskille(MAKS_TEKST_LENGDE)} tegn.`;
  }
  if (/[<>]/.test(tekst)) return "Begrunnelsen kan ikke inneholde < eller >.";
  if (FORMATTEGN.test(tekst) || Array.from(tekst).some(erKontrolltegn)) {
    return "Begrunnelsen inneholder tegn som ikke er tillatt. Skriv inn teksten på nytt.";
  }
  return null;
}

type Valideringsgrunnlag = {
  tilstand: ForelopigVedtakSkjemaTilstand;
  soknadssum: number | null;
  maksSatser: MaksSats[];
  begrunnelseDeltaker: string;
  begrunnelseArbeidsgiver: string;
  sluttdatoEtterÅrsskiftet: boolean;
};

/** Samme regler som backend (feltregler, sats og søknadssum). Gir input til API-et når alt er gyldig. */
export function validerSkjema({
  tilstand,
  soknadssum,
  maksSatser,
  begrunnelseDeltaker,
  begrunnelseArbeidsgiver,
  sluttdatoEtterÅrsskiftet,
}: Valideringsgrunnlag): { feil: Feil[]; input: ForelopigVedtakInput | null } {
  const { utfall, tiltaksperiodeFra: fra, tiltaksperiodeTil: til } = tilstand;
  const feil: Feil[] = [];
  const leggTil = (felt: FeltId, melding: string | null) => {
    if (melding) feil.push({ felt, melding });
  };

  if (!utfall) {
    leggTil("utfall", "Du må velge en vurdering.");
    return { feil, input: null };
  }

  const sats = fra ? gjeldendeMaksSats(maksSatser, fra) : undefined;

  if (harTiltaksperiode(utfall)) {
    if (soknadssum === null) {
      leggTil("utfall", "Søknadssummen er ikke et gyldig beløp, og saken kan ikke innvilges.");
    } else if (utfall === "INNVILGE" && sats && soknadssum > sats.belop) {
      leggTil("utfall", innvilgeSperretTekst(sats));
    }
  }

  let innvilgetBelop: number | null = null;
  if (utfall === "INNVILGE_REDUSERT") {
    const tekst = tilstand.nyttBelop.trim();
    innvilgetBelop = tolkNyttBelop(tekst);
    if (!tekst) {
      leggTil("nyttBelop", "Du må fylle inn nytt beløp.");
    } else if (innvilgetBelop === null) {
      leggTil("nyttBelop", "Nytt beløp må være et helt antall kroner.");
    } else if (innvilgetBelop <= 0) {
      leggTil("nyttBelop", "Nytt beløp må være mer enn 0 kr.");
    } else if (soknadssum !== null && innvilgetBelop >= soknadssum) {
      leggTil(
        "nyttBelop",
        `Nytt beløp må være lavere enn søknadssummen (${formatKroner(soknadssum)}).`
      );
    } else if (sats && innvilgetBelop > sats.belop) {
      leggTil(
        "nyttBelop",
        `Nytt beløp kan ikke være høyere enn maks sats (${formatKroner(sats.belop)}).`
      );
    }
  }

  if (harTiltaksperiode(utfall)) {
    if (!fra) leggTil("startdato", "Du må velge startdato. Format: dd.mm.åååå");
    else if (!sats) leggTil("startdato", INGEN_SATS_TEKST);

    if (!til) {
      leggTil(
        "sluttdato",
        sluttdatoEtterÅrsskiftet
          ? OVER_ÅRSSKIFTET_TEKST
          : "Du må velge sluttdato. Format: dd.mm.åååå"
      );
    } else if (fra && til < fra) {
      leggTil("sluttdato", "Sluttdato kan ikke være før startdato.");
    } else if (fra && årFra(fra) !== årFra(til)) {
      leggTil("sluttdato", OVER_ÅRSSKIFTET_TEKST);
    }
  }

  leggTil("deltaker", tekstfeil(begrunnelseDeltaker, "deltaker"));
  if (harBegrunnelseTilArbeidsgiver(utfall)) {
    leggTil("arbeidsgiver", tekstfeil(begrunnelseArbeidsgiver, "arbeidsgiver"));
  }

  if (feil.length > 0) return { feil, input: null };

  return {
    feil,
    input: {
      utfall,
      innvilgetBelop: utfall === "INNVILGE_REDUSERT" ? innvilgetBelop : null,
      tiltaksperiodeFra: harTiltaksperiode(utfall) ? fra : null,
      tiltaksperiodeTil: harTiltaksperiode(utfall) ? til : null,
      begrunnelseDeltaker: begrunnelseDeltaker.trim(),
      begrunnelseArbeidsgiver: harBegrunnelseTilArbeidsgiver(utfall)
        ? begrunnelseArbeidsgiver.trim()
        : null,
    },
  };
}
