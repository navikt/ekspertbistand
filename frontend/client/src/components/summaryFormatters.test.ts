import { describe, expect, it } from "vitest";
import {
  formatCurrency,
  formatDate,
  formatSubmittedDate,
  formatTimer,
  formatValue,
} from "./summaryFormatters";

describe("summary formatters", () => {
  it("formats generic values with sensible fallbacks", () => {
    expect(formatValue(12)).toBe("12");
    expect(formatValue(Number.NaN)).toBe("—");
    expect(formatValue("   ")).toBe("—");
  });

  it("formats currency values and preserves non-numeric strings", () => {
    expect(formatCurrency("1200")).toMatch(/1\s?200 kr/);
    expect(formatCurrency("  ")).toBe("—");
    expect(formatCurrency("abc")).toBe("abc");
  });

  it("formats timer values with the right unit", () => {
    expect(formatTimer("2")).toBe("2 timer");
    expect(formatTimer("1")).toBe("1 time");
    expect(formatTimer("  ")).toBe("—");
  });

  it("returns a placeholder for invalid dates", () => {
    expect(formatDate("not-a-date")).toBe("—");
    expect(formatSubmittedDate("not-a-date")).toBe("—");
  });
});
