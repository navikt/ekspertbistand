import type { Inputs } from "./types";

export const virksomhetsnummerPattern = /^\d{9}$/;
export const telephonePattern = /^\d{8}$/;
export const fnrPattern = /^\d{11}$/;
export const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const ensureText = (value: unknown) => (typeof value === "string" ? value.trim() : "");

const requireText = (value: unknown, message: string): true | string =>
  ensureText(value) ? true : message;

export const validateVirksomhetsnummer = (value: unknown) => {
  const text = ensureText(value);
  if (!text) return "Du må velge virksomhet.";
  if (!virksomhetsnummerPattern.test(text)) return "Virksomhetsnummer må være 9 siffer.";
  return true;
};

export const validateKontaktpersonNavn = (value: unknown) =>
  requireText(value, "Du må fylle ut navn.");

export const validateKontaktpersonEpost = (value: unknown) => {
  const text = ensureText(value);
  if (!text) return "Du må fylle ut e-post.";
  if (!emailPattern.test(text)) return "Ugyldig e-postadresse.";
  return true;
};

export const validateKontaktpersonTelefonnummer = (value: unknown) => {
  const text = ensureText(value);
  if (!text) return "Du må fylle ut telefonnummer.";
  if (!telephonePattern.test(text)) return "Telefonnummer må være 8 siffer.";
  return true;
};

export const validateAnsattFnr = (value: unknown) => {
  const text = ensureText(value);
  if (!text) return "Du må fylle ut fødselsnummer.";
  if (!fnrPattern.test(text)) return "Fødselsnummer må være 11 siffer.";
  return true;
};

export const validateAnsattNavn = (value: unknown) => requireText(value, "Du må fylle ut navn.");

export const validateEkspertNavn = (value: unknown) =>
  requireText(value, "Du må fylle ut navn på ekspert.");

export const validateEkspertVirksomhet = (value: unknown) =>
  requireText(value, "Du må fylle ut tilknyttet virksomhet.");

export const validateEkspertKompetanse = (value: unknown) =>
  requireText(value, "Du må beskrive ekspertens kompetanse.");

export const validateBegrunnelse = (value: unknown) =>
  requireText(value, "Du må beskrive problemstillingen.");

export const validateBehov = (value: unknown) =>
  requireText(value, "Du må beskrive hva dere ønsker hjelp til fra eksperten.");

export const validateTilrettelegging = (value: unknown) =>
  requireText(value, "Du må beskrive tiltak for tilrettelegging.");

export const validateNavKontaktperson = (value: unknown) =>
  requireText(value, "Du må fylle ut hvem i Nav du har drøftet med.");

export const validateEstimertKostnad = (value: Inputs["behovForBistand"]["estimertKostnad"]) => {
  if (value === "" || value === null) return "Du må anslå kostnad.";
  const numeric = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numeric)) return "Kostnad må være et tall.";
  if (numeric < 0) return "Kostnad kan ikke være negativ.";
  if (numeric > 25000) return "Maksimalt beløp er 25 000.";
  return true;
};

export const validateStartdato = (value: Inputs["behovForBistand"]["startdato"]) => {
  if (!value) return "Du må velge en dato.";
  if (Number.isNaN(Date.parse(value))) return "Ugyldig dato.";
  return true;
};

export type ValidationError = {
  id: string;
  message: string;
};

const collectError = (validatorResult: true | string, id: string, errors: ValidationError[]) => {
  if (validatorResult !== true) {
    errors.push({ id, message: validatorResult });
  }
};

export const validateInputs = (values: Inputs): ValidationError[] => {
  const errors: ValidationError[] = [];

  collectError(
    validateVirksomhetsnummer(values.virksomhet.virksomhetsnummer),
    "virksomhet.virksomhetsnummer",
    errors
  );
  collectError(
    validateKontaktpersonNavn(values.virksomhet.kontaktperson.navn),
    "virksomhet.kontaktperson.navn",
    errors
  );
  collectError(
    validateKontaktpersonEpost(values.virksomhet.kontaktperson.epost),
    "virksomhet.kontaktperson.epost",
    errors
  );
  collectError(
    validateKontaktpersonTelefonnummer(values.virksomhet.kontaktperson.telefonnummer),
    "virksomhet.kontaktperson.telefonnummer",
    errors
  );
  collectError(validateAnsattFnr(values.ansatt.fnr), "ansatt.fnr", errors);
  collectError(validateAnsattNavn(values.ansatt.navn), "ansatt.navn", errors);
  collectError(validateEkspertNavn(values.ekspert.navn), "ekspert.navn", errors);
  collectError(validateEkspertVirksomhet(values.ekspert.virksomhet), "ekspert.virksomhet", errors);
  collectError(validateEkspertKompetanse(values.ekspert.kompetanse), "ekspert.kompetanse", errors);
  collectError(
    validateBegrunnelse(values.behovForBistand.begrunnelse),
    "behovForBistand.begrunnelse",
    errors
  );
  collectError(validateBehov(values.behovForBistand.behov), "behovForBistand.behov", errors);
  collectError(
    validateTilrettelegging(values.behovForBistand.tilrettelegging),
    "behovForBistand.tilrettelegging",
    errors
  );
  collectError(
    validateEstimertKostnad(values.behovForBistand.estimertKostnad),
    "behovForBistand.estimertKostnad",
    errors
  );
  collectError(
    validateStartdato(values.behovForBistand.startdato),
    "behovForBistand.startdato",
    errors
  );
  collectError(validateNavKontaktperson(values.nav.kontaktperson), "nav.kontaktperson", errors);

  return errors;
};
