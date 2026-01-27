import { z } from "zod";

export const kontaktpersonServerSchema = z.object({
  navn: z.string(),
  epost: z.string(),
  telefonnummer: z.string(),
});

const kontaktpersonDraftServerSchema = kontaktpersonServerSchema.partial();

export const virksomhetServerSchema = z.object({
  virksomhetsnummer: z.string(),
  virksomhetsnavn: z.string(),
  kontaktperson: kontaktpersonServerSchema,
});

const virksomhetDraftServerSchema = z.object({
  virksomhetsnummer: z.string().optional(),
  virksomhetsnavn: z.string().optional(),
  beliggenhetsadresse: z.string().nullable().optional(),
  kontaktperson: kontaktpersonDraftServerSchema.optional(),
});

export const ansattServerSchema = z.object({
  fnr: z.string(),
  navn: z.string(),
});

export const ekspertServerSchema = z.object({
  navn: z.string(),
  virksomhet: z.string(),
  kompetanse: z.string(),
});

export const behovForBistandServerSchema = z.object({
  begrunnelse: z.string(),
  behov: z.string(),
  timer: z.string(),
  estimertKostnad: z.string(),
  tilrettelegging: z.string(),
  startdato: z.string(),
});

export const navServerSchema = z.object({
  kontaktperson: z.string(),
});

export const draftPayloadServerSchema = z.object({
  virksomhet: virksomhetServerSchema,
  ansatt: ansattServerSchema,
  ekspert: ekspertServerSchema,
  behovForBistand: behovForBistandServerSchema,
  nav: navServerSchema,
});

export const draftDtoServerSchema = z.object({
  id: z.string().optional(),
  status: z.enum(["utkast", "innsendt", "godkjent", "avlyst"]).optional(),
  beslutning: z
    .object({
      status: z.string().trim().optional(),
      tidspunkt: z.string().optional(),
    })
    .optional(),
  virksomhet: virksomhetDraftServerSchema.nullable().optional(),
  ansatt: ansattServerSchema.nullable().optional(),
  ekspert: ekspertServerSchema.nullable().optional(),
  behovForBistand: behovForBistandServerSchema.partial().nullable().optional(),
  nav: navServerSchema.nullable().optional(),
  opprettetAv: z.string().nullable().optional(),
  opprettetTidspunkt: z.string().nullable().optional(),
  innsendtTidspunkt: z.string().nullable().optional(),
});
