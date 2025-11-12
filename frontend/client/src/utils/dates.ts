export const startOfToday = (): Date => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
};

export const formatDateToIso = (date: Date): string => {
  const utc = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  return utc.toISOString().slice(0, 10);
};

export const parseIsoDate = (value?: string | null): Date | undefined => {
  if (!value) return undefined;
  const parts = value.split("-");
  if (parts.length !== 3) return undefined;
  const [year, month, day] = parts.map((part) => Number.parseInt(part, 10));
  if ([year, month, day].some((num) => Number.isNaN(num))) return undefined;
  const parsed = new Date(year, month - 1, day);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed;
};

export const ensureIsoDateString = (value?: string | null): string => {
  const parsed = parseIsoDate(value);
  return parsed ? formatDateToIso(parsed) : formatDateToIso(startOfToday());
};
