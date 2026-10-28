import { describe, expect, it } from "vitest";
import { lesLokalBruker, lokalModus, lokaltSubjectToken } from "./lokal-bruker.js";

describe("lesLokalBruker", () => {
  it("returnerer kjent ident fra cookien", () => {
    expect(lesLokalBruker("theme=dark; lokal-bruker=B654321")).toBe("B654321");
  });

  it("ignorerer ukjent ident", () => {
    expect(lesLokalBruker("lokal-bruker=Z999999")).toBeUndefined();
  });

  it("ignorerer verdi som ikke er en ident", () => {
    expect(lesLokalBruker("lokal-bruker=B654321%0D%0AX-Evil%3A1")).toBeUndefined();
  });

  it("returnerer undefined uten cookie", () => {
    expect(lesLokalBruker(undefined)).toBeUndefined();
    expect(lesLokalBruker("theme=dark")).toBeUndefined();
  });
});

describe("lokaltSubjectToken", () => {
  it("bruker tokenet uendret uten valgt bruker", () => {
    expect(lokaltSubjectToken("faketoken", undefined)).toBe("faketoken");
  });

  it("legger valgt ident til tokenet", () => {
    expect(lokaltSubjectToken("faketoken", "B654321")).toBe("faketoken-B654321");
  });
});

describe("lokalModus", () => {
  it("lokal utvikling uten Azure gir session og brukerbytte", () => {
    expect(
      lokalModus({ nodeEnv: "development", azureEnabled: false, lokalBrukerbytteFlagg: false })
    ).toEqual({ localSessionEnabled: true, lokalBrukerbytte: true });
  });

  it("production med Azure gir ingen lokale snarveier", () => {
    expect(
      lokalModus({ nodeEnv: "production", azureEnabled: true, lokalBrukerbytteFlagg: false })
    ).toEqual({ localSessionEnabled: false, lokalBrukerbytte: false });
  });

  it("production med LOKAL_BRUKERBYTTE gir session og brukerbytte", () => {
    expect(
      lokalModus({ nodeEnv: "production", azureEnabled: false, lokalBrukerbytteFlagg: true })
    ).toEqual({ localSessionEnabled: true, lokalBrukerbytte: true });
  });

  it("production uten Azure og uten flagg kaster", () => {
    expect(() =>
      lokalModus({ nodeEnv: "production", azureEnabled: false, lokalBrukerbytteFlagg: false })
    ).toThrow(/AZURE_APP_CLIENT_ID/);
  });

  it("LOKAL_BRUKERBYTTE sammen med Azure kaster", () => {
    expect(() =>
      lokalModus({ nodeEnv: "production", azureEnabled: true, lokalBrukerbytteFlagg: true })
    ).toThrow(/LOKAL_BRUKERBYTTE/);
    expect(() =>
      lokalModus({ nodeEnv: "development", azureEnabled: true, lokalBrukerbytteFlagg: true })
    ).toThrow(/LOKAL_BRUKERBYTTE/);
  });
});
