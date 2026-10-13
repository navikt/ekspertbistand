export type PdfFil = {
  url: string;
  filnavn: string;
  /** Størrelse i bytes */
  storrelse?: number;
  /** Når satt, gjengis PDF-en fra innholdet, og `url` brukes bare til nedlasting. */
  innhold?: Blob;
};

export const A4_FORHOLD = 297 / 210;
