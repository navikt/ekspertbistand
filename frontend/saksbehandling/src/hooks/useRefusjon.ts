import useSWR from "swr";
import { SAKSBEHANDLING_REFUSJON_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type RefusjonStatusDto = {
  belopKroner: number;
  utgifter: string;
  opprettet: string;
  vedlegg: {
    id: string;
    filnavn: string;
    storrelse: number;
  }[];
};

export function useRefusjon(sakId: string, enabled: boolean) {
  const { data, error, isLoading } = useSWR<RefusjonStatusDto, HttpError>(
    enabled && sakId ? SAKSBEHANDLING_REFUSJON_URL(sakId) : null,
    { revalidateOnFocus: false }
  );

  return { refusjon: data ?? null, error, isLoading };
}
