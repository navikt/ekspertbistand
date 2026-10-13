import { BodyShort, Button, Heading, VStack } from "@navikt/ds-react";

export default function Sluttrapportkontroll() {
  return (
    <VStack gap="space-16" padding="space-16" paddingBlock="space-32">
      <Heading level="2" size="xsmall">
        Kontroll av sluttrapport
      </Heading>
      <Button type="button">Registrer som mottatt</Button>
      <BodyShort size="small">Handlingen er ikke tilgjengelig ennå.</BodyShort>
    </VStack>
  );
}
