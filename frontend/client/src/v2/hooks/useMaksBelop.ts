import useSWR from "swr";
import { z } from "zod";
import { HttpError } from "../../utils/http";
import { EKSPERTBISTAND_REFUSJON_PATH } from "../utils/constants";

const maksBelopSchema = z.object({
  maksBelopKroner: z.number().int().positive(),
});

async function hentMaksBelop(url: string) {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    const message = (await response.text()).trim();
    throw new HttpError(message || `Kunne ikke hente maksbeløpet (${response.status}).`, {
      status: response.status,
      statusText: response.statusText,
    });
  }
  return maksBelopSchema.parse(await response.json());
}

export function useMaksBelop(id: string | undefined) {
  const { data, error, isLoading, mutate } = useSWR(
    id ? `${EKSPERTBISTAND_REFUSJON_PATH(id)}/maksbelop` : null,
    hentMaksBelop
  );

  return {
    maksBelopKroner: data?.maksBelopKroner ?? null,
    error: error ?? null,
    isLoading,
    mutate,
  };
}
