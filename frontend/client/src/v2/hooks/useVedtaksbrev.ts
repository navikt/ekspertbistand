import { useEffect, useState } from "react";
import useSWR from "swr";
import type { HttpError } from "../../utils/http";
import type { PdfFil } from "../components/pdf";
import { fetchVedtaksbrevPdf, vedtakUrl, VEDTAKSBREV_FILNAVN } from "../features/vedtak/vedtak";

type BrevNokkel = [url: string, id: string];

/** Lager en object URL for [blob] og frigjør den når blob-en byttes eller komponenten forsvinner. */
function useObjectUrl(blob: Blob | undefined) {
  const [url, setUrl] = useState<{ blob: Blob; url: string }>();

  useEffect(() => {
    if (!blob) return;
    const objectUrl = URL.createObjectURL(blob);
    // URL-en må lages og frigjøres i samme effekt, ellers lekker den eller frigjøres for tidlig i StrictMode.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setUrl({ blob, url: objectUrl });
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);

  return url && url.blob === blob ? url.url : undefined;
}

/**
 * Henter vedtaksbrevet først når [apen] er sann. Hvert oppslag publiserer `VedtaksbrevVist`, så
 * SWR skal ikke hente brevet på nytt ved fokus eller ny tilkobling.
 */
export function useVedtaksbrev(id: string | undefined, apen: boolean) {
  const { data, error, isLoading } = useSWR<Blob, HttpError, BrevNokkel | null>(
    id && apen ? [`${vedtakUrl(id)}/brev-pdf`, id] : null,
    ([, soknadId]) => fetchVedtaksbrevPdf(soknadId),
    {
      revalidateIfStale: false,
      revalidateOnFocus: false,
      revalidateOnReconnect: false,
      shouldRetryOnError: false,
    }
  );
  const url = useObjectUrl(data);
  const fil: PdfFil | undefined =
    data && url ? { url, filnavn: VEDTAKSBREV_FILNAVN, innhold: data } : undefined;

  return { fil, error, isLoading: isLoading || (data !== undefined && !url) };
}
