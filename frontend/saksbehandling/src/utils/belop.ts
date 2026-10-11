/** Søknadssummen som positivt heltall, eller `null` hvis den ikke kan tolkes slik (som i backend). */
export function tolkSoknadssum(estimertKostnad: string): number | null {
  const tekst = estimertKostnad.trim();
  if (!/^\d+$/.test(tekst)) return null;
  const belop = Number(tekst);
  return Number.isSafeInteger(belop) && belop > 0 ? belop : null;
}

export function formatKroner(belop: number) {
  return `${formatTusenskille(belop)} kr`;
}

export function formatTusenskille(belop: number) {
  return belop.toLocaleString("nb-NO");
}
