const dateFormatter = new Intl.DateTimeFormat("nb-NO", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});
const timeFormatter = new Intl.DateTimeFormat("nb-NO", {
  hour: "2-digit",
  minute: "2-digit",
});

export const formatDateTime = (value: string): string | null => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  const datePart = dateFormatter.format(date);
  const formattedTime = timeFormatter.format(date).replace(":", ".").trim();
  return formattedTime ? `${datePart} kl. ${formattedTime}` : datePart;
};

export const startOfToday = (): Date => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
};

export const formatDateToIso = (date: Date): string => {
  const utc = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  return utc.toISOString().slice(0, 10);
};

const isoFromDate = (date: Date): string => date.toISOString().slice(0, 10);

const parseExactIsoDate = (value?: string | null): Date | null => {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (trimmed.length !== 10) return null;
  const parsed = new Date(trimmed);
  if (Number.isNaN(parsed.getTime())) return null;
  return isoFromDate(parsed) === trimmed ? parsed : null;
};

export const ensureIsoDateString = (value?: string | null): string => {
  const parsed = parseExactIsoDate(value);
  return parsed ? isoFromDate(parsed) : formatDateToIso(startOfToday());
};

export const parseIsoDate = (value?: string | null): Date | null => parseExactIsoDate(value);
