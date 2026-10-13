import useSWR from "swr";
import { fetchJson } from "../utils/api";
import { EKSPERTBISTAND_SLUTTRAPPORT_PATH } from "../utils/constants";

export type SluttrapportStatus = {
  filnavn: string;
  opprettet: string;
  storrelse: number;
};

export function useSluttrapportStatus(id: string | undefined) {
  const { data, error, isLoading, mutate } = useSWR(
    id ? EKSPERTBISTAND_SLUTTRAPPORT_PATH(id) : null,
    (url) => fetchJson<SluttrapportStatus>(url)
  );

  return {
    sluttrapport: data ?? null,
    isLoading,
    error: error ?? null,
    mutate,
  };
}
