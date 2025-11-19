import { z } from "zod";
import type { Path } from "react-hook-form";
import { startOfToday } from "../../utils/date";
z.config(z.locales.no());

const virksomhetsnummerPattern = /^\d{9}$/;
const telephonePattern = /^\d{8}$/;
const fnrPattern = /^\d{11}$/;
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const MIN_ESTIMERT_KOSTNAD = 0;
export const MAX_ESTIMERT_KOSTNAD = 100_000;

const trimmedText = (message: string) => z.string().trim().min(1, { message });

const emailField = trimmedText("Du må fylle ut e-post.").refine(
  (value) => emailPattern.test(value),
  {
    message: "Ugyldig e-postadresse.",
  }
);

const phoneField = trimmedText("Du må fylle ut telefonnummer.").refine(
  (value) => telephonePattern.test(value),
  { message: "Telefonnummer må være 8 siffer." }
);

const orgnrField = trimmedText("Du må velge virksomhet.").refine(
  (value) => virksomhetsnummerPattern.test(value),
  { message: "Virksomhetsnummer må være 9 siffer." }
);

const fnrField = trimmedText("Du må fylle ut fødselsnummer.").refine(
  (value) => fnrPattern.test(value),
  { message: "Fødselsnummer må være 11 siffer." }
);

const timerSchema = z.union([z.number(), z.string()]).superRefine((value, ctx) => {
  if (value === "" || value === null) {
    ctx.addIssue({ code: "custom", message: "Du må anslå hvor mange timer dere trenger." });
    return;
  }

  const numeric = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numeric)) {
    ctx.addIssue({ code: "custom", message: "Antall timer må være et tall." });
    return;
  }

  if (numeric <= 0) {
    ctx.addIssue({ code: "custom", message: "Antall timer må være større enn 0." });
    return;
  }

  if (!Number.isInteger(numeric)) {
    ctx.addIssue({ code: "custom", message: "Antall timer må være hele timer." });
  }
});

const estimertKostnadSchema = z.union([z.number(), z.string()]).superRefine((value, ctx) => {
  if (value === "" || value === null) {
    ctx.addIssue({ code: "custom", message: "Du må anslå kostnad." });
    return;
  }

  const numeric = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numeric)) {
    ctx.addIssue({ code: "custom", message: "Kostnad må være et tall." });
    return;
  }
  if (numeric < MIN_ESTIMERT_KOSTNAD) {
    ctx.addIssue({ code: "custom", message: "Kostnad kan ikke være negativ." });
    return;
  }
  if (numeric > MAX_ESTIMERT_KOSTNAD) {
    ctx.addIssue({ code: "custom", message: `Maksimalt beløp er ${MAX_ESTIMERT_KOSTNAD}.` });
  }
});

const startdatoSchema = z.union([z.string().trim(), z.null()]).superRefine((value, ctx) => {
  if (!value) {
    ctx.addIssue({ code: "custom", message: "Du må velge en dato." });
    return;
  }
  // Basic ISO date pattern check.
  const isoPattern = /^(\d{4})-(\d{2})-(\d{2})$/;
  if (!isoPattern.test(value)) {
    ctx.addIssue({ code: "custom", message: "Ugyldig dato." });
    return;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime()) || value !== date.toISOString().slice(0, 10)) {
    ctx.addIssue({ code: "custom", message: "Ugyldig dato." });
    return;
  }
  if (date < startOfToday()) {
    ctx.addIssue({ code: "custom", message: "Startdato kan ikke være i fortiden." });
  }
});

export const soknadSchema = z.object({
  virksomhet: z.object({
    navn: z.string(),
    virksomhetsnummer: orgnrField,
    kontaktperson: z.object({
      navn: trimmedText("Du må fylle ut navn."),
      epost: emailField,
      telefonnummer: phoneField,
    }),
  }),
  ansatt: z.object({
    fnr: fnrField,
    navn: trimmedText("Du må fylle ut navn."),
  }),
  ekspert: z.object({
    navn: trimmedText("Du må fylle ut navn på ekspert."),
    virksomhet: trimmedText("Du må fylle ut tilknyttet virksomhet."),
    kompetanse: trimmedText("Du må beskrive ekspertens kompetanse."),
  }),
  behovForBistand: z.object({
    begrunnelse: trimmedText("Du må beskrive problemstillingen."),
    behov: trimmedText("Du må beskrive hva dere ønsker hjelp til fra eksperten."),
    timer: timerSchema,
    estimertKostnad: estimertKostnadSchema,
    tilrettelegging: trimmedText("Du må beskrive tiltak for tilrettelegging."),
    startdato: startdatoSchema,
  }),
  nav: z.object({
    kontaktperson: trimmedText("Du må fylle ut hvem i Nav du har drøftet med."),
  }),
});

export type SoknadInputs = z.infer<typeof soknadSchema>;

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
  "nav.kontaktperson",
] as const satisfies ReadonlyArray<Path<SoknadInputs>>;

export const STEP2_FIELDS = [
  "behovForBistand.begrunnelse",
  "behovForBistand.behov",
  "behovForBistand.timer",
  "behovForBistand.estimertKostnad",
  "behovForBistand.tilrettelegging",
  "behovForBistand.startdato",
] as const satisfies ReadonlyArray<Path<SoknadInputs>>;

export const createEmptyInputs = (): SoknadInputs => ({
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
    timer: "",
    estimertKostnad: "",
    tilrettelegging: "",
    startdato: null,
  },
  nav: {
    kontaktperson: "",
  },
});
