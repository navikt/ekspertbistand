import useSWR from "swr";
import { fetchJson } from "../utils/api";
import { EKSPERTBISTAND_REFUSJON_PATH } from "../utils/constants";

export type RefusjonVedlegg = {
  id: string;
  filnavn: string;
  storrelse: number;
};

export type RefusjonStatus = {
  belopKroner: number;
  utgifter: string;
  opprettet: string;
  kontonummer: string | null;
  vedlegg: RefusjonVedlegg[];
};

export function useRefusjonStatus(id: string | undefined) {
  const { data, error, isLoading, mutate } = useSWR(
    id ? EKSPERTBISTAND_REFUSJON_PATH(id) : null,
    (url) => fetchJson<RefusjonStatus>(url)
  );

  return {
    refusjon: data ?? null,
    isLoading,
    error: error ?? null,
    mutate,
  };
}
