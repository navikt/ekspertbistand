import { z } from "zod";
import { createEmptyInputs, type SoknadInputs } from "./schema";
import { draftDtoServerSchema, draftPayloadServerSchema } from "./server-schemas";
import { ensureIsoDateString } from "../../utils/date";

const normalizeStartdato = (value: SoknadInputs["behovForBistand"]["startdato"]): string =>
  ensureIsoDateString(typeof value === "string" ? value : null);

const normalizeTimer = (value: SoknadInputs["behovForBistand"]["timer"]): string =>
  typeof value === "string" ? value.trim() : "";

const normalizeEstimertKostnad = (
  value: SoknadInputs["behovForBistand"]["estimertKostnad"]
): string => (typeof value === "string" ? value.trim() : "");

const mapInputsToPayload = (inputs: SoknadInputs) => ({
  virksomhet: {
    virksomhetsnummer: inputs.virksomhet.virksomhetsnummer,
    virksomhetsnavn: inputs.virksomhet.virksomhetsnavn,
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
    timer: normalizeTimer(inputs.behovForBistand.timer),
    estimertKostnad: normalizeEstimertKostnad(inputs.behovForBistand.estimertKostnad),
    tilrettelegging: inputs.behovForBistand.tilrettelegging,
    startdato: normalizeStartdato(inputs.behovForBistand.startdato),
  },
  nav: {
    kontaktperson: inputs.nav.kontaktperson,
  },
});

export type DraftDto = z.output<typeof draftDtoServerSchema>;

export type DraftPayload = z.output<typeof draftPayloadServerSchema>;
export type SkjemaPayload = DraftPayload & {
  id: string;
};

export const buildDraftPayload = (inputs: SoknadInputs): DraftPayload =>
  draftPayloadServerSchema.parse(mapInputsToPayload(inputs));

export const buildSkjemaPayload = (id: string, inputs: SoknadInputs): SkjemaPayload => ({
  id,
  ...buildDraftPayload(inputs),
});

const draftDtoToInputsSchema = draftDtoServerSchema.transform((dto) => {
  const inputs = createEmptyInputs();

  if (dto.virksomhet) {
    inputs.virksomhet.virksomhetsnummer = dto.virksomhet.virksomhetsnummer ?? "";
    inputs.virksomhet.virksomhetsnavn = dto.virksomhet.virksomhetsnavn ?? "";
    inputs.virksomhet.beliggenhetsadresse = dto.virksomhet.beliggenhetsadresse ?? null;
    const kontaktperson = dto.virksomhet.kontaktperson;
    if (kontaktperson) {
      inputs.virksomhet.kontaktperson.navn = kontaktperson.navn ?? "";
      inputs.virksomhet.kontaktperson.epost = kontaktperson.epost ?? "";
      inputs.virksomhet.kontaktperson.telefonnummer = kontaktperson.telefonnummer ?? "";
    }
  }

  if (dto.ansatt) {
    inputs.ansatt.fnr = dto.ansatt.fnr ?? "";
    inputs.ansatt.navn = dto.ansatt.navn ?? "";
  }

  if (dto.ekspert) {
    inputs.ekspert.navn = dto.ekspert.navn ?? "";
    inputs.ekspert.virksomhet = dto.ekspert.virksomhet ?? "";
    inputs.ekspert.kompetanse = dto.ekspert.kompetanse ?? "";
  }

  if (dto.behovForBistand) {
    inputs.behovForBistand.begrunnelse = dto.behovForBistand.begrunnelse ?? "";
    inputs.behovForBistand.behov = dto.behovForBistand.behov ?? "";
    inputs.behovForBistand.timer = dto.behovForBistand.timer ?? "";
    inputs.behovForBistand.estimertKostnad = dto.behovForBistand.estimertKostnad ?? "";
    inputs.behovForBistand.tilrettelegging = dto.behovForBistand.tilrettelegging ?? "";
    const startdato = dto.behovForBistand.startdato ?? "";
    inputs.behovForBistand.startdato = startdato.length > 0 ? startdato : null;
  }

  if (dto.nav) {
    inputs.nav.kontaktperson = dto.nav.kontaktperson ?? "";
  }

  return inputs;
});

export const draftDtoToInputs = (dto: DraftDto | null | undefined): SoknadInputs => {
  if (!dto) return createEmptyInputs();
  return draftDtoToInputsSchema.parse(dto);
};
