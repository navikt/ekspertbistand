import useSWR from "swr";
import type { HttpError } from "../../utils/http";
import { fetchVedtak, vedtakUrl, type Vedtaksbrev } from "../features/vedtak/vedtak";

export function useVedtak(id: string | undefined, enabled = true) {
  const { data, error, isLoading } = useSWR<Vedtaksbrev | null, HttpError>(
    id && enabled ? vedtakUrl(id) : null,
    fetchVedtak
  );

  return { vedtak: data, error, isLoading };
}
