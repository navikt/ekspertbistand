import { type JSX, useEffect, useState } from "react";
import { Link as RouterLink, useLocation, useNavigate } from "react-router-dom";
import { Alert, BodyShort, Box, Button, Heading, Loader, Tag, VStack } from "@navikt/ds-react";
import { LinkCard } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { ApplicationPictogram } from "../components/ApplicationPictogram";
import { LOGIN_URL, MIN_SIDE_URL } from "../utils/constants";
import { BackLink } from "../components/BackLink";
import { useSoknader } from "../hooks/useSoknader.ts";

export default function SoknaderPage() {
  const { soknader, error, loading, requiresLogin } = useSoknader();
  const location = useLocation();
  const navigate = useNavigate();
  const savedDraftFromState =
    typeof location.state === "object" &&
    location.state !== null &&
    "savedDraft" in location.state &&
    Boolean((location.state as { savedDraft?: boolean }).savedDraft);
  const [showSavedAlert, setShowSavedAlert] = useState(savedDraftFromState);

  useEffect(() => {
    if (!savedDraftFromState) return;
    navigate(".", { replace: true, state: {} });
  }, [navigate, savedDraftFromState]);
  let content: JSX.Element | null;
  if (loading) {
    content = (
      <Box aria-live="polite">
        <Loader size="large" title="Laster søknader" />
      </Box>
    );
  } else if (error) {
    content = (
      <Alert variant="error">
        <VStack gap="space-24">
          <BodyShort>{error}</BodyShort>
          {requiresLogin ? (
            <Button as="a" href={LOGIN_URL} variant="primary">
              Logg inn
            </Button>
          ) : null}
        </VStack>
      </Alert>
    );
  } else if (soknader.length === 0) {
    content = <BodyShort>Du har ingen søknader ennå.</BodyShort>;
  } else {
    content = (
      <VStack
        as="ul"
        gap="space-16"
        padding="space-0"
        margin="space-0"
        style={{ listStyle: "none" }}
      >
        {soknader.map((application) => (
          <li key={application.id}>
            <LinkCard>
              <LinkCard.Title as="h3">
                <LinkCard.Anchor asChild>
                  <RouterLink to={application.href}>{application.title}</RouterLink>
                </LinkCard.Anchor>
              </LinkCard.Title>
              <LinkCard.Description>
                <VStack gap="space-4" align="start">
                  <BodyShort>{application.description}</BodyShort>
                  <Tag variant={application.tag.variant}>{application.tag.label}</Tag>
                </VStack>
              </LinkCard.Description>
            </LinkCard>
          </li>
        ))}
      </VStack>
    );
  }

  return (
    <DecoratedPage>
      <VStack gap="space-32">
        <Heading level="1" size="xlarge" spacing>
          Tilskudd til ekspertbistand
        </Heading>

        <BackLink href={MIN_SIDE_URL}>Tilbake til Min side - arbeidsgiver</BackLink>

        <VStack gap="space-12">
          <LinkCard size="medium" arrowPosition="center">
            <LinkCard.Icon aria-hidden>
              <ApplicationPictogram aria-hidden width={48} />
            </LinkCard.Icon>
            <LinkCard.Title as="h2">
              <LinkCard.Anchor asChild>
                <RouterLink to="/skjema/start">
                  Opprett søknad om tilskudd til ekspertbistand
                </RouterLink>
              </LinkCard.Anchor>
            </LinkCard.Title>
          </LinkCard>

          <Heading level="2" size="large">
            Søknader
          </Heading>

          {showSavedAlert ? (
            <>
              <BodyShort size="small" textColor="subtle">
                Utkast lagres i 48 timer innen vi sletter dem.
              </BodyShort>
              <Alert
                variant="success"
                role="status"
                closeButton
                onClose={() => setShowSavedAlert(false)}
              >
                Utkast lagret
              </Alert>
            </>
          ) : null}
          {content}
        </VStack>
      </VStack>
    </DecoratedPage>
  );
}
