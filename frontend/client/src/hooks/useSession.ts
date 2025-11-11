import useSWR from "swr";
import { SESSION_URL } from "../utils/constants";

type SessionResponse = {
  session?: {
    ends_in_seconds?: number | null;
  } | null;
};

export const useSession = () => {
  const { data, error, isLoading } = useSWR<SessionResponse>(SESSION_URL);
  const authenticated = (data?.session?.ends_in_seconds ?? 0) > 0;
  return { authenticated, error, isLoading };
};
