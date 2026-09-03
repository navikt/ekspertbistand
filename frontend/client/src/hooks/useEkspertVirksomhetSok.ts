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

const useDebouncedValue = <T,>(value: T, delayMs: number): T => {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
};

export const useEkspertVirksomhetSok = (navn: string) => {
  const sokeord = navn.trim();
  const debouncedNavn = useDebouncedValue(sokeord, DEBOUNCE_MS);
  const shouldFetch = debouncedNavn.length >= MIN_SOK_LENGDE;

  const { data, error, isLoading } = useSWR<OrganisasjonSok[]>(
    shouldFetch
      ? `${EKSPERTBISTAND_EREG_ORGANISASJONER_PATH}?navn=${encodeURIComponent(debouncedNavn)}`
      : null,
    fetchOrganisasjoner,
    // Uten keepPreviousData blir data undefined i det nøkkelen endres, slik at
    // trefflisten kollapser til tom mellom hvert søk.
    { keepPreviousData: true, revalidateOnFocus: false }
  );

  return {
    // keepPreviousData beholder treff også etter at søkeordet er blitt for kort,
    // så nullstill eksplisitt når vi ikke søker.
    organisasjoner: shouldFetch ? (data ?? []) : [],
    // Marker også debounce-vinduet som lasting, ellers vises forrige søks treff
    // som om de var ferdige resultater for det man nettopp skrev.
    isLoading: shouldFetch && (isLoading || sokeord !== debouncedNavn),
    error,
  };
};
