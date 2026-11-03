import { tnr } from "@navikt/fnrvalidator";
import { describe, expect, it } from "vitest";

import {
  createEmptyInputs as createV1EmptyInputs,
  soknadSchema as v1SoknadSchema,
} from "../features/soknad/schema";
import { buildSkjemaPayload as buildV1Payload } from "../features/soknad/payload";
import {
  createEmptyInputs as createV2EmptyInputs,
  soknadSchema as v2SoknadSchema,
} from "./features/soknad/schema";
import { buildSkjemaPayload as buildV2Payload } from "./features/soknad/payload";

const createTestTnr = (): string => {
  for (let individualNumber = 500; individualNumber < 1000; individualNumber += 1) {
    for (let checksum = 0; checksum < 100; checksum += 1) {
      const candidate = `410100${individualNumber}${String(checksum).padStart(2, "0")}`;
      if (tnr(candidate).status === "valid") return candidate;
    }
  }
  throw new Error("Could not generate a valid test T-number.");
};

const TEST_TNR = createTestTnr();

const sampleInputs = <T extends ReturnType<typeof createV1EmptyInputs>>(inputs: T): T => {
  const startdato = new Date();
  startdato.setDate(startdato.getDate() + 7);

  inputs.virksomhet.virksomhetsnummer = "123456789";
  inputs.virksomhet.virksomhetsnavn = "Eksempel Bedrift AS";
  inputs.virksomhet.beliggenhetsadresse = "Testveien 1, 0557 Oslo";
  inputs.virksomhet.kontaktperson.navn = "Lise Kontakt";
  inputs.virksomhet.kontaktperson.epost = "lise@example.no";
  inputs.virksomhet.kontaktperson.telefonnummer = "99112233";
  inputs.ansatt.fnr = TEST_TNR;
  inputs.ansatt.navn = "Testperson";
  inputs.ekspert.navn = "Dr. Ekspert";
  inputs.ekspert.virksomhet = "Ekspert & Co AS";
  inputs.ekspert.virksomhetNavn = "Ekspert & Co AS";
  inputs.ekspert.virksomhetOrgnr = "910825226";
  inputs.ekspert.godkjentUtdanningEllerAutorisasjon = ["Psykolog"];
  inputs.ekspert.relevantKompetanse = ["Tilrettelegging på arbeidsplassen"];
  inputs.behovForBistand.begrunnelse = "Behov for tilrettelegging.";
  inputs.behovForBistand.behov = "Kartlegging av arbeidssituasjonen.";
  inputs.behovForBistand.timer = "20";
  inputs.behovForBistand.estimertKostnad = "20000";
  inputs.behovForBistand.tilrettelegging = "Tilrettelegging av arbeidsoppgaver.";
  inputs.behovForBistand.startdato = startdato.toISOString().slice(0, 10);
  inputs.nav.kontaktperson = "Nav Kontakt";

  return inputs;
};

const validationIssues = (result: ReturnType<typeof v1SoknadSchema.safeParse>) =>
  result.success
    ? []
    : result.error.issues.map(({ code, message, path }) => ({ code, message, path }));

