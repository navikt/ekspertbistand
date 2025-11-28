import { BodyLong, Button, Heading, LinkPanel, VStack } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { TILGANGSSTYRING_URL, LOGIN_URL } from "../utils/constants";

export default function LandingPage() {
  return (
    <DecoratedPage>
      <VStack gap="8">
        <VStack gap="4">
          <Heading level="1" size="xlarge">
            Tilskudd til ekspertbistand
          </Heading>
          <VStack gap="3">
            <Heading level="2" size="large">
              Opprett søknad
            </Heading>
            <BodyLong>
              Ekspertbistand dekker hjelp til arbeidsgiver og ansatt fra en nøytral ekspert som har
              kompetanse på sykefravær og arbeidsmiljø.
            </BodyLong>
          </VStack>
        </VStack>

        <Button as="a" href={LOGIN_URL} className="button-align-start">
          Logg inn
        </Button>

        <LinkPanel href={TILGANGSSTYRING_URL} border>
          <Heading level="3" size="small">
            Les om tilgangsstyring
          </Heading>
          <BodyLong size="small">
            For å kunne sende inn søknad må du ha blitt tildelt enkelttrettigheten “Ekspertbistand”
            for den aktuelle virksomheten.
          </BodyLong>
        </LinkPanel>
      </VStack>
    </DecoratedPage>
  );
}
