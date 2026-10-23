import { Tag } from "@navikt/ds-react";
import type { Adressebeskyttelse } from "../hooks/useSaker";

const tagPerGradering = {
  FORTROLIG: { tekst: "Fortrolig adresse", farge: "warning" },
  STRENGT_FORTROLIG: { tekst: "Strengt fortrolig adresse", farge: "danger" },
} as const;

/** Merker deltakere med adressebeskyttelse, slik at opplysningene behandles varsomt (etterlevelseskrav K255.1). */
export function AdressebeskyttelseTag({
  adressebeskyttelse,
  size = "xsmall",
}: {
  adressebeskyttelse: Adressebeskyttelse | null;
  size?: "xsmall" | "small";
}) {
  if (!adressebeskyttelse) return null;
  const { tekst, farge } = tagPerGradering[adressebeskyttelse];
  return (
    <Tag size={size} variant="strong" data-color={farge}>
      {tekst}
    </Tag>
  );
}