const resolveV2RelativeImport = (file: string, specifier: string) => {
  const parts = ["src", "v2", ...file.replace(/^\.\//, "").split("/").slice(0, -1)];
  for (const segment of specifier.split("/")) {
    if (segment === "..") {
      parts.pop();
    } else if (segment !== ".") {
      parts.push(segment);
    }
  }
  return parts.join("/").replace(/\.(?:ts|tsx|js|jsx)$/, "");
};

const sourcePathFromGlobKey = (file: string) =>
  `src/v2/${file.replace(/^\.\//, "").replace(/\.(?:ts|tsx)$/, "")}`;

describe("v2 application isolation", () => {
  it("keeps representative valid and invalid input behavior equivalent to v1", () => {
    const validV1 = v1SoknadSchema.safeParse(sampleInputs(createV1EmptyInputs()));
    const validV2 = v2SoknadSchema.safeParse(sampleInputs(createV2EmptyInputs()));

    expect(validV1.success).toBe(true);
    expect(validV2.success).toBe(true);
    if (!validV1.success || !validV2.success) return;
    expect(validV2.data).toEqual(validV1.data);

    const invalidV1Inputs = sampleInputs(createV1EmptyInputs());
    const invalidV2Inputs = sampleInputs(createV2EmptyInputs());
    invalidV1Inputs.virksomhet.kontaktperson.navn = "<ugyldig>";
    invalidV2Inputs.virksomhet.kontaktperson.navn = "<ugyldig>";

    const invalidV1 = v1SoknadSchema.safeParse(invalidV1Inputs);
    const invalidV2 = v2SoknadSchema.safeParse(invalidV2Inputs);
    expect(invalidV1.success).toBe(false);
    expect(invalidV2.success).toBe(false);
    expect(validationIssues(invalidV2)).toEqual(validationIssues(invalidV1));
  });

  it("builds the same backend payload from equivalent form input", () => {
    const v1Payload = buildV1Payload("draft-test-id", sampleInputs(createV1EmptyInputs()));
    const v2Payload = buildV2Payload("draft-test-id", sampleInputs(createV2EmptyInputs()));

    expect(v2Payload).toEqual(v1Payload);
    expect(v2Payload).toMatchObject({
      id: "draft-test-id",
      virksomhet: {
        virksomhetsnummer: "123456789",
        kontaktperson: { navn: "Lise Kontakt", epost: "lise@example.no" },
      },
      ekspert: { virksomhetNavn: "Ekspert & Co AS", virksomhetOrgnr: "910825226" },
      behovForBistand: { estimertKostnad: "20000" },
    });
  });

  it("resolves the v2 import graph without crossing into original business modules", () => {
    const v2SourceFiles = import.meta.glob<string>("./**/*.{ts,tsx}", {
      eager: true,
      import: "default",
      query: "?raw",
    });
    const sourceFiles = Object.entries(v2SourceFiles).filter(
      ([file]) => !/\.test\.(?:ts|tsx)$/.test(file)
    );
    const v2Modules = new Set(sourceFiles.map(([file]) => sourcePathFromGlobKey(file)));
    const allowedSharedModules = new Set([
      "src/hooks/useErrorFocus",
      "src/hooks/useOrganisasjoner",
      "src/hooks/useSession",
      "src/hooks/useVirksomhetAdresse",
      "src/components/ApplicationPictogram",
      "src/components/BackLink",
      "src/components/DecoratedPage",
      "src/components/FormErrorSummary",
      "src/components/ScrollToTop",
      "src/utils/api",
      "src/utils/constants",
      "src/utils/date",
      "src/utils/env",
      "src/utils/format",
      "src/utils/http",
    ]);
    const forbiddenImports: string[] = [];
    const unresolvedV2Imports: string[] = [];
    const legacyRoutes: string[] = [];
    const importPattern = /\b(?:from\s*|import\s*\(\s*|import\s*)["']([^"']+)["']/g;

    for (const [file, source] of sourceFiles) {
      for (const match of source.matchAll(/["'`]\/(?:skjema|soknader|tilskuddsbrev)(?=[/"'`])/g)) {
        legacyRoutes.push(`${file} -> ${match[0]}`);
      }
      for (const match of source.matchAll(importPattern)) {
        const specifier = match[1];
        if (!specifier) continue;

        if (!specifier.startsWith(".")) continue;
        const target = resolveV2RelativeImport(file, specifier);
        if (target.startsWith("src/v2/")) {
          if (!v2Modules.has(target)) unresolvedV2Imports.push(`${file} -> ${specifier}`);
        } else if (!allowedSharedModules.has(target)) {
          forbiddenImports.push(`${file} -> ${specifier}`);
        }
      }
    }

    expect(unresolvedV2Imports).toEqual([]);
    expect(forbiddenImports).toEqual([]);
    expect(legacyRoutes).toEqual([]);
  });
});
