import { BodyShort, Heading, Link, List, VStack } from "@navikt/ds-react";

export default function TilgangFetchError() {
  return (
    <VStack gap="4">
      <Heading level="1" size="large" spacing>
        Henting av tilganger feilet
      </Heading>
      <BodyShort spacing>
        En teknisk feil på våre servere gjør at siden er utilgjengelig. Dette skyldes ikke noe du
        gjorde.
      </BodyShort>
      <BodyShort>Du kan prøve å</BodyShort>
      <List>
        <List.Item>vente noen minutter og laste siden på nytt</List.Item>
        <List.Item>gå tilbake til forrige side</List.Item>
      </List>
      <BodyShort>
        Hvis problemet vedvarer, kan du{" "}
        <Link href="https://nav.no/kontaktoss" target="_blank" rel="noreferrer">
          kontakte oss (åpnes i ny fane)
        </Link>
        .
      </BodyShort>
    </VStack>
  );
}
