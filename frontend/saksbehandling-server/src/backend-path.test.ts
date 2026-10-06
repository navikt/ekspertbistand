import { describe, expect, it } from "vitest";
import { isApiRequest, isBackendPathAllowed } from "./backend-path.js";

describe("caseworker backend namespace authorization", () => {
  for (const requestTarget of [
    "/api/saksbehandling",
    "/api/saksbehandling/",
    "/api/saksbehandling/v1/meg",
    "/api/saksbehandling/v1/saker/example/vilkar/child",
    "/api/saksbehandling?next=/api/soknad",
  ]) {
    it(`allows ${requestTarget}`, () => {
      expect(isBackendPathAllowed(requestTarget)).toBe(true);
    });
  }

  for (const requestTarget of [
    "/api/soknad",
    "/api/tilsagndata",
    "/api/organisasjoner",
    "/api/ereg",
    "/api/saksbehandling-other/v1",
    "/api/unknown",
    "/api/saksbehandling/../soknad",
    "/api/saksbehandling/%2e%2e/soknad",
    "/api/saksbehandling/%252e%252e/soknad",
    "/api/saksbehandling%2fsomething",
    "/api/saksbehandling/%2f..%2fsoknad",
    "/api/saksbehandling/%5c..%5csoknad",
    "/api/saksbehandling\\..\\soknad",
    "/api/saksbehandling/%",
    "/api/saksbehandling/%00",
    "/api/saksbehandling;x=y",
    "/api/saksbehandling/..;/soknad",
    "/api/saksbehandling/./v1",
    "/api/saksbehandling#fragment",
    "",
    "//api/saksbehandling",
    "https://example.invalid/api/saksbehandling",
  ]) {
    it(`rejects ${requestTarget}`, () => {
      expect(isBackendPathAllowed(requestTarget)).toBe(false);
    });
  }

  for (const path of ["/api", "/api/", "/api/soknad", "/api/saksbehandling/v1"]) {
    it(`treats ${path} as an API request`, () => {
      expect(isApiRequest(path)).toBe(true);
    });
  }

  for (const path of ["/", "/apiet", "/saker/123", "/assets/index.js", "/internal/isAlive"]) {
    it(`does not treat ${path} as an API request`, () => {
      expect(isApiRequest(path)).toBe(false);
    });
  }
});
