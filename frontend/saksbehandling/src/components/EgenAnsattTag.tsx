import { Tag } from "@navikt/ds-react";

/** Merker deltakere som er skjermet (egne ansatte i Nav), slik at opplysningene behandles varsomt. */
export function EgenAnsattTag({ skjermet, size = "xsmall" }: { skjermet: boolean; size?: "xsmall" | "small" }) {
  if (!skjermet) return null;
  return (
    <Tag size={size} variant="outline" data-color="info">
      Egen ansatt
    </Tag>
  );
}
