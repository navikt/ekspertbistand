export type Inputs = {
  virksomhet: {
    navn: string;
    virksomhetsnummer: string;
    kontaktperson: {
      navn: string;
      epost: string;
      telefonnummer: string;
    };
  };
  ansatt: {
    fnr: string;
    navn: string;
  };
  ekspert: {
    navn: string;
    virksomhet: string;
    kompetanse: string;
  };
  behovForBistand: {
    begrunnelse: string;
    behov: string;
    estimertKostnad: number | string;
    tilrettelegging: string;
    startdato: string | null;
  };
  nav: {
    kontaktperson: string;
  };
};

export const STEP1_FIELDS = [
  "virksomhet.virksomhetsnummer",
  "virksomhet.navn",
  "virksomhet.kontaktperson.navn",
  "virksomhet.kontaktperson.epost",
  "virksomhet.kontaktperson.telefonnummer",
  "ansatt.fnr",
  "ansatt.navn",
  "ekspert.navn",
  "ekspert.virksomhet",
  "ekspert.kompetanse",
] as const satisfies ReadonlyArray<keyof Inputs | string>;

export const STEP2_FIELDS = [
  "behovForBistand.begrunnelse",
  "behovForBistand.behov",
  "behovForBistand.tilrettelegging",
  "behovForBistand.estimertKostnad",
  "behovForBistand.startdato",
  "nav.kontaktperson",
] as const satisfies ReadonlyArray<keyof Inputs | string>;

export const createEmptyInputs = (): Inputs => ({
  virksomhet: {
    navn: "",
    virksomhetsnummer: "",
    kontaktperson: {
      navn: "",
      epost: "",
      telefonnummer: "",
    },
  },
  ansatt: {
    fnr: "",
    navn: "",
  },
  ekspert: {
    navn: "",
    virksomhet: "",
    kompetanse: "",
  },
  behovForBistand: {
    begrunnelse: "",
    behov: "",
    estimertKostnad: "",
    tilrettelegging: "",
    startdato: null,
  },
  nav: {
    kontaktperson: "",
  },
});
