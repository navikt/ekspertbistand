import useSWR from "swr";

import { fetchSkjema, type SkjemaListItem } from "../features/soknad/soknader";

export function useSoknader() {
  const { data, error, isLoading } = useSWR<SkjemaListItem[]>(
    "ekspertbistand-soknader",
    fetchSkjema,
    {
      revalidateOnFocus: true,
    }
  );

  return {
    soknader: data ?? [],
    error: error
      ? error instanceof Error
        ? error.message
        : "Kunne ikke hente søknader akkurat nå."
      : null,
    loading: isLoading,
  } as const;
}

export type { SkjemaListItem };
