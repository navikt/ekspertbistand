import { describe, expect, it } from "vitest";
import { buildSkjemaPayload } from "./payload";
import { createEmptyInputs } from "./schema";

describe("soknad payload", () => {
  it("vasker alle ikke-digit tegn fra ansatt fnr", () => {
    const inputs = createEmptyInputs();
    inputs.virksomhet.virksomhetsnummer = "123456789";
    inputs.virksomhet.virksomhetsnavn = "Virksomhet";
    inputs.virksomhet.kontaktperson.navn = "Kontakt";
    inputs.virksomhet.kontaktperson.epost = "kontakt@virksomhet.no";
    inputs.virksomhet.kontaktperson.telefonnummer = "12345678";
    inputs.ansatt.fnr = "12 345-6789a10";
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

    const payload = buildSkjemaPayload("123", inputs);

    expect(payload.ansatt.fnr).toBe("12345678910");
  });

  it("tar med valgt ekspert-virksomhet navn og orgnr i payload", () => {
    const inputs = createEmptyInputs();
    inputs.virksomhet.virksomhetsnummer = "123456789";
    inputs.virksomhet.virksomhetsnavn = "Virksomhet";
    inputs.virksomhet.kontaktperson.navn = "Kontakt";
    inputs.virksomhet.kontaktperson.epost = "kontakt@virksomhet.no";
    inputs.virksomhet.kontaktperson.telefonnummer = "12345678";
    inputs.ansatt.fnr = "12345678910";
    inputs.ansatt.navn = "Ansatt";
    inputs.ekspert.navn = "Ekspert";
    inputs.ekspert.virksomhet = "Ekspert & Co AS";
    inputs.ekspert.virksomhetNavn = "Ekspert & Co AS";
    inputs.ekspert.virksomhetOrgnr = "910825226";
    inputs.ekspert.godkjentUtdanningEllerAutorisasjon = ["Psykolog"];
    inputs.ekspert.relevantKompetanse = ["Tilrettelegging på arbeidsplassen"];
    inputs.behovForBistand.begrunnelse = "Begrunnelse";
    inputs.behovForBistand.behov = "Behov";
    inputs.behovForBistand.timer = "10";
    inputs.behovForBistand.estimertKostnad = "5000";
    inputs.behovForBistand.tilrettelegging = "Tiltak";
    inputs.behovForBistand.startdato = "2026-03-03";
    inputs.nav.kontaktperson = "Nav Kontakt";

    const payload = buildSkjemaPayload("123", inputs);

    expect(payload.ekspert.virksomhet).toBe("Ekspert & Co AS");
    expect(payload.ekspert.virksomhetNavn).toBe("Ekspert & Co AS");
    expect(payload.ekspert.virksomhetOrgnr).toBe("910825226");
  });
});
