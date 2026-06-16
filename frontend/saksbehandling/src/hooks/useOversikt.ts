import useSWR from "swr";
import { SAKSBEHANDLING_OVERSIKT_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type OversiktRad = {
  id: string;
  virksomhet: string;
  deltaker: string;
  status: "Til behandling" | "Avventer svar" | "Ferdigstilt";
  saksbehandler: string;
  opprettetDato: string;
  tilsagnNummer?: string;
};

export type OversiktResponse = {
  saker: OversiktRad[];
};

export function useOversikt() {
  const { data, error, isLoading } = useSWR<OversiktResponse, HttpError>(
    SAKSBEHANDLING_OVERSIKT_URL,
    {
      revalidateOnFocus: false,
    }
  );

  return {
    saker: data?.saker ?? [],
    error,
    isLoading,
  };
}
