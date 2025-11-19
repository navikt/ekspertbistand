import useSWR from "swr";

import { fetchSkjema, type SkjemaListItem } from "../features/soknad/soknader";
import { resolveApiError } from "../utils/http";

export function useSoknader() {
  const { data, error, isLoading } = useSWR<SkjemaListItem[]>(
    "ekspertbistand-soknader",
    fetchSkjema,
    {
      revalidateOnFocus: true,
    }
  );
  const apiError = error ? resolveApiError(error, "Kunne ikke hente søknader akkurat nå.") : null;

  return {
    soknader: data ?? [],
    error: apiError?.message ?? null,
    requiresLogin: apiError?.requiresLogin ?? false,
    loading: isLoading,
  } as const;
}

export type { SkjemaListItem };
