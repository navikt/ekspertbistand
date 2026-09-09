import { useEffect, useState } from "react";
import useSWR from "swr";
import { fetchJson } from "../utils/api";
import {
  EKSPERTBISTAND_EREG_ORGANISASJON_PATH,
  EKSPERTBISTAND_EREG_ORGANISASJONER_PATH,
} from "../utils/constants";

export type OrganisasjonSok = {
  organisasjonsnummer: string;
  navn: string;
};

const MIN_SOK_LENGDE = 2;
const DEBOUNCE_MS = 300;
const ORGNR_REGEX = /^\d{9}$/;

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
  const sokeord = navn.trim();
  const debouncedNavn = useDebouncedValue(sokeord, DEBOUNCE_MS);
  const shouldFetch = debouncedNavn.length >= MIN_SOK_LENGDE;
  const isOrgnr = ORGNR_REGEX.test(debouncedNavn);
  const searchUrl = isOrgnr
    ? EKSPERTBISTAND_EREG_ORGANISASJON_PATH(debouncedNavn)
    : `${EKSPERTBISTAND_EREG_ORGANISASJONER_PATH}?navn=${encodeURIComponent(debouncedNavn)}`;

  const { data, error, isValidating } = useSWR<OrganisasjonSok[]>(
    shouldFetch ? searchUrl : null,
    fetchOrganisasjoner,
    { keepPreviousData: true }
  );

  return {
    organisasjoner: data ?? [],
    isLoading: sokeord !== debouncedNavn || (shouldFetch && isValidating),
    error,
  };
};
