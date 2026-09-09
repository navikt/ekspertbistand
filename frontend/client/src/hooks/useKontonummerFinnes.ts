import useSWR from "swr";
import { fetchJson } from "../utils/api";
import { EKSPERTBISTAND_KONTONUMMER_PATH } from "../utils/constants";

type KontonummerFinnesResponse = {
  finnes: boolean;
};

const fetchKontonummerFinnes = async (url: string): Promise<boolean> => {
  const data = await fetchJson<KontonummerFinnesResponse>(url);
  return data?.finnes ?? false;
};

export const useKontonummerFinnes = (orgnr: string | null | undefined) => {
  const shouldFetch = Boolean(orgnr);
  const { data, error, isLoading } = useSWR<boolean>(
    shouldFetch ? `${EKSPERTBISTAND_KONTONUMMER_PATH}/${orgnr}` : null,
    fetchKontonummerFinnes
  );

  return {
    finnes: data ?? false,
    isLoading,
    error,
  };
};
