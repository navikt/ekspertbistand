import useSWR from "swr";
import { SAKSBEHANDLING_FORELOPIG_VEDTAK_OPPSETT_URL } from "../utils/constants";
import type { HttpError } from "../utils/http";
import type { MaksSats } from "../utils/maksSats";
import type { ForelopigUtfall } from "./useSak";

/** Bare til arbeidsgiver. Begrunnelsen til deltaker skrives for hver enkelt. */
export type Standardtekst = {
  arbeidsgiver: string;
};

/** `navn` er uten enhetsnummer, f.eks. «Nav arbeidslivssenter Innlandet». */
export type Kostnadssted = {
  enhetsnummer: string;
  navn: string;
};

export type ForelopigVedtakOppsett = {
  standardtekster: Partial<Record<ForelopigUtfall, Standardtekst>>;
  maksSatser: MaksSats[];
  kostnadssteder: Kostnadssted[];
};

/** `aktiv: false` henter ikke. Endepunktet krever saksbehandlerrollen og gir 403 til andre. */
export function useForelopigVedtakOppsett(aktiv: boolean) {
  const { data, error, isLoading } = useSWR<ForelopigVedtakOppsett, HttpError>(
    aktiv ? SAKSBEHANDLING_FORELOPIG_VEDTAK_OPPSETT_URL : null,
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      revalidateIfStale: false,
    }
  );

  return { oppsett: data, error, isLoading };
}
