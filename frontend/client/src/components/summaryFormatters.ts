import type { SoknadInputs } from "../features/soknad/schema";
import { parseIsoDate } from "../utils/date";

const numberFormatter = new Intl.NumberFormat("nb-NO");
const shortDateFormatter = new Intl.DateTimeFormat("nb-NO", {
  day: "numeric",
  month: "short",
  year: "numeric",
});

export const formatValue = (value: unknown): string => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value.toString() : "—";
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : "—";
  }
  return "—";
};

export const formatCurrency = (
  value: SoknadInputs["behovForBistand"]["estimertKostnad"]
): string => {
  if (typeof value === "number" && Number.isFinite(value)) {
    return `${numberFormatter.format(value)} kr`;
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      return "—";
    }
    const asNumber = Number.parseFloat(trimmed);
    if (Number.isFinite(asNumber)) {
      return `${numberFormatter.format(asNumber)} kr`;
    }
    return trimmed;
  }
  return formatValue(value);
};

export const formatTimer = (value: SoknadInputs["behovForBistand"]["timer"]): string => {
  if (typeof value === "number" && Number.isFinite(value)) {
    const rounded = Math.trunc(value);
    const unit = rounded === 1 ? "time" : "timer";
    return `${rounded} ${unit}`;
  }

  if (typeof value === "string") {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      return "—";
    }
    const numeric = Number.parseInt(trimmed, 10);
    if (Number.isFinite(numeric)) {
      const unit = numeric === 1 ? "time" : "timer";
      return `${numeric} ${unit}`;
    }
    return trimmed;
  }

  return "—";
};

export const formatDate = (
  value: SoknadInputs["behovForBistand"]["startdato"] | string | null | undefined
): string => {
  const parsed = parseIsoDate(value);
  return parsed ? shortDateFormatter.format(parsed) : "—";
};

export const formatSubmittedDate = (value: string | null | undefined): string => {
  if (!value) return "—";
  const parsedIso = parseIsoDate(value);
  const date = parsedIso ?? new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }
  return shortDateFormatter.format(date);
};
