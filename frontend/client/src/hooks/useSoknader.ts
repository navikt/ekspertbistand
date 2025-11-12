import useSWR from "swr";

import { fetchApplications, type ApplicationListItem } from "../utils/soknader.ts";

export function useSoknader() {
  const { data, error, isLoading } = useSWR<ApplicationListItem[]>(
    "ekspertbistand-applications",
    fetchApplications,
    {
      revalidateOnFocus: true,
    }
  );

  return {
    applications: data ?? [],
    error: error
      ? error instanceof Error
        ? error.message
        : "Kunne ikke hente søknader akkurat nå."
      : null,
    loading: isLoading,
  } as const;
}

export type { ApplicationListItem };
