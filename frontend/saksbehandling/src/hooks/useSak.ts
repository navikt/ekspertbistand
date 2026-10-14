import useSWR from "swr";
import { SAKSBEHANDLING_SAK_URL } from "../utils/constants";
import { HttpError } from "../utils/http";
import type { SakInfo, Saksstatus, SoknadStatus } from "./useSaker";

export type ForelopigUtfall = "INNVILGE" | "INNVILGE_REDUSERT" | "AVSLAA";

/** Speiler `ForelopigVedtakDTO` i backend. Datoer er ISO-datoer (`yyyy-mm-dd`). */
export type ForelopigVedtak = {
  versjon: number;
  utfall: ForelopigUtfall;
  innvilgetBelop: number | null;
  tiltaksperiodeFra: string | null;
  tiltaksperiodeTil: string | null;
  begrunnelseDeltaker: string;
  begrunnelseArbeidsgiver: string | null;
  innstiltAvIdent: string;
  sendtTidspunkt: string;
};

export type ReturArsak = "FEIL_REGELFORSTAELSE" | "FEIL_FAKTA" | "ANNET";

/** Speiler `ReturDTO` i backend: nyeste retur fra beslutter. */
export type Retur = {
  forelopigVedtakVersjon: number;
  aarsaker: ReturArsak[];
  forklaring: string;
  returnertAvNavn: string;
  tidspunkt: string;
};

/** Speiler `VedtakDTO` i backend. */
export type Vedtak = {
  utfall: Extract<Saksstatus, "INNVILGET" | "AVSLATT">;
  forelopigVedtakVersjon: number;
  fattetAvNavn: string;
  fattetTidspunkt: string;
};

export type SakDetaljer = SakInfo & {
  soknad: SoknadDetaljer;
  forelopigVedtak: ForelopigVedtak | null;
  sisteRetur: Retur | null;
  vedtak: Vedtak | null;
};

export type SoknadDetaljer = {
  soknadId: string;
  status: SoknadStatus;
  innsendtTidspunkt: string;
  virksomhet: {
    virksomhetsnummer: string;
    virksomhetsnavn: string;
    kontaktperson: {
      navn: string;
      epost: string;
      telefonnummer: string;
    };
    beliggenhetsadresse?: string | null;
  };
  ansatt: {
    fnr: string;
    navn: string;
  };
  ekspert: {
    navn: string;
    virksomhet: string;
    virksomhetNavn?: string | null;
    virksomhetOrgnr?: string | null;
    kompetanse: string;
    godkjentUtdanningEllerAutorisasjon: string[];
    relevantKompetanse: string[];
  };
  behovForBistand: {
    begrunnelse: string;
    behov: string;
    estimertKostnad: string;
    timer: string;
    tilrettelegging: string;
    startdato: string;
  };
  nav: {
    kontaktperson: string;
  };
};

export function useSak(sakId: string) {
  const { data, error, isLoading } = useSWR<SakDetaljer, HttpError>(
    sakId ? SAKSBEHANDLING_SAK_URL(sakId) : null,
    {
      revalidateOnFocus: false,
    }
  );

  return { sak: data, error, isLoading };
}
