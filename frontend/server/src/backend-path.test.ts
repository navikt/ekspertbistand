import { describe, expect, it } from "vitest";
import { isBackendPathAllowed } from "./backend-path.js";

describe("applicant backend namespace authorization", () => {
  for (const namespace of ["soknad", "tilsagndata", "organisasjoner", "ereg"]) {
    for (const suffix of ["", "/", "/v1", "/v1/example/child", "?next=/api/saksbehandling"]) {
      const requestTarget = `/api/${namespace}${suffix}`;

      it(`allows ${requestTarget}`, () => {
        expect(isBackendPathAllowed(requestTarget)).toBe(true);
      });
    }
  }

  for (const requestTarget of [
    "/api/saksbehandling",
    "/api/saksbehandling/v1/meg",
    "/api/soknad-other/v1",
    "/api/unknown",
    "/api/soknad/../saksbehandling",
    "/api/soknad/%2e%2e/saksbehandling",
    "/api/soknad/%252e%252e/saksbehandling",
    "/api/soknad%2fsomething",
    "/api/soknad/%2f..%2fsaksbehandling",
    "/api/soknad/%5c..%5csaksbehandling",
    "/api/soknad\\..\\saksbehandling",
    "/api/soknad/%",
    "/api/soknad/%00",
    "/api/soknad;x=y",
    "/api/soknad/..;/saksbehandling",
    "/api/soknad/./v1",
    "/api/soknad#fragment",
    "",
    "//api/soknad",
    "https://example.invalid/api/soknad",
  ]) {
    it(`rejects ${requestTarget}`, () => {
      expect(isBackendPathAllowed(requestTarget)).toBe(false);
    });
  }
});
