import { describe, expect, it } from "vitest";
import { createEmptyInputs, soknadSchema } from "./schema";
import { draftDtoServerSchema } from "./server-schemas";

const gyldigeInputs = () => {
  const inputs = createEmptyInputs();
  inputs.virksomhet.virksomhetsnummer = "123456789";
  inputs.virksomhet.virksomhetsnavn = "Virksomhet";
  inputs.virksomhet.kontaktperson.navn = "Kontakt";
  inputs.virksomhet.kontaktperson.epost = "kontakt@virksomhet.no";
  inputs.virksomhet.kontaktperson.telefonnummer = "12345678";
  inputs.ansatt.fnr = "12345678910";
  inputs.ansatt.navn = "Ansatt";
  inputs.ekspert.navn = "Ekspert";
  inputs.ekspert.virksomhet = "Ekspert AS";
  inputs.ekspert.godkjentUtdanningEllerAutorisasjon = ["Psykolog"];
  inputs.ekspert.relevantKompetanse = ["Tilrettelegging på arbeidsplassen"];
  inputs.behovForBistand.begrunnelse = "Begrunnelse";
  inputs.behovForBistand.behov = "Behov";
  inputs.behovForBistand.timer = "10";
  inputs.behovForBistand.estimertKostnad = "5000";
  inputs.behovForBistand.tilrettelegging = "Tiltak";
  inputs.behovForBistand.startdato = "2026-03-03";
  inputs.nav.kontaktperson = "Nav Kontakt";
  return inputs;
};

const feilFor = (navn: string) => {
  const inputs = gyldigeInputs();
  inputs.virksomhet.kontaktperson.navn = navn;
  const result = soknadSchema.safeParse(inputs);
  return result.success
    ? []
    : result.error.issues
        .filter((issue) => issue.path.join(".") === "virksomhet.kontaktperson.navn")
        .map((issue) => issue.message);
};

describe("soknad schema fritekst", () => {
  it("godtar vanlig norsk tekst med linjeskift og tab", () => {
    expect(feilFor("Ærlige Øystein Ås\nlinje to\tmed tab\r\n")).toEqual([]);
  });

  it("avviser < og >", () => {
    expect(feilFor("<b>Kontakt</b>")).toEqual(["Teksten kan ikke inneholde < eller >."]);
  });

  it.each([
    ["kontrolltegn", "Kon\u0007takt"],
    ["zero width space", "Kon\u200Btakt"],
    ["BOM i starten", "\uFEFFKontakt"],
  ])("avviser usynlige tegn: %s", (_, verdi) => {
    expect(feilFor(verdi)).toEqual([
      "Teksten inneholder usynlige tegn. Skriv inn teksten på nytt i stedet for å lime den inn.",
    ]);
  });

  it("avviser tekst lengre enn 10 000 tegn", () => {
    expect(feilFor("a".repeat(10_000))).toEqual([]);
    expect(feilFor("a".repeat(10_001))).toEqual(["Teksten kan ikke være lengre enn 10 000 tegn."]);
  });
});

describe("søknad fra server", () => {
  it.each(["innsendt", "godkjent", "avslatt", "avlyst"])("godtar status %s", (status) => {
    expect(draftDtoServerSchema.safeParse({ status }).success).toBe(true);
  });

  it("avviser ukjent status", () => {
    expect(draftDtoServerSchema.safeParse({ status: "slettet" }).success).toBe(false);
  });
});
