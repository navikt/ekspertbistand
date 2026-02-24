import { Alert, BodyLong, Button, Heading, VStack } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { LOGIN_URL } from "../utils/constants";

export default function LoginRequiredPage() {
  return (
    <DecoratedPage>
      <VStack gap="space-32">
        <Heading level="1" size="xlarge">
          Du må logge inn
        </Heading>
        <Alert variant="warning">
          <BodyLong>
            For å kunne sende inn søknad må du ha blitt tildelt enkeltrettigheten "Tilskudd til
            ekspertbistand" for den aktuelle virksomheten.
          </BodyLong>
        </Alert>
        <Button as="a" href={LOGIN_URL} variant="primary">
          Logg inn
        </Button>
      </VStack>
    </DecoratedPage>
  );
}
