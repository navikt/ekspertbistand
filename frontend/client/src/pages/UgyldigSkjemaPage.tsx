import { useNavigate } from "react-router-dom";
import { Alert, BodyLong, Button, Heading, VStack } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { BackLink } from "../components/BackLink";
import { SOKNADER_PATH } from "../utils/constants";

type UgyldigSkjemaPageProps = {
  message?: string;
};

export default function UgyldigSkjemaPage({ message }: UgyldigSkjemaPageProps) {
  const navigate = useNavigate();

  return (
    <DecoratedPage>
      <VStack as="main" gap="6">
        <BackLink to={SOKNADER_PATH}>Gå til oversikt</BackLink>
        <VStack gap="4">
          <Heading level="1" size="xlarge">
            Søknaden finnes ikke
          </Heading>
          <Alert variant="warning" role="alert">
            <VStack gap="3">
              <BodyLong>
                Lenken du fulgte er ugyldig, eller peker på en søknad som ikke finnes lenger.
              </BodyLong>
              <BodyLong>
                Gå tilbake til oversikten for å finne et gyldig utkast eller starte en ny søknad.
              </BodyLong>
              {message ? (
                <BodyLong size="small" textColor="subtle">
                  Teknisk detalj: {message}
                </BodyLong>
              ) : null}
            </VStack>
          </Alert>
        </VStack>
        <Button type="button" onClick={() => navigate(SOKNADER_PATH)}>
          Gå til søknadsoversikten
        </Button>
      </VStack>
    </DecoratedPage>
  );
}
