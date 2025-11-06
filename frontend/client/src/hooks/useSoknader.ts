import { useEffect, useState } from "react";

import { fetchApplications, type ApplicationListItem } from "../utils/soknader.ts";

export function useSoknader() {
  const [applications, setApplications] = useState<ApplicationListItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();

    async function load() {
      setLoading(true);
      try {
        const fetched = await fetchApplications(controller.signal);
        if (!controller.signal.aborted) {
          setApplications(fetched);
          setError(null);
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          const message =
            err instanceof Error ? err.message : "Kunne ikke hente søknader akkurat nå.";
          setError(message);
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => controller.abort();
  }, []);

  return { applications, error, loading } as const;
}

export type { ApplicationListItem };
