/** Speiler `MaksSats` i backend. `gyldigFra` er en ISO-dato (`yyyy-mm-dd`). */
export type MaksSats = {
  gyldigFra: string;
  belop: number;
};

/**
 * Satsen med senest `gyldigFra` som ikke er etter `dato`, eller `undefined` hvis ingen gjelder.
 * Samme regel som `gjeldendeMaksSats` i backend. ISO-datoer kan sammenlignes som tekst.
 */
export function gjeldendeMaksSats(satser: MaksSats[], dato: string): MaksSats | undefined {
  return satser
    .filter((sats) => sats.gyldigFra <= dato)
    .reduce<MaksSats | undefined>(
      (senest, sats) => (!senest || sats.gyldigFra > senest.gyldigFra ? sats : senest),
      undefined
    );
}
