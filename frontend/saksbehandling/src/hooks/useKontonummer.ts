import useSWR from "swr";
import { fetchJson } from "../utils/api";
import { HttpError } from "../utils/http";
import { SAKSBEHANDLING_KONTONUMMER_URL } from "../utils/constants";

type KontonummerResponse = {
  kontonummer: string;
};

const fetchKontonummer = async (url: string): Promise<string | null> => {
  try {
    const data = await fetchJson<KontonummerResponse>(url);
    return data?.kontonummer ?? null;
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
};

export const useKontonummer = (orgnr: string | null | undefined) => {
  const shouldFetch = Boolean(orgnr);
  const { data, error, isLoading } = useSWR<string | null>(
    shouldFetch ? SAKSBEHANDLING_KONTONUMMER_URL(orgnr as string) : null,
    fetchKontonummer
  );

  return {
    kontonummer: data ?? null,
    isLoading,
    error,
  };
};
