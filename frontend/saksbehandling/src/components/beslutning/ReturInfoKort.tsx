import { ArrowUndoIcon } from "@navikt/aksel-icons";
import { InfoCard, VStack } from "@navikt/ds-react";
import type { Retur } from "../../hooks/useSak";
import { DataRad } from "../InfoKort";
import { RETUR_ARSAK_TEKST } from "./beslutning";

export default function ReturInfoKort({ retur }: { retur: Retur }) {
  return (
    <InfoCard data-color="info" size="small">
      <InfoCard.Header icon={<ArrowUndoIcon aria-hidden />}>
        <InfoCard.Title as="h2">I retur av beslutter</InfoCard.Title>
      </InfoCard.Header>
      <InfoCard.Content>
        <VStack gap="space-12">
          <DataRad
            label="Årsak for retur"
            value={retur.aarsaker.map((a) => RETUR_ARSAK_TEKST[a]).join(", ")}
          />
          <DataRad label="Forklaring" value={retur.forklaring} />
          <DataRad label="Beslutter" value={retur.returnertAvNavn} />
        </VStack>
      </InfoCard.Content>
    </InfoCard>
  );
}
