import type { SakListeElement, Virksomhetstype } from "./useSaker";

/** Saker uten tilgang til deltakeren mangler virksomhetstype, men vises alltid, slik at de ikke forsvinner. */
export function brukVirksomhetstypefilter(
  saker: SakListeElement[],
  valgte: Virksomhetstype[],
  tilgangssjekkFeilet: boolean
) {
  if (valgte.length === 0 || tilgangssjekkFeilet) return saker;
  return saker.filter(
    (s) =>
      s.deltakerTilgang?.avvist ||
      (s.virksomhetstype !== null && valgte.includes(s.virksomhetstype))
  );
}

export const virksomhetstypeVisning: Record<
  Virksomhetstype,
  { tekst: string; farge: "meta-lime" | "meta-purple" | "info" | "neutral" }
> = {
  STATLIG: { tekst: "Statlig", farge: "meta-lime" },
  KOMMUNAL: { tekst: "Kommunal", farge: "meta-purple" },
  PRIVAT: { tekst: "Privat", farge: "info" },
  UKJENT: { tekst: "Ukjent", farge: "neutral" },
};
