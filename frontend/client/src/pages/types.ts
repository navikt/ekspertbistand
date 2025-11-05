export type Inputs = {
  virksomhet: {
    navn: string;
    virksomhetsnummer: string;
    kontaktperson: {
      navn: string;
      epost: string;
      telefon: string;
    };
  };
  ansatt: {
    fodselsnummer: string;
    navn: string;
  };
  ekspert: {
    navn: string;
    virksomhet: string;
    kompetanse: string;
    problemstilling: string;
  };
  bistand: string;
  tiltak: {
    forTilrettelegging: string;
  };
  bestilling: {
    kostnad: number | string;
    startDato: string | null;
  };
  nav: {
    kontakt: string;
  };
};

export const STEP1_FIELDS = [
  "virksomhet.virksomhetsnummer",
  "virksomhet.navn",
  "virksomhet.kontaktperson.navn",
  "virksomhet.kontaktperson.epost",
  "virksomhet.kontaktperson.telefon",
  "ansatt.fodselsnummer",
  "ansatt.navn",
  "ekspert.navn",
  "ekspert.virksomhet",
  "ekspert.kompetanse",
] as const satisfies ReadonlyArray<keyof Inputs | string>;

export const STEP2_FIELDS = [
  "ekspert.problemstilling",
  "bistand",
  "tiltak.forTilrettelegging",
  "bestilling.kostnad",
  "bestilling.startDato",
  "nav.kontakt",
] as const satisfies ReadonlyArray<keyof Inputs | string>;

export const createEmptyInputs = (): Inputs => ({
  virksomhet: {
    navn: "",
    virksomhetsnummer: "",
    kontaktperson: {
      navn: "",
      epost: "",
      telefon: "",
    },
  },
  ansatt: {
    fodselsnummer: "",
    navn: "",
  },
  ekspert: {
    navn: "",
    virksomhet: "",
    kompetanse: "",
    problemstilling: "",
  },
  bistand: "",
  tiltak: {
    forTilrettelegging: "",
  },
  bestilling: {
    kostnad: "",
    startDato: null,
  },
  nav: {
    kontakt: "",
  },
});

// Shallow merge helper that ignores undefined values.
const mergeShallow = <T extends Record<string, unknown>>(base: T, update?: Partial<T>): T => {
  if (!update) return { ...base } as T;
  const result = { ...base } as Record<string, unknown>;
  for (const [key, val] of Object.entries(update)) {
    if (val !== undefined) result[key] = val;
  }
  return result as T;
};

export const mergeInputs = (base: Partial<Inputs> | undefined, update: Partial<Inputs>): Inputs => {
  const b = (base as Inputs | undefined) ?? createEmptyInputs();

  const virksomhet = mergeShallow(b.virksomhet, update.virksomhet);
  const kontaktperson = mergeShallow(b.virksomhet.kontaktperson, update.virksomhet?.kontaktperson);

  return {
    virksomhet: { ...virksomhet, kontaktperson },
    ansatt: mergeShallow(b.ansatt, update.ansatt),
    ekspert: mergeShallow(b.ekspert, update.ekspert),
    bistand: (update.bistand as string | undefined) ?? b.bistand,
    tiltak: mergeShallow(b.tiltak, update.tiltak),
    bestilling: mergeShallow(b.bestilling, update.bestilling),
    nav: mergeShallow(b.nav, update.nav),
  };
};
