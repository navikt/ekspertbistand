import { useState } from "react";
import useSWR from "swr";
import { useSWRConfig } from "swr";
import { fetchJson } from "../utils/api";
import {
  SAKSBEHANDLING_SAKSLOGG_URL,
  SAKSBEHANDLING_SAK_URL,
  SAKSBEHANDLING_SLUTTRAPPORT_URL,
  SAKSBEHANDLING_SLUTTRAPPORT_VURDERING_URL,
} from "../utils/constants";
import { HttpError } from "../utils/http";

export type SluttrapportStatus = "MOTTATT" | "BEKREFTET" | "NY_INNSENDING_KREVES";

export type SluttrapportStatusDto = {
  sluttrapportId: string;
  filnavn: string;
  opprettet: string;
  storrelse: number;
  status: SluttrapportStatus;
  vurdertTidspunkt: string | null;
  begrunnelse: string | null;
};

export type SluttrapportVurderingInput = {
  utfall: Exclude<SluttrapportStatus, "MOTTATT">;
  begrunnelse?: string;
};

const REVALIDER_ETTER_MS = 500;

function feilmelding409(kode: string | undefined) {
  switch (kode) {
    case "SAK_UGYLDIG_KILDE":
      return "Saken behandles i Arena, og sluttrapporten kan ikke vurderes her.";
    case "SAK_UGYLDIG_STATUS":
      return "Saken har endret status. Last siden på nytt før du prøver igjen.";
    case "SLUTTRAPPORT_IKKE_NYESTE":
      return "Det finnes en nyere sluttrapport. Last siden på nytt for å vurdere den.";
    case "SLUTTRAPPORT_ALLEREDE_VURDERT":
      return "Sluttrapporten er allerede vurdert. Last siden på nytt for å se status.";
    default:
      return "Sluttrapporten kan ikke vurderes nå. Last siden på nytt og prøv igjen.";
  }
}

function feilmelding(e: unknown): Error {
  if (e instanceof HttpError) {
    switch (e.status) {
      case 400:
        return new Error("Kontroller begrunnelsen og prøv igjen.");
      case 403:
        return new Error(
          e.begrunnelse ?? "Du har ikke tilgang til å vurdere sluttrapporten i denne saken."
        );
      case 404:
        return new Error("Vi fant ikke sluttrapporten. Last siden på nytt og prøv igjen.");
      case 409:
        return new Error(feilmelding409(e.kode));
      case 503:
        return new Error("Tjenesten er midlertidig utilgjengelig. Prøv igjen om litt.");
    }
  }
  return new Error("Kunne ikke lagre vurderingen av sluttrapporten. Prøv igjen senere.");
}

export function useSluttrapport(sakId: string, enabled: boolean) {
  const { data, error, isLoading } = useSWR<SluttrapportStatusDto, HttpError>(
    enabled && sakId ? SAKSBEHANDLING_SLUTTRAPPORT_URL(sakId) : null,
    { revalidateOnFocus: false }
  );

  return { sluttrapport: data ?? null, error, isLoading };
}

export function useSluttrapportVurdering(sakId: string) {
  const { mutate } = useSWRConfig();
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const sluttrapportUrl = SAKSBEHANDLING_SLUTTRAPPORT_URL(sakId);
  const sakUrl = SAKSBEHANDLING_SAK_URL(sakId);

  const vurder = async (sluttrapport: SluttrapportStatusDto, input: SluttrapportVurderingInput) => {
    setIsSaving(true);
    setError(null);
    try {
      await fetchJson(
        SAKSBEHANDLING_SLUTTRAPPORT_VURDERING_URL(sakId, sluttrapport.sluttrapportId),
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(input),
        }
      );

      await mutate<SluttrapportStatusDto>(
        sluttrapportUrl,
        {
          ...sluttrapport,
          status: input.utfall,
          vurdertTidspunkt: new Date().toISOString(),
          begrunnelse: input.utfall === "NY_INNSENDING_KREVES" ? (input.begrunnelse ?? null) : null,
        },
        { revalidate: false }
      );
      setTimeout(() => {
        void mutate(sluttrapportUrl);
        void mutate(sakUrl);
        void mutate(SAKSBEHANDLING_SAKSLOGG_URL(sakId));
      }, REVALIDER_ETTER_MS);
      return true;
    } catch (e) {
      if (e instanceof HttpError && (e.status === 403 || e.status === 409)) {
        void mutate(sluttrapportUrl);
        void mutate(sakUrl);
      }
      setError(feilmelding(e));
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  return { vurder, isSaving, error };
}
