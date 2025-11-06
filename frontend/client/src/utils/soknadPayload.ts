import { createEmptyInputs, type Inputs } from "../pages/types";

export type DraftDto = {
  id?: string;
  status?: "utkast" | "innsendt";
  virksomhet?: {
    virksomhetsnummer: string;
    kontaktperson: {
      navn: string;
      epost: string;
      telefon: string;
    };
  };
  ansatt?: {
    fodselsnummer: string;
    navn: string;
  };
  ekspert?: {
    navn: string;
    virksomhet: string;
    kompetanse: string;
    problemstilling: string;
  };
  tiltak?: {
    forTilrettelegging: string;
  };
  bestilling?: {
    kostnad: string;
    startDato: string;
  };
  nav?: {
    kontakt: string;
  };
  bistand?: string;
  opprettetAv?: string | null;
  opprettetTidspunkt?: string | null;
  innsendtTidspunkt?: string | null;
};

export type DraftPayload = {
  virksomhet: {
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
  tiltak: {
    forTilrettelegging: string;
  };
  bestilling: {
    kostnad: string;
    startDato: string;
  };
  nav: {
    kontakt: string;
  };
};

export type SkjemaPayload = DraftPayload & {
  id: string;
};

const normalizeKostnad = (value: Inputs["bestilling"]["kostnad"]) => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value.toString() : "";
  }
  if (typeof value === "string") {
    return value.trim();
  }
  return "";
};

const normalizeStartDato = (value: Inputs["bestilling"]["startDato"]) => value?.trim() ?? "";

export const buildDraftPayload = (inputs: Inputs): DraftPayload => ({
  virksomhet: {
    virksomhetsnummer: inputs.virksomhet.virksomhetsnummer,
    kontaktperson: {
      navn: inputs.virksomhet.kontaktperson.navn,
      epost: inputs.virksomhet.kontaktperson.epost,
      telefon: inputs.virksomhet.kontaktperson.telefon,
    },
  },
  ansatt: {
    fodselsnummer: inputs.ansatt.fodselsnummer,
    navn: inputs.ansatt.navn,
  },
  ekspert: {
    navn: inputs.ekspert.navn,
    virksomhet: inputs.ekspert.virksomhet,
    kompetanse: inputs.ekspert.kompetanse,
    problemstilling: inputs.ekspert.problemstilling,
  },
  tiltak: {
    forTilrettelegging: inputs.tiltak.forTilrettelegging,
  },
  bestilling: {
    kostnad: normalizeKostnad(inputs.bestilling.kostnad),
    startDato: normalizeStartDato(inputs.bestilling.startDato),
  },
  nav: {
    kontakt: inputs.nav.kontakt,
  },
});

export const buildSkjemaPayload = (id: string, inputs: Inputs): SkjemaPayload => ({
  id,
  ...buildDraftPayload(inputs),
});

export const draftDtoToInputs = (dto: DraftDto | null | undefined): Inputs => {
  const result = createEmptyInputs();
  if (!dto) return result;

  if (dto.virksomhet) {
    result.virksomhet.virksomhetsnummer = dto.virksomhet.virksomhetsnummer;
    result.virksomhet.kontaktperson.navn = dto.virksomhet.kontaktperson.navn;
    result.virksomhet.kontaktperson.epost = dto.virksomhet.kontaktperson.epost;
    result.virksomhet.kontaktperson.telefon = dto.virksomhet.kontaktperson.telefon;
  }

  if (dto.ansatt) {
    result.ansatt.fodselsnummer = dto.ansatt.fodselsnummer;
    result.ansatt.navn = dto.ansatt.navn;
  }

  if (dto.ekspert) {
    result.ekspert.navn = dto.ekspert.navn;
    result.ekspert.virksomhet = dto.ekspert.virksomhet;
    result.ekspert.kompetanse = dto.ekspert.kompetanse;
    result.ekspert.problemstilling = dto.ekspert.problemstilling;
  }

  if (dto.tiltak) {
    result.tiltak.forTilrettelegging = dto.tiltak.forTilrettelegging;
  }

  if (dto.bestilling) {
    const kostnad = dto.bestilling.kostnad;
    result.bestilling.kostnad = kostnad ?? "";

    const startDato = dto.bestilling.startDato?.trim();
    result.bestilling.startDato = startDato ? startDato : null;
  }

  if (dto.nav) {
    result.nav.kontakt = dto.nav.kontakt;
  }

  if (dto.bistand !== undefined) {
    result.bistand = dto.bistand;
  }

  return result;
};
