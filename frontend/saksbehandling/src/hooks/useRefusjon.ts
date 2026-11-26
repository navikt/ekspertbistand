import { useEffect, useRef, useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import { fetchJson } from "../utils/api";
import {
  SAKSBEHANDLING_REFUSJON_URL,
  SAKSBEHANDLING_REFUSJON_VURDERING_URL,
  SAKSBEHANDLING_SAK_URL,
  SAKSBEHANDLING_SAKSLOGG_URL,
} from "../utils/constants";
import { HttpError } from "../utils/http";

export const REFUSJON_ÅRSAKER = {
  BELOP_MAA_JUSTERES: {
    label: "Justere beløp for refusjonskravet",
    kontrolltekst: "Beløpet i refusjonskravet stemmer med de dokumenterte utgiftene.",
  },
  DOKUMENT_IKKE_FAKTURA: {
    label: "Dokumentet er ikke en faktura",
    kontrolltekst: "Vedleggene er fakturaer som dokumenterer utgiftene til ekspertbistand.",
  },
  DOKUMENT_IKKE_LESELIG: {
    label: "Dokumentet er ikke leselig",
    kontrolltekst: "Vedleggene er leselige og av god kvalitet.",
  },
  ANNET: {
    label: "Annet",
    kontrolltekst:
      "For at vi skal kunne behandle refusjonskravet må du korrigere opplysningene. Vennligst send refusjonskravet på nytt og kontroller at vedleggene du laster opp er korrekte. Dokumentene må være leselige, relevante for refusjonskravet og inneholde nødvendig informasjon for at vi skal kunne behandle saken.",
  },
} as const;
export type RefusjonskravArsak = keyof typeof REFUSJON_ÅRSAKER;
export const REFUSJON_ÅRSAKSREKKEFØLGE = Object.keys(REFUSJON_ÅRSAKER) as RefusjonskravArsak[];
const REFUSJON_BESKJED =
  "For at vi skal kunne behandle refusjonskravet, må du korrigere opplysningene. Send refusjonskravet på nytt.";

export function refusjonBeskjed(arsaker: RefusjonskravArsak[]): string {
  const innledning = arsaker.includes("ANNET")
    ? REFUSJON_ÅRSAKER.ANNET.kontrolltekst
    : REFUSJON_BESKJED;
  const punkter = REFUSJON_ÅRSAKSREKKEFØLGE.filter(
    (arsak) => arsak !== "ANNET" && arsaker.includes(arsak)
  );
  return (
    innledning +
    (punkter.length
      ? `\n\nKontroller at:\n${punkter.map((arsak) => `- ${REFUSJON_ÅRSAKER[arsak].kontrolltekst}`).join("\n")}`
      : "")
  );
}

export type RefusjonStatusDto = {
  refusjonskravId: string;
  status: "MOTTATT" | "NY_INNSENDING_KREVES";
  arsaker: RefusjonskravArsak[];
  begrunnelse: string | null;
  vurdertAvIdent: string | null;
  vurdertAvNavn: string | null;
  vurdertTidspunkt: string | null;
  belopKroner: number;
  opprettet: string;
  vedlegg: {
    id: string;
    filnavn: string;
    storrelse: number;
  }[];
};

export function useRefusjon(sakId: string, enabled: boolean) {
  const { data, error, isLoading } = useSWR<RefusjonStatusDto, HttpError>(
    enabled && sakId ? SAKSBEHANDLING_REFUSJON_URL(sakId) : null,
    { revalidateOnFocus: false }
  );

  return { refusjon: data ?? null, error, isLoading };
}

function vurderingsfeil(error: unknown): string {
  if (error instanceof HttpError) {
    if (error.status === 400) return "Velg minst én årsak og prøv igjen.";
    if (error.status === 403) return "Du har ikke tilgang til å vurdere refusjonskravet.";
    if (error.status === 404) return "Vi fant ikke refusjonskravet. Last siden på nytt.";
    if (error.kode === "SLUTTRAPPORT_IKKE_BEKREFTET")
      return "Sluttrapporten må være bekreftet før refusjonskravet kan vurderes.";
    if (error.status === 409)
      return "Saken eller refusjonskravet er endret. Last siden på nytt før du prøver igjen.";
  }
  return "Kunne ikke lagre vurderingen av refusjonskravet. Prøv igjen senere.";
}

export function useRefusjonVurdering(sakId: string) {
  const { mutate } = useSWRConfig();
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [erMottatt, setErMottatt] = useState(false);
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), []);

  const vurder = async (krav: RefusjonStatusDto, arsaker: RefusjonskravArsak[]) => {
    if (controller.current) return;
    const abort = new AbortController();
    controller.current = abort;
    setIsSaving(true);
    setError(null);
    let mottatt = erMottatt;
    const url = SAKSBEHANDLING_REFUSJON_URL(sakId);
    try {
      if (!mottatt) {
        await fetchJson(SAKSBEHANDLING_REFUSJON_VURDERING_URL(sakId, krav.refusjonskravId), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ arsaker }),
          signal: abort.signal,
        });
        mottatt = true;
        setErMottatt(true);
      }
      for (let forsøk = 0; forsøk < 20; forsøk++) {
        const lagret = await fetchJson<RefusjonStatusDto>(url, { signal: abort.signal });
        if (
          lagret?.refusjonskravId === krav.refusjonskravId &&
          lagret.status === "NY_INNSENDING_KREVES"
        ) {
          await Promise.all([
            mutate(url, lagret, { revalidate: false }),
            mutate(SAKSBEHANDLING_SAK_URL(sakId)),
            mutate(SAKSBEHANDLING_SAKSLOGG_URL(sakId)),
          ]);
          return;
        }
        if (lagret?.refusjonskravId !== krav.refusjonskravId)
          throw new Error("Refusjonskravet er endret");
        await new Promise<void>((resolve, reject) => {
          const avbryt = () => {
            clearTimeout(timer);
            reject(new DOMException("Avbrutt", "AbortError"));
          };
          const timer = setTimeout(() => {
            abort.signal.removeEventListener("abort", avbryt);
            resolve();
          }, 500);
          abort.signal.addEventListener("abort", avbryt, { once: true });
        });
      }
      setError(
        "Nav har mottatt vurderingen, men lagringen er ikke bekreftet. Hent status på nytt."
      );
    } catch (e) {
      if (!abort.signal.aborted) {
        setError(
          mottatt
            ? "Nav har mottatt vurderingen, men status kunne ikke bekreftes. Hent status på nytt."
            : vurderingsfeil(e)
        );
      }
    } finally {
      if (!abort.signal.aborted) setIsSaving(false);
      controller.current = null;
    }
  };
  return { vurder, isSaving, error, erMottatt };
}
