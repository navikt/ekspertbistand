import { useRef, useState } from "react";
import useSWR from "swr";
import type { AnsattEnhet } from "../mock/ansatt";
import { fetchJson } from "../utils/api";
import { SAKSBEHANDLING_KOMMUNEFILTER_URL } from "../utils/constants";
import { HttpError } from "../utils/http";
import type { SakListeElement } from "./useSaker";

export type Kommune = { nummer: string; navn: string | null };

/** `enhet` er null når ingenting er lagret, eller når den innloggede ikke lenger har enheten. */
type KommunefilterResponse = { enhet: string | null; kommuner: Kommune[] };

/** Tom `kommuner` betyr alle kommuner. */
export type Kommunefilter = { enhet: string; kommuner: string[] };

export type KommuneValg = { label: string; value: string };

export function useKommunefilter(enheter: ReadonlyArray<AnsattEnhet>) {
  // Uten revalidering beholder vi et filter som ikke ble lagret, resten av økten.
  const { data, isLoading, mutate } = useSWR<KommunefilterResponse, HttpError>(
    SAKSBEHANDLING_KOMMUNEFILTER_URL,
    { revalidateOnFocus: false, revalidateIfStale: false }
  );
  const [lagringFeilet, setLagringFeilet] = useState(false);
  // Lagringene går i rekkefølge, så et raskt klikk etter et annet ikke overskrives av det første.
  const koe = useRef<Promise<void>>(Promise.resolve());

  const lagretEnhet = enheter.find((e) => e.nummer === data?.enhet)?.nummer;
  const filter: Kommunefilter = lagretEnhet
    ? { enhet: lagretEnhet, kommuner: (data?.kommuner ?? []).map((k) => k.nummer) }
    : { enhet: enheter[0]?.nummer ?? "", kommuner: [] };

  const lagre = (nytt: Kommunefilter) => {
    const navn = new Map((data?.kommuner ?? []).map((k) => [k.nummer, k.navn]));
    void mutate(
      {
        enhet: nytt.enhet,
        kommuner: nytt.kommuner.map((nummer) => ({ nummer, navn: navn.get(nummer) ?? null })),
      },
      { revalidate: false }
    );
    koe.current = koe.current.then(async () => {
      try {
        await fetchJson(SAKSBEHANDLING_KOMMUNEFILTER_URL, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(nytt),
        });
        setLagringFeilet(false);
      } catch {
        setLagringFeilet(true);
      }
    });
  };

  return {
    filter,
    lagredeKommuner: data?.kommuner ?? [],
    lagre,
    lagringFeilet,
    isLoading,
  };
}

/**
 * Saker uten tilgang til deltakeren mangler kommune, men vises alltid, slik at de ikke forsvinner.
 * Valgene er kommunene på sakene på enheten før kommunefilteret, pluss de valgte kommunene.
 */
export function brukKommunefilter(
  saker: SakListeElement[],
  filter: Kommunefilter,
  lagredeKommuner: Kommune[],
  tilgangssjekkFeilet: boolean
) {
  const paaEnhet = filter.enhet ? saker.filter((s) => s.behandlendeEnhet === filter.enhet) : saker;
  const synlige =
    filter.kommuner.length === 0
      ? paaEnhet
      : paaEnhet.filter(
          (s) =>
            tilgangssjekkFeilet ||
            s.deltakerTilgang?.avvist ||
            (s.kommunenummer !== null && filter.kommuner.includes(s.kommunenummer))
        );

  const lagretNavn = new Map(lagredeKommuner.map((k) => [k.nummer, k.navn]));
  const valg = new Map<string, string>();
  filter.kommuner.forEach((nummer) => valg.set(nummer, lagretNavn.get(nummer) ?? nummer));
  paaEnhet.forEach(({ kommunenummer, kommunenavn }) => {
    if (kommunenummer) {
      valg.set(kommunenummer, kommunenavn ?? valg.get(kommunenummer) ?? kommunenummer);
    }
  });
  const kommuneValg: KommuneValg[] = [...valg]
    .map(([value, label]) => ({ value, label }))
    .sort((a, b) => a.label.localeCompare(b.label, "nb"));

  return { synlige, skjulte: paaEnhet.length - synlige.length, kommuneValg };
}
