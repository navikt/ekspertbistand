/** Beriker fritekstfeltet med navn + organisasjonsnummer, f.eks. «Ekspert & Co AS (910825226)». */
export const formaterVirksomhet = (
  navn: string | null | undefined,
  orgnr: string | null | undefined
) => {
  if (navn == null || orgnr == null) {
    return "";
  }
  return `${navn} (${orgnr})`;
};
