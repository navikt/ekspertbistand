import { useState } from "react";
import { useSWRConfig } from "swr";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import { fetchJson } from "../utils/api";
import {
  SAKSBEHANDLING_BESLUTTER_TILDELING_URL,
  SAKSBEHANDLING_SAKER_URL,
  SAKSBEHANDLING_SAKSLOGG_URL,
  SAKSBEHANDLING_SAK_URL,
  SAKSBEHANDLING_TILDELING_URL,
} from "../utils/constants";
import { HttpError } from "../utils/http";
import type { SakDetaljer } from "./useSak";
import type { SakInfo, SakerResponse, Tildelingsrolle } from "./useSaker";

// API-et svarer 202 før handleren har oppdatert saken (EventManager poller hvert 100. ms).
// Revaliderer vi med en gang, kan svaret overskrive den optimistiske tilstanden med den gamle.
const REVALIDER_ETTER_MS = 500;

type Handling = "tildel" | "frigjoer";

type Oppdatering = <T extends SakInfo>(sak: T) => T;

function feilmelding(e: unknown, handling: Handling, rolle: Tildelingsrolle): Error {
  if (e instanceof HttpError) {
    switch (e.status) {
      case 403:
        return new Error(
          e.begrunnelse ??
            (handling === "tildel"
              ? "Du har ikke tilgang til å behandle saken."
              : "Saken er ikke tildelt deg, og du kan ikke frigjøre den.")
        );
      case 409:
        return new Error(
          rolle === "beslutter"
            ? "Saken venter ikke på beslutter."
            : "Saken kan ikke tildeles med den statusen den har nå."
        );
      case 503:
        return new Error("Vi kunne ikke sjekke tilgangen din akkurat nå. Prøv igjen om litt.");
    }
  }
  return new Error(
    handling === "tildel" ? "Kunne ikke tildele saken." : "Kunne ikke frigjøre saken."
  );
}

type Valg = {
  /** Beslutter kan bare tildele seg, ikke frigjøre. */
  rolle?: Tildelingsrolle;
  onError?: (error: Error) => void;
};

export function useTildeling(sakId: string, { rolle = "saksbehandler", onError }: Valg = {}) {
  const { mutate, cache } = useSWRConfig();
  const innloggetAnsatt = useInnloggetAnsatt();
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const sakUrl = SAKSBEHANDLING_SAK_URL(sakId);

  const oppdaterCache = async (oppdater: Oppdatering) => {
    await mutate<SakerResponse>(
      SAKSBEHANDLING_SAKER_URL,
      (liste) =>
        liste && {
          ...liste,
          saker: liste.saker.map((s) => (s.sakId === sakId ? oppdater(s) : s)),
        },
      { revalidate: false }
    );
    await mutate<SakDetaljer>(sakUrl, (sak) => sak && oppdater(sak), { revalidate: false });
  };

  const revalider = () =>
    Promise.all([
      mutate(SAKSBEHANDLING_SAKER_URL),
      mutate(sakUrl),
      mutate(SAKSBEHANDLING_SAKSLOGG_URL(sakId)),
    ]);

  const utfoer = async (handling: Handling, oppdater: Oppdatering) => {
    setIsSaving(true);
    setError(null);

    const listeFoer = cache.get(SAKSBEHANDLING_SAKER_URL)?.data as SakerResponse | undefined;
    const sakFoer = cache.get(sakUrl)?.data as SakDetaljer | undefined;
    await oppdaterCache(oppdater);

    try {
      const url =
        rolle === "beslutter"
          ? SAKSBEHANDLING_BESLUTTER_TILDELING_URL(sakId)
          : SAKSBEHANDLING_TILDELING_URL(sakId);
      await fetchJson(url, {
        method: handling === "tildel" ? "POST" : "DELETE",
      });
      setTimeout(revalider, REVALIDER_ETTER_MS);
      return true;
    } catch (e) {
      await mutate(SAKSBEHANDLING_SAKER_URL, listeFoer, { revalidate: false });
      await mutate(sakUrl, sakFoer, { revalidate: false });
      void revalider();
      const feil = feilmelding(e, handling, rolle);
      setError(feil);
      onError?.(feil);
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  const tildelMeg = () =>
    utfoer("tildel", (sak) => {
      if (!innloggetAnsatt) return sak;
      if (rolle === "beslutter") {
        return {
          ...sak,
          beslutterIdent: innloggetAnsatt.id,
          beslutterNavn: innloggetAnsatt.navn,
          kanTildeleMegSomBeslutter: false,
        };
      }
      return {
        ...sak,
        saksbehandlerIdent: innloggetAnsatt.id,
        saksbehandlerNavn: innloggetAnsatt.navn,
        kanTildeleMeg: false,
        status: sak.status === "OPPRETTET" ? "UNDER_BEHANDLING" : sak.status,
      };
    });

  const frigjoer = () =>
    utfoer("frigjoer", (sak) => ({ ...sak, saksbehandlerIdent: null, saksbehandlerNavn: null }));

  return { tildelMeg, frigjoer, isSaving, error };
}
