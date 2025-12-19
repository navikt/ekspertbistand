import useSWR from "swr";
import { fetchJson } from "../utils/api";
import { HttpError } from "../utils/http";

type AdresseResponse = {
  adresse: string;
};

const fetchAdresse = async (url: string): Promise<string | null> => {
  try {
    const data = await fetchJson<AdresseResponse>(url);
    return data?.adresse ?? null;
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
};

export const useVirksomhetAdresse = (orgnr: string | null | undefined) => {
  const shouldFetch = Boolean(orgnr);
  const { data, error, isLoading } = useSWR<string | null>(
    shouldFetch ? `/api/ereg/${orgnr}/adresse` : null,
    fetchAdresse
  );

  return {
    adresse: data ?? null,
    isLoading,
    error,
  };
};
