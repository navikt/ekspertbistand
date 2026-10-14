import { describe, expect, it } from "vitest";
import { resolveStatusTag } from "./soknader";

describe("resolveStatusTag", () => {
  it("viser avslått søknad som Avslått", () => {
    expect(resolveStatusTag("avslatt")).toEqual({ label: "Avslått", variant: "error" });
  });

  it("viser avlyst Arena-søknad som Trukket eller avslått", () => {
    expect(resolveStatusTag("avlyst")).toEqual({
      label: "Trukket eller avslått",
      variant: "error",
    });
  });

  it("viser godkjent søknad som Godkjent", () => {
    expect(resolveStatusTag("godkjent")).toEqual({ label: "Godkjent", variant: "success" });
  });
});
