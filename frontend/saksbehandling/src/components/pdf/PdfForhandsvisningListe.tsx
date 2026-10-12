import { BodyShort, HStack } from "@navikt/ds-react";
import { PdfForhandsvisning } from "./PdfForhandsvisning";
import type { PdfFil } from "./types";

type Props = {
  filer: PdfFil[];
  bredde?: number;
};

export function PdfForhandsvisningListe({ filer, bredde }: Props) {
  if (filer.length === 0) {
    return <BodyShort>–</BodyShort>;
  }

  return (
    <HStack as="ul" gap="space-24" wrap style={{ listStyle: "none", margin: 0, padding: 0 }}>
      {filer.map((fil) => (
        <li key={fil.url}>
          <PdfForhandsvisning fil={fil} bredde={bredde} />
        </li>
      ))}
    </HStack>
  );
}
