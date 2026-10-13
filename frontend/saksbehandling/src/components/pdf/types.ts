export type PdfFil = {
  url: string;
  filnavn: string;
  /** Størrelse i bytes */
  storrelse?: number;
};

export const A4_FORHOLD = 297 / 210;
