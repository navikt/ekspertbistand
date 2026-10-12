import { useCallback, useEffect, useRef, useState } from "react";
import { lastNedPdf } from "../utils/api";
import { SAKSBEHANDLING_REFUSJON_VEDLEGG_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

function nedlastingsFeilmelding(error: unknown): string {
  if (error instanceof HttpError) {
    switch (error.status) {
      case 403:
        return "Du har ikke tilgang til vedlegget.";
      case 404:
        return "Vi fant ikke vedlegget. Last inn siden på nytt og prøv igjen.";
      case 503:
        return "Tjenesten er midlertidig utilgjengelig. Prøv igjen om litt.";
    }
  }
  return "Noe gikk galt. Prøv igjen senere.";
}

export function useRefusjonsvedleggNedlasting(sakId: string) {
  const kontroller = useRef<AbortController | null>(null);
  const [lastesNedId, setLastesNedId] = useState<string | null>(null);
  const [nedlastingFeil, setNedlastingFeil] = useState<string | null>(null);

  useEffect(() => () => kontroller.current?.abort(), [sakId]);

  const lastNed = useCallback(
    async (vedleggId: string) => {
      kontroller.current?.abort();
      const nesteKontroller = new AbortController();
      kontroller.current = nesteKontroller;
      setNedlastingFeil(null);
      setLastesNedId(vedleggId);

      try {
        await lastNedPdf(
          SAKSBEHANDLING_REFUSJON_VEDLEGG_URL(sakId, vedleggId),
          nesteKontroller.signal
        );
      } catch (error) {
        if (
          !nesteKontroller.signal.aborted &&
          !(error instanceof HttpError && error.status === 401)
        ) {
          setNedlastingFeil(nedlastingsFeilmelding(error));
        }
      } finally {
        if (kontroller.current === nesteKontroller) {
          kontroller.current = null;
          setLastesNedId(null);
        }
      }
    },
    [sakId]
  );

  return { lastNed, lastesNedId, nedlastingFeil };
}
