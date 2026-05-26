import { Alert, BodyLong, Heading, VStack } from "@navikt/ds-react";

export default function UautorisertPage() {
  return (
    <VStack gap="space-4">
      <Heading level="1" size="large">
        Ingen tilgang
      </Heading>
      <Alert variant="warning">
        Du har ikke tilgang til denne delen av saksbehandlingsløsningen.
      </Alert>
      <BodyLong>Kontakt administrator hvis du mener dette er feil.</BodyLong>
    </VStack>
  );
}
