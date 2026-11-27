import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import { fetchJson } from "../utils/api";
import {
  SAKSBEHANDLING_SAKSLOGG_URL,
  SAKSBEHANDLING_SAKSNOTATER_URL,
  SAKSBEHANDLING_SAKSNOTAT_URL,
} from "../utils/constants";
import { HttpError } from "../utils/http";
import { IKKE_TILGANG_TEKST, medFeilId, tilgangAvvistTekst } from "../utils/tilgangsfeil";
import type { AktorRolle } from "./useSakslogg";

export const SAKSNOTAT_MAKS_LENGDE = 1000;

// API-et svarer 202 før handleren har lagret notatet, se useTildeling.
const REVALIDER_ETTER_MS = 500;

export type Saksnotat = {
  notatId: string;
  tekst: string;
  skrevetAvIdent: string;
  skrevetAvNavn: string;
  skrevetAvRolle: Exclude<AktorRolle, "SYSTEM">;
  opprettet: string;
  endret: string | null;
};

export type SaksnotaterResponse = {
  notater: Saksnotat[];
};

/** `sakId` er null for Arena-saker, som ikke har notater. Da hentes ingenting. */
export function useSaksnotater(sakId: string | null) {
  const { data, error, isLoading } = useSWR<SaksnotaterResponse, HttpError>(
    sakId ? SAKSBEHANDLING_SAKSNOTATER_URL(sakId) : null,
    { revalidateOnFocus: false }
  );

  return { notater: data?.notater, error, isLoading };
}

/** `iFeltet` betyr at feilen gjelder teksten og vises i tekstfeltet, ikke over notatene. */
export type NotatFeil = { melding: string; iFeltet: boolean };

function notatFeil(e: unknown): NotatFeil {
  if (e instanceof HttpError) {
    switch (e.status) {
      case 400:
        if (e.kode === "UGYLDIG_INPUT") {
          return {
            melding: "Notatet inneholder tegn som ikke er tillatt, for eksempel < eller >.",
            iFeltet: true,
          };
        }
        break;
      case 403:
        return {
          melding:
            e.kode === "IKKE_EGET_NOTAT"
              ? "Du kan bare endre og slette notater du har skrevet selv."
              : medFeilId(tilgangAvvistTekst(e, IKKE_TILGANG_TEKST), e),
          iFeltet: false,
        };
      case 409:
        if (e.kode === "SAK_UGYLDIG_KILDE") {
          return { melding: "Arena-saker har ikke notater.", iFeltet: false };
        }
        break;
      case 503:
        return {
          melding: "Vi kunne ikke sjekke tilgangen din akkurat nå. Prøv igjen om litt.",
          iFeltet: false,
        };
    }
  }
  return { melding: "Kunne ikke lagre notatet.", iFeltet: false };
}

type Oppdatering = (notater: Saksnotat[]) => Saksnotat[];

export function useSaksnotatHandlinger(sakId: string) {
  const { mutate, cache } = useSWRConfig();
  const innloggetAnsatt = useInnloggetAnsatt();
  const [isSaving, setIsSaving] = useState(false);

  const url = SAKSBEHANDLING_SAKSNOTATER_URL(sakId);

  const revalider = () => Promise.all([mutate(url), mutate(SAKSBEHANDLING_SAKSLOGG_URL(sakId))]);

  const utfoer = async (
    oppdater: Oppdatering,
    kall: () => Promise<unknown>
  ): Promise<NotatFeil | null> => {
    setIsSaving(true);
    const foer = cache.get(url)?.data as SaksnotaterResponse | undefined;
    await mutate<SaksnotaterResponse>(url, (data) => data && { notater: oppdater(data.notater) }, {
      revalidate: false,
    });

    try {
      await kall();
      setTimeout(revalider, REVALIDER_ETTER_MS);
      return null;
    } catch (e) {
      await mutate(url, foer, { revalidate: false });
      void revalider();
      return notatFeil(e);
    } finally {
      setIsSaving(false);
    }
  };

  const opprett = (tekst: string) => {
    const midlertidigId = `ny-${Date.now()}`;
    const nytt: Saksnotat = {
      notatId: midlertidigId,
      tekst,
      skrevetAvIdent: innloggetAnsatt?.id ?? "",
      skrevetAvNavn: innloggetAnsatt?.navn ?? "",
      skrevetAvRolle: "SAKSBEHANDLER",
      opprettet: new Date().toISOString(),
      endret: null,
    };
    return utfoer(
      (notater) => [nytt, ...notater],
      async () => {
        const svar = await fetchJson<{ notatId: string }>(url, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ tekst }),
        });
        if (svar?.notatId) {
          await mutate<SaksnotaterResponse>(
            url,
            (data) =>
              data && {
                notater: data.notater.map((n) =>
                  n.notatId === midlertidigId ? { ...n, notatId: svar.notatId } : n
                ),
              },
            { revalidate: false }
          );
        }
      }
    );
  };

  const endre = (notatId: string, tekst: string) =>
    utfoer(
      (notater) =>
        notater.map((n) =>
          n.notatId === notatId ? { ...n, tekst, endret: new Date().toISOString() } : n
        ),
      () =>
        fetchJson(SAKSBEHANDLING_SAKSNOTAT_URL(sakId, notatId), {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ tekst }),
        })
    );

  const slett = (notatId: string) =>
    utfoer(
      (notater) => notater.filter((n) => n.notatId !== notatId),
      () => fetchJson(SAKSBEHANDLING_SAKSNOTAT_URL(sakId, notatId), { method: "DELETE" })
    );

  return { opprett, endre, slett, isSaving };
}
