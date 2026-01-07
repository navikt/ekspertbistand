import { afterEach, describe, expect, it, vi } from "vitest";
import { ensureIsoDateString, formatDateToIso, parseIsoDate } from "./date";

describe("date utils", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("parses exact ISO dates and rejects invalid inputs", () => {
    const parsed = parseIsoDate("2024-05-17");
    expect(parsed).toBeInstanceOf(Date);
    expect(parsed?.toISOString().slice(0, 10)).toBe("2024-05-17");

    expect(parseIsoDate("2024-5-17")).toBeNull();
    expect(parseIsoDate("2024-05-32")).toBeNull();
  });

  it("formats a local date into a stable ISO string", () => {
    const date = new Date(2024, 4, 17);
    expect(formatDateToIso(date)).toBe("2024-05-17");
  });

  it("falls back to today's ISO date when input is invalid", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2024-05-17T10:15:00.000Z"));

    expect(ensureIsoDateString("")).toBe("2024-05-17");
  });
});
