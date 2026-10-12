import { describe, expect, it } from "vitest";
import { lesLokalBruker, lokaltSubjectToken } from "./lokal-bruker.js";

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
