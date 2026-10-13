import useSWR from "swr";
import { SAKSBEHANDLING_SLUTTRAPPORT_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type SluttrapportStatusDto = {
  filnavn: string;
  opprettet: string;
  storrelse: number;
};

export function useSluttrapport(sakId: string, enabled: boolean) {
  const { data, error, isLoading } = useSWR<SluttrapportStatusDto, HttpError>(
    enabled && sakId ? SAKSBEHANDLING_SLUTTRAPPORT_URL(sakId) : null,
    { revalidateOnFocus: false }
  );

  return { sluttrapport: data ?? null, error, isLoading };
}
