import useSWR from "swr";
import { SAKSBEHANDLING_FORELOPIG_VEDTAK_OPPSETT_URL } from "../utils/constants";
import type { HttpError } from "../utils/http";
import type { MaksSats } from "../utils/maksSats";
import type { ForelopigUtfall } from "./useSak";

/** Bare til arbeidsgiver. Begrunnelsen til deltaker skrives for hver enkelt. */
export type Standardtekst = {
  arbeidsgiver: string;
};

export type ForelopigVedtakOppsett = {
  standardtekster: Partial<Record<ForelopigUtfall, Standardtekst>>;
  maksSatser: MaksSats[];
};

export function useForelopigVedtakOppsett() {
  const { data, error, isLoading } = useSWR<ForelopigVedtakOppsett, HttpError>(
    SAKSBEHANDLING_FORELOPIG_VEDTAK_OPPSETT_URL,
    {
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      revalidateIfStale: false,
    }
  );

  return { oppsett: data, error, isLoading };
}
