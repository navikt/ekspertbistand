import { useState } from "react";
import { useSWRConfig } from "swr";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import { fetchJson } from "../utils/api";
import { tolkSoknadssum } from "../utils/belop";
import {
  SAKSBEHANDLING_FORELOPIG_VEDTAK_URL,
  SAKSBEHANDLING_SAKSLOGG_URL,
  SAKSBEHANDLING_SAK_URL,
} from "../utils/constants";
import { HttpError } from "../utils/http";
import type { ForelopigUtfall, ForelopigVedtak, SakDetaljer } from "./useSak";

// Handleren skriver sakslogglinjen kort tid etter 202 (EventManager poller hvert 100. ms).
const REVALIDER_LOGG_ETTER_MS = 500;

/** Request-kroppen til `POST …/forelopig-vedtak`. For `INNVILGE` setter backend beløpet selv. */
export type ForelopigVedtakInput = {
  utfall: ForelopigUtfall;
  innvilgetBelop: number | null;
  tiltaksperiodeFra: string | null;
  tiltaksperiodeTil: string | null;
  begrunnelseDeltaker: string;
  begrunnelseArbeidsgiver: string | null;
};

function feilmelding409(kode: string | undefined) {
  switch (kode) {
    case "SAK_UGYLDIG_STATUS":
      return "Saken er ikke under behandling, og foreløpig vedtak kan ikke sendes.";
    case "VILKAR_IKKE_VURDERT":
      return "Alle vilkår må vurderes før du kan fatte foreløpig vedtak.";
    case "BELOP_OVER_SATS":
      return "Søknadssummen er høyere enn maks sats. Velg innvilge med redusert beløp.";
    case "SAK_UGYLDIG_KILDE":
      return "Saken behandles i Arena, og foreløpig vedtak kan ikke fattes her.";
    case "UGYLDIG_SOKNADSSUM":
      return "Søknadssummen er ikke et gyldig beløp, og foreløpig vedtak kan ikke sendes.";
    default:
      return "Saken kan ikke sendes til beslutter nå.";
  }
}

function feilmelding(e: unknown): Error {
  if (e instanceof HttpError) {
    switch (e.status) {
      case 400:
        return new Error("Sjekk feltene og prøv igjen.");
      case 403:
        return new Error(
          e.begrunnelse ?? "Du har ikke tilgang til å fatte foreløpig vedtak i denne saken."
        );
      case 409:
        return new Error(feilmelding409(e.kode));
      case 503:
        return new Error("Vi kunne ikke sjekke tilgangen din akkurat nå. Prøv igjen om litt.");
    }
  }
  return new Error("Kunne ikke sende saken til beslutter.");
}

export function useForelopigVedtak(sakId: string) {
  const { mutate } = useSWRConfig();
  const innloggetAnsatt = useInnloggetAnsatt();
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const sakUrl = SAKSBEHANDLING_SAK_URL(sakId);

  const sendTilBeslutter = async (input: ForelopigVedtakInput) => {
    setIsSaving(true);
    setError(null);

    try {
      await fetchJson(SAKSBEHANDLING_FORELOPIG_VEDTAK_URL(sakId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(input),
      });

      // 202: eventen er publisert og lagres snart av handleren. Vis vedtaket som sendt
      // uten å revalidere, så vi ikke henter saken før handleren har kjørt.
      await mutate<SakDetaljer>(
        sakUrl,
        (sak) => {
          if (!sak) return sak;
          const forelopigVedtak: ForelopigVedtak = {
            ...input,
            versjon: (sak.forelopigVedtak?.versjon ?? 0) + 1,
            innvilgetBelop:
              input.utfall === "INNVILGE"
                ? tolkSoknadssum(sak.soknad.behovForBistand.estimertKostnad)
                : input.innvilgetBelop,
            innstiltAvIdent: innloggetAnsatt?.id ?? "",
            sendtTidspunkt: new Date().toISOString(),
          };
          return { ...sak, status: "TIL_BESLUTNING", forelopigVedtak };
        },
        { revalidate: false }
      );
      setTimeout(() => void mutate(SAKSBEHANDLING_SAKSLOGG_URL(sakId)), REVALIDER_LOGG_ETTER_MS);
      return true;
    } catch (e) {
      // 403 og 409 betyr at saken har endret seg (f.eks. tatt av en kollega). Hent den på nytt.
      if (e instanceof HttpError && (e.status === 403 || e.status === 409)) {
        void mutate(sakUrl);
      }
      setError(feilmelding(e));
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  return { sendTilBeslutter, isSaving, error };
}
