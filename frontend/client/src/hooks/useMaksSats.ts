import useSWR from "swr";
import { z } from "zod";
import { fetchJson } from "../utils/api";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { formatDateToIso, parseIsoDate, startOfToday } from "../utils/date";

const maksSatsSchema = z.object({
  gyldigFra: z.iso.date(),
  belop: z.number().int().positive(),
});

export function useMaksSats(startdato: string | null | undefined) {
  const dato = startdato
    ? parseIsoDate(startdato)
      ? startdato
      : null
    : formatDateToIso(startOfToday());
  const { data, error, isLoading, isValidating, mutate } = useSWR(
    dato ? `${EKSPERTBISTAND_API_PATH}/maks-sats?startdato=${encodeURIComponent(dato)}` : null,
    async (url) => maksSatsSchema.parse(await fetchJson<unknown>(url))
  );

  return {
    maksSats: error || isLoading || isValidating ? null : (data ?? null),
    dato,
    error,
    isLoading: isLoading || isValidating,
    mutate,
  };
}
