import { createEmptyInputs, type Inputs } from "../pages/types";

export type DraftDto = {
  id?: string;
  status?: "utkast" | "innsendt";
  virksomhet?: {
    navn?: string;
    virksomhetsnummer?: string;
    kontaktperson?: {
      navn?: string;
      epost?: string;
      telefon?: string;
    };
  };
  ansatt?: {
    fodselsnummer?: string;
    navn?: string;
  };
  ekspert?: {
    navn?: string;
    virksomhet?: string;
    kompetanse?: string;
  };
  behovForBistand?: {
    problemstilling?: string | null;
    bistand?: string | null;
    tiltak?: string | null;
    kostnad?: number | string | null;
    startDato?: string | null;
    navKontakt?: string | null;
  };
  opprettetAv?: string | null;
  opprettetTidspunkt?: string | null;
  innsendtTidspunkt?: string | null;
};

export type DraftPayload = Omit<
  DraftDto,
  "id" | "status" | "opprettetAv" | "opprettetTidspunkt" | "innsendtTidspunkt"
>;

export type SkjemaPayload = {
  id: string;
  virksomhet: NonNullable<DraftPayload["virksomhet"]>;
  ansatt: NonNullable<DraftPayload["ansatt"]>;
  ekspert: Required<NonNullable<DraftPayload["ekspert"]>>;
  behovForBistand: Required<NonNullable<DraftPayload["behovForBistand"]>>;
};

const normalizeKostnad = (value: Inputs["behovForBistand"]["kostnad"]) => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value.toString() : "";
  }
  if (typeof value === "string") {
    return value.trim();
  }
  return "";
};

const normalizeModernStartDato = (value: Inputs["behovForBistand"]["startDato"]) => value ?? null;

const buildBehovForBistandPayload = (inputs: Inputs) => {
  const kostnad = normalizeKostnad(inputs.behovForBistand.kostnad);
  const startDato = normalizeModernStartDato(inputs.behovForBistand.startDato);

  return {
    problemstilling: inputs.behovForBistand.problemstilling,
    bistand: inputs.behovForBistand.bistand,
    tiltak: inputs.behovForBistand.tiltak,
    kostnad: kostnad === "" ? null : kostnad,
    startDato,
    navKontakt: inputs.behovForBistand.navKontakt,
  };
};

export const buildDraftPayload = (inputs: Inputs): DraftPayload => ({
  virksomhet: {
    navn: inputs.virksomhet.navn,
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
  },
  behovForBistand: buildBehovForBistandPayload(inputs),
});

export const buildSkjemaPayload = (id: string, inputs: Inputs): SkjemaPayload => {
  const base = buildDraftPayload(inputs);
  return {
    id,
    virksomhet: {
      navn: base.virksomhet?.navn ?? "",
      virksomhetsnummer: base.virksomhet?.virksomhetsnummer ?? "",
      kontaktperson: {
        navn: base.virksomhet?.kontaktperson?.navn ?? "",
        epost: base.virksomhet?.kontaktperson?.epost ?? "",
        telefon: base.virksomhet?.kontaktperson?.telefon ?? "",
      },
    },
    ansatt: {
      fodselsnummer: base.ansatt?.fodselsnummer ?? "",
      navn: base.ansatt?.navn ?? "",
    },
    ekspert: {
      navn: base.ekspert?.navn ?? "",
      virksomhet: base.ekspert?.virksomhet ?? "",
      kompetanse: base.ekspert?.kompetanse ?? "",
    },
    behovForBistand: {
      problemstilling: base.behovForBistand?.problemstilling ?? "",
      bistand: base.behovForBistand?.bistand ?? "",
      tiltak: base.behovForBistand?.tiltak ?? "",
      kostnad: base.behovForBistand?.kostnad ?? "",
      startDato: base.behovForBistand?.startDato ?? null,
      navKontakt: base.behovForBistand?.navKontakt ?? "",
    },
  };
};

export const draftDtoToInputs = (dto: DraftDto | null | undefined): Inputs => {
  const result = createEmptyInputs();
  if (!dto) return result;

  if (dto.virksomhet?.virksomhetsnummer !== undefined) {
    result.virksomhet.virksomhetsnummer = dto.virksomhet.virksomhetsnummer;
  }
  if (dto.virksomhet?.navn !== undefined) {
    result.virksomhet.navn = dto.virksomhet.navn ?? "";
  }
  if (dto.virksomhet?.kontaktperson) {
    result.virksomhet.kontaktperson.navn = dto.virksomhet.kontaktperson.navn ?? "";
    result.virksomhet.kontaktperson.epost = dto.virksomhet.kontaktperson.epost ?? "";
    result.virksomhet.kontaktperson.telefon = dto.virksomhet.kontaktperson.telefon ?? "";
  }

  if (dto.ansatt) {
    result.ansatt.fodselsnummer = dto.ansatt.fodselsnummer ?? "";
    result.ansatt.navn = dto.ansatt.navn ?? "";
  }

  if (dto.ekspert) {
    result.ekspert.navn = dto.ekspert.navn ?? "";
    result.ekspert.virksomhet = dto.ekspert.virksomhet ?? "";
    result.ekspert.kompetanse = dto.ekspert.kompetanse ?? "";
  }

  if (dto.behovForBistand) {
    result.behovForBistand.problemstilling = dto.behovForBistand.problemstilling ?? "";
    result.behovForBistand.bistand = dto.behovForBistand.bistand ?? "";
    result.behovForBistand.tiltak = dto.behovForBistand.tiltak ?? "";
    const kostnad = dto.behovForBistand.kostnad;
    if (typeof kostnad === "number") {
      result.behovForBistand.kostnad = Number.isFinite(kostnad) ? kostnad : "";
    } else if (typeof kostnad === "string") {
      result.behovForBistand.kostnad = kostnad.trim();
    } else if (kostnad === null) {
      result.behovForBistand.kostnad = "";
    }
    const startDato = dto.behovForBistand.startDato;
    if (typeof startDato === "string") {
      const trimmed = startDato.trim();
      result.behovForBistand.startDato = trimmed.length > 0 ? trimmed : null;
    } else if (startDato === null) {
      result.behovForBistand.startDato = null;
    }
    result.behovForBistand.navKontakt = dto.behovForBistand.navKontakt ?? "";
  }

  return result;
};
