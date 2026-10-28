import { useState } from "react";
import { useSWRConfig } from "swr";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import { fetchJson } from "../utils/api";
import {
  SAKSBEHANDLING_RETUR_URL,
  SAKSBEHANDLING_SAKSLOGG_URL,
  SAKSBEHANDLING_SAK_URL,
  SAKSBEHANDLING_VEDTAK_URL,
} from "../utils/constants";
import { HttpError } from "../utils/http";
import { medFeilId, tilgangAvvistTekst } from "../utils/tilgangsfeil";
import type { ForelopigUtfall, ReturArsak, SakDetaljer, Vedtak } from "./useSak";

// Handleren skriver sakslogglinjen kort tid etter 202 (EventManager poller hvert 100. ms).
const REVALIDER_LOGG_ETTER_MS = 500;

/** Request-kroppen til `POST …/retur`. */
export type ReturInput = {
  forelopigVedtakVersjon: number;
  aarsaker: ReturArsak[];
  forklaring: string;
};

export const vedtaksutfall = (utfall: ForelopigUtfall): Vedtak["utfall"] =>
  utfall === "AVSLAA" ? "AVSLATT" : "INNVILGET";

function feilmelding409(kode: string | undefined) {
  switch (kode) {
    case "SAK_UGYLDIG_STATUS":
      return "Saken er ikke til beslutning lenger. Last siden på nytt for å se status.";
    case "FORELOPIG_VEDTAK_ENDRET":
      return "Det foreløpige vedtaket er endret. Les gjennom det nye før du tar stilling.";
    case "SAK_UGYLDIG_KILDE":
      return "Saken behandles i Arena, og vedtak kan ikke fattes her.";
    default:
      return "Saken kan ikke behandles nå.";
  }
}

function feilmelding(e: unknown, standard: string): Error {
  if (e instanceof HttpError) {
    switch (e.status) {
      case 400:
        return new Error(e.message);
      case 403:
        return new Error(
          medFeilId(tilgangAvvistTekst(e, "Du har ikke tilgang til å beslutte denne saken."), e)
        );
      case 409:
        return new Error(feilmelding409(e.kode));
      case 503:
        return new Error("Vi kunne ikke sjekke tilgangen din akkurat nå. Prøv igjen om litt.");
    }
  }
  return new Error(standard);
}

export function useBeslutning(sakId: string) {
  const { mutate } = useSWRConfig();
  const innloggetAnsatt = useInnloggetAnsatt();
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const sakUrl = SAKSBEHANDLING_SAK_URL(sakId);

  // 202: eventen er publisert og lagres snart av handleren. Oppdater saken lokalt uten å
  // revalidere, så vi ikke henter den før handleren har kjørt.
  const send = async (
    url: string,
    body: object,
    standardfeil: string,
    oppdater: (sak: SakDetaljer) => SakDetaljer
  ) => {
    setIsSaving(true);
    setError(null);
    try {
      await fetchJson(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      await mutate<SakDetaljer>(sakUrl, (sak) => sak && oppdater(sak), { revalidate: false });
      setTimeout(() => void mutate(SAKSBEHANDLING_SAKSLOGG_URL(sakId)), REVALIDER_LOGG_ETTER_MS);
      return true;
    } catch (e) {
      // 403 og 409 betyr at saken har endret seg. Hent den på nytt.
      if (e instanceof HttpError && (e.status === 403 || e.status === 409)) {
        void mutate(sakUrl);
      }
      setError(feilmelding(e, standardfeil));
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  const fattVedtak = (forelopigVedtakVersjon: number) =>
    send(
      SAKSBEHANDLING_VEDTAK_URL(sakId),
      { forelopigVedtakVersjon },
      "Kunne ikke fatte vedtaket.",
      (sak) => {
        if (!sak.forelopigVedtak) return sak;
        const utfall = vedtaksutfall(sak.forelopigVedtak.utfall);
        return {
          ...sak,
          status: utfall,
          oppgavetype: null,
          vedtak: {
            utfall,
            forelopigVedtakVersjon,
            fattetAvNavn: innloggetAnsatt?.navn ?? "",
            fattetTidspunkt: new Date().toISOString(),
          },
        };
      }
    );

  const returner = (input: ReturInput) =>
    send(SAKSBEHANDLING_RETUR_URL(sakId), input, "Kunne ikke sende saken i retur.", (sak) => ({
      ...sak,
      status: "UNDER_BEHANDLING",
      oppgavetype: "SOKNAD_RETUR",
      sisteRetur: {
        ...input,
        returnertAvNavn: innloggetAnsatt?.navn ?? "",
        tidspunkt: new Date().toISOString(),
      },
    }));

  const nullstillFeil = () => setError(null);

  return { fattVedtak, returner, isSaving, error, nullstillFeil };
}
