import { useEffect, useState } from "react";
import useSWR from "swr";
import { fetchJson } from "../utils/api";
import { EKSPERTBISTAND_EREG_ORGANISASJONER_PATH } from "../utils/constants";

export type OrganisasjonSok = {
  organisasjonsnummer: string;
  navn: string;
};

const MIN_SOK_LENGDE = 2;
const DEBOUNCE_MS = 300;

const fetchOrganisasjoner = async (url: string): Promise<OrganisasjonSok[]> => {
  const data = await fetchJson<OrganisasjonSok[]>(url);
  return data ?? [];
};

const useDebouncedValue = <T>(value: T, delayMs: number): T => {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
};

export const useEkspertVirksomhetSok = (navn: string) => {
  const debouncedNavn = useDebouncedValue(navn.trim(), DEBOUNCE_MS);
  const shouldFetch = debouncedNavn.length >= MIN_SOK_LENGDE;

  const { data, error, isLoading } = useSWR<OrganisasjonSok[]>(
    shouldFetch
      ? `${EKSPERTBISTAND_EREG_ORGANISASJONER_PATH}?navn=${encodeURIComponent(debouncedNavn)}`
      : null,
    fetchOrganisasjoner,
    { keepPreviousData: true }
  );

  return {
    organisasjoner: data ?? [],
    isLoading: shouldFetch && isLoading,
    error,
  };
};
