import useSWR from "swr";
import { SAKSBEHANDLING_SAKSLOGG_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type AktorRolle = "SAKSBEHANDLER" | "BESLUTTER" | "SYSTEM";

export type SaksloggInnslag = {
  id: string;
  tidspunkt: string;
  utfortAvRolle: AktorRolle;
  utfortAvIdent: string | null;
  utfortAvNavn: string;
  notat: string | null;
};

export type SaksloggResponse = {
  innslag: SaksloggInnslag[];
};

/** Hentes først når saksloggen åpnes, fordi komponenten som bruker hooken bare rendres da. */
export function useSakslogg(sakId: string) {
  const { data, error, isLoading } = useSWR<SaksloggResponse, HttpError>(
    sakId ? SAKSBEHANDLING_SAKSLOGG_URL(sakId) : null,
    { revalidateOnFocus: false }
  );

  return { innslag: data?.innslag, error, isLoading };
}
