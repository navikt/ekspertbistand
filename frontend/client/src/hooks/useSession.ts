import useSWR from "swr";
import { SESSION_URL } from "../utils/constants";
import { HttpError, parseErrorMessage } from "../utils/http";

type SessionResponse = {
  session?: {
    ends_in_seconds?: number | null;
  } | null;
};

const fetchSession = async (url: string): Promise<SessionResponse> => {
  const response = await fetch(url, { headers: { Accept: "application/json" } });

  if (response.status === 401 || response.status === 403) {
    return { session: null };
  }

  if (!response.ok) {
    const message = (await parseErrorMessage(response)) ?? `Kunne ikke hente data (${response.status}).`;
    throw new HttpError(message, { status: response.status, statusText: response.statusText });
  }

  const text = await response.text();
  if (!text || text.trim().length === 0) {
    return { session: null };
  }

  return JSON.parse(text) as SessionResponse;
};

export const useSession = () => {
  const { data, error, isLoading } = useSWR<SessionResponse>(SESSION_URL, fetchSession);
  const authenticated = (data?.session?.ends_in_seconds ?? 0) > 0;
  return { authenticated, error, isLoading };
};
