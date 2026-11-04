import { afterEach, describe, expect, it, vi } from "vitest";
import { isFeatureEnabled } from "./featureFlags";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("EKSPERTBISTAND_V2", () => {
  it.each([
    ["localhost", true],
    ["127.0.0.1", true],
    ["arbeidsgiver.intern.dev.nav.no", true],
    ["arbeidsgiver.dev.nav.no", true],
    ["arbeidsgiver.nav.no", false],
    ["preview.example.net", false],
  ])("is %s -> enabled: %s", (hostname, expected) => {
    vi.stubGlobal("window", { location: { hostname } });

    expect(isFeatureEnabled("EKSPERTBISTAND_V2")).toBe(expected);
  });
});
