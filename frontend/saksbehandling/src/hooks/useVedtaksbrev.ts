import { useEffect, useState } from "react";
import useSWR from "swr";
import type { PdfFil } from "../components/pdf";
import {
  SAKSBEHANDLING_FORELOPIG_VEDTAK_BREV_URL,
  SAKSBEHANDLING_FORELOPIG_VEDTAK_FORHANDSVISNING_URL,
} from "../utils/constants";
import { HttpError, parseErrorPayload } from "../utils/http";
import type { ForelopigVedtakInput } from "./useForelopigVedtak";

export type Mottaker = "deltaker" | "arbeidsgiver";

export const vedtaksbrevFilnavn = (mottaker: Mottaker) => `Foreløpig vedtak - ${mottaker}.pdf`;

// Handleren lagrer vedtaket kort tid etter 202 (EventManager poller hvert 100. ms), så GET kan gi
// 404 rett etter innsending.
const MAKS_FORSOK_VED_404 = 5;
const VENT_FOR_NYTT_FORSOK_MS = 300;

type ForhandsvisningNokkel = [url: string, mottaker: Mottaker, body: string];
type LagretNokkel = [url: string, versjon: number];

async function hentPdf(url: string, init?: RequestInit): Promise<Blob> {
  const response = await fetch(url, {
    ...init,
    headers: { ...init?.headers, Accept: "application/pdf" },
  });
  if (!response.ok) {
    const { message, begrunnelse, kode, feilId } = await parseErrorPayload(response);
    throw new HttpError(message ?? `Kunne ikke lage brev (${response.status}).`, {
      status: response.status,
      begrunnelse,
      kode,
      feilId,
    });
  }
  return response.blob();
}

const hentForhandsvisning = ([url, mottaker, body]: ForhandsvisningNokkel) =>
  hentPdf(`${url}?mottaker=${mottaker}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
  });

async function hentLagret([url]: LagretNokkel): Promise<Blob> {
  for (let forsok = 0; ; forsok++) {
    try {
      return await hentPdf(url);
    } catch (e) {
      if (!(e instanceof HttpError) || e.status !== 404 || forsok >= MAKS_FORSOK_VED_404) throw e;
      await new Promise((resolve) => setTimeout(resolve, VENT_FOR_NYTT_FORSOK_MS * 2 ** forsok));
    }
  }
}

/** Lager en object URL for [blob] og frigjør den når blob-en byttes eller komponenten forsvinner. */
function useObjectUrl(blob: Blob | undefined) {
  const [url, setUrl] = useState<{ blob: Blob; url: string }>();

  useEffect(() => {
    if (!blob) return;
    const objectUrl = URL.createObjectURL(blob);
    setUrl({ blob, url: objectUrl });
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);

  return url && url.blob === blob ? url.url : undefined;
}

const utenRevalidering = {
  revalidateIfStale: false,
  revalidateOnFocus: false,
  revalidateOnReconnect: false,
};

function useForhandsvistBrev(sakId: string, mottaker: Mottaker, body: string) {
  const swr = useSWR<Blob, HttpError, ForhandsvisningNokkel>(
    [SAKSBEHANDLING_FORELOPIG_VEDTAK_FORHANDSVISNING_URL(sakId), mottaker, body],
    hentForhandsvisning,
    { ...utenRevalidering, shouldRetryOnError: false }
  );
  return useBrevFil(mottaker, swr);
}

function useLagretBrev(sakId: string, versjon: number, mottaker: Mottaker) {
  const swr = useSWR<Blob, HttpError, LagretNokkel>(
    [
      `${SAKSBEHANDLING_FORELOPIG_VEDTAK_BREV_URL(sakId, mottaker)}?visning=forhandsvisning`,
      versjon,
    ],
    hentLagret,
    { ...utenRevalidering, shouldRetryOnError: false }
  );
  return useBrevFil(mottaker, swr);
}

function useBrevFil(
  mottaker: Mottaker,
  { data, error, isLoading }: { data?: Blob; error?: HttpError; isLoading: boolean }
) {
  const url = useObjectUrl(data);
  const fil: PdfFil | undefined =
    data && url ? { url, filnavn: vedtaksbrevFilnavn(mottaker), innhold: data } : undefined;

  return { fil, error, isLoading: isLoading || (data !== undefined && !url) };
}

/**
 * Brevene til deltaker og arbeidsgiver for et foreløpig vedtak som ikke er sendt. Backend lager
 * PDF-ene fra [input] uten å lagre noe. Samme input gir samme SWR-nøkkel, så brevene hentes ikke
 * på nytt når saksbehandler går fram og tilbake mellom stegene.
 */
export function useVedtaksbrevForhandsvisning(sakId: string, input: ForelopigVedtakInput) {
  const body = JSON.stringify(input);
  return beggeBrev(
    useForhandsvistBrev(sakId, "deltaker", body),
    useForhandsvistBrev(sakId, "arbeidsgiver", body)
  );
}

/**
 * Brevene fra siste lagrede foreløpige vedtak. [versjon] er med i SWR-nøkkelen, så et nytt vedtak
 * ikke viser brevene fra forrige versjon fra cachen.
 */
export function useLagredeVedtaksbrev(sakId: string, versjon: number) {
  return beggeBrev(
    useLagretBrev(sakId, versjon, "deltaker"),
    useLagretBrev(sakId, versjon, "arbeidsgiver")
  );
}

type BrevFil = ReturnType<typeof useBrevFil>;

function beggeBrev(deltaker: BrevFil, arbeidsgiver: BrevFil) {
  return {
    deltaker: deltaker.fil,
    arbeidsgiver: arbeidsgiver.fil,
    isLoading: deltaker.isLoading || arbeidsgiver.isLoading,
    error: deltaker.error ?? arbeidsgiver.error,
  };
}
