import { describe, expect, it } from "vitest";
import { buildSkjemaPayload } from "./payload";
import { createEmptyInputs } from "./schema";

describe("soknad payload", () => {
  it("vasker whitespace fra ansatt fnr", () => {
    const inputs = createEmptyInputs();
    inputs.virksomhet.virksomhetsnummer = "123456789";
    inputs.virksomhet.virksomhetsnavn = "Virksomhet";
    inputs.virksomhet.kontaktperson.navn = "Kontakt";
    inputs.virksomhet.kontaktperson.epost = "kontakt@virksomhet.no";
    inputs.virksomhet.kontaktperson.telefonnummer = "12345678";
    inputs.ansatt.fnr = "\t12345 678910 ";
    inputs.ansatt.navn = "Ansatt";
    inputs.ekspert.navn = "Ekspert";
    inputs.ekspert.virksomhet = "Ekspert AS";
    inputs.ekspert.kompetanse = "Kompetanse";
    inputs.behovForBistand.begrunnelse = "Begrunnelse";
    inputs.behovForBistand.behov = "Behov";
    inputs.behovForBistand.timer = "10";
    inputs.behovForBistand.estimertKostnad = "5000";
    inputs.behovForBistand.tilrettelegging = "Tiltak";
    inputs.behovForBistand.startdato = "2026-03-03";
    inputs.nav.kontaktperson = "Nav Kontakt";

    const payload = buildSkjemaPayload("123", inputs);

    expect(payload.ansatt.fnr).toBe("12345678910");
  });
});
