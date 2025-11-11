import { createEmptyInputs, type SoknadInputs } from "../features/soknad/schema";
import { ensureIsoDateString } from "./dates";

export type DraftDto = {
  id?: string;
  status?: "utkast" | "innsendt";
  virksomhet?: {
    virksomhetsnummer: string;
    virksomhetsnavn: string;
    kontaktperson: {
      navn: string;
      epost: string;
      telefonnummer: string;
    };
  };
  ansatt?: {
    fnr: string;
    navn: string;
  };
  ekspert?: {
    navn: string;
    virksomhet: string;
    kompetanse: string;
  };
  behovForBistand?: {
    begrunnelse: string;
    behov: string;
    estimertKostnad: number;
    tilrettelegging: string;
    startdato: string;
  };
  nav?: {
    kontaktperson: string;
  };
  opprettetAv?: string | null;
  opprettetTidspunkt?: string | null;
  innsendtTidspunkt?: string | null;
};

export type DraftPayload = {
  virksomhet: {
    virksomhetsnummer: string;
    virksomhetsnavn: string;
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
    estimertKostnad: number;
    tilrettelegging: string;
    startdato: string;
  };
  nav: {
    kontaktperson: string;
  };
};

export type SkjemaPayload = DraftPayload & {
  id: string;
};

const normalizeEstimertKostnad = (
  value: SoknadInputs["behovForBistand"]["estimertKostnad"]
): number => {
  if (typeof value === "number" && Number.isFinite(value)) {
    return Math.round(value);
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (trimmed.length === 0) return 0;
    const parsed = Number.parseInt(trimmed, 10);
    if (Number.isFinite(parsed)) {
      return Math.round(parsed);
    }
  }
  return 0;
};

const normalizeStartdato = (value: SoknadInputs["behovForBistand"]["startdato"]): string =>
  ensureIsoDateString(typeof value === "string" ? value : null);

export const buildDraftPayload = (inputs: SoknadInputs): DraftPayload => ({
  virksomhet: {
    virksomhetsnummer: inputs.virksomhet.virksomhetsnummer,
    virksomhetsnavn: inputs.virksomhet.navn,
    kontaktperson: {
      navn: inputs.virksomhet.kontaktperson.navn,
      epost: inputs.virksomhet.kontaktperson.epost,
      telefonnummer: inputs.virksomhet.kontaktperson.telefonnummer,
    },
  },
  ansatt: {
    fnr: inputs.ansatt.fnr,
    navn: inputs.ansatt.navn,
  },
  ekspert: {
    navn: inputs.ekspert.navn,
    virksomhet: inputs.ekspert.virksomhet,
    kompetanse: inputs.ekspert.kompetanse,
  },
  behovForBistand: {
    begrunnelse: inputs.behovForBistand.begrunnelse,
    behov: inputs.behovForBistand.behov,
    estimertKostnad: normalizeEstimertKostnad(inputs.behovForBistand.estimertKostnad),
    tilrettelegging: inputs.behovForBistand.tilrettelegging,
    startdato: normalizeStartdato(inputs.behovForBistand.startdato),
  },
  nav: {
    kontaktperson: inputs.nav.kontaktperson,
  },
});

export const buildSkjemaPayload = (id: string, inputs: SoknadInputs): SkjemaPayload => ({
  id,
  ...buildDraftPayload(inputs),
});

export const draftDtoToInputs = (dto: DraftDto | null | undefined): SoknadInputs => {
  const result = createEmptyInputs();
  if (!dto) return result;

  if (dto.virksomhet) {
    result.virksomhet.virksomhetsnummer = dto.virksomhet.virksomhetsnummer;
    result.virksomhet.navn = dto.virksomhet.virksomhetsnavn;
    result.virksomhet.kontaktperson.navn = dto.virksomhet.kontaktperson.navn;
    result.virksomhet.kontaktperson.epost = dto.virksomhet.kontaktperson.epost;
    result.virksomhet.kontaktperson.telefonnummer = dto.virksomhet.kontaktperson.telefonnummer;
  }

  if (dto.ansatt) {
    result.ansatt.fnr = dto.ansatt.fnr;
    result.ansatt.navn = dto.ansatt.navn;
  }

  if (dto.ekspert) {
    result.ekspert.navn = dto.ekspert.navn;
    result.ekspert.virksomhet = dto.ekspert.virksomhet;
    result.ekspert.kompetanse = dto.ekspert.kompetanse;
  }

  if (dto.behovForBistand) {
    result.behovForBistand.begrunnelse = dto.behovForBistand.begrunnelse ?? "";
    result.behovForBistand.behov = dto.behovForBistand.behov ?? "";
    const kostnad = dto.behovForBistand.estimertKostnad;
    result.behovForBistand.estimertKostnad = Number.isFinite(kostnad) ? kostnad : "";
    result.behovForBistand.tilrettelegging = dto.behovForBistand.tilrettelegging ?? "";
    result.behovForBistand.startdato =
      dto.behovForBistand.startdato.length > 0 ? dto.behovForBistand.startdato : null;
  }

  if (dto.nav) {
    result.nav.kontaktperson = dto.nav.kontaktperson;
  }

  return result;
};
