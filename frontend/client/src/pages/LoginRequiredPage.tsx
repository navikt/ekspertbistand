import { Alert, BodyLong, Button, Heading, VStack } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { LOGIN_URL } from "../utils/constants";

export default function LoginRequiredPage() {
  return (
    <DecoratedPage>
      <VStack as="main" gap="6">
        <Heading level="1" size="xlarge">
          Du må logge inn
        </Heading>
        <Alert variant="warning">
          <BodyLong>
            For å se søknadene dine må du logge inn med Altinn-rettigheten «Ekspertbistand».
          </BodyLong>
        </Alert>
        <Button as="a" href={LOGIN_URL} variant="primary">
          Logg inn
        </Button>
      </VStack>
    </DecoratedPage>
  );
}
