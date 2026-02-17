import { BodyLong, BodyShort, Button, Heading, LinkPanel, Loader, VStack } from "@navikt/ds-react";
import { Navigate } from "react-router-dom";
import DecoratedPage from "../components/DecoratedPage";
import { TILGANGSSTYRING_URL, LOGIN_URL, SOKNADER_PATH } from "../utils/constants";
import { useSession } from "../hooks/useSession";

export default function LandingPage() {
  const { authenticated, isLoading } = useSession();

  if (authenticated) {
    return <Navigate to={SOKNADER_PATH} replace />;
  }

  if (isLoading) {
    return (
      <DecoratedPage>
        <VStack align="center" gap="space-4" padding="space-32">
          <Loader size="large" title="Sjekker innlogging" />
          <BodyShort>Sjekker innlogging …</BodyShort>
        </VStack>
      </DecoratedPage>
    );
  }

  return (
    <DecoratedPage>
      <VStack gap="space-8" align="start">
        <VStack gap="space-4">
          <Heading level="1" size="xlarge">
            Tilskudd til ekspertbistand
          </Heading>
          <VStack gap="space-4">
            <Heading level="2" size="large">
              Opprett søknad
            </Heading>
            <BodyLong>
              Ekspertbistand dekker hjelp til arbeidsgiver og ansatt fra en nøytral ekspert som har
              kompetanse på sykefravær og arbeidsmiljø.
            </BodyLong>
          </VStack>
        </VStack>

        <Button as="a" href={LOGIN_URL}>
          Logg inn
        </Button>

        <LinkPanel href={TILGANGSSTYRING_URL} border>
          <Heading level="3" size="small">
            Les om tilgangsstyring
          </Heading>
          <BodyLong size="small">
            For å kunne sende inn søknad må du ha blitt tildelt enkeltrettigheten "Tilskudd til
            ekspertbistand" for den aktuelle virksomheten.
          </BodyLong>
        </LinkPanel>
      </VStack>
    </DecoratedPage>
  );
}
