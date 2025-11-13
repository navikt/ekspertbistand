import { type JSX } from "react";
import { Link as RouterLink } from "react-router-dom";
import { Alert, BodyShort, Box, Heading, Loader, Tag, VStack } from "@navikt/ds-react";
import { LinkCard } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { ApplicationPictogram } from "../components/ApplicationPictogram";
import { MIN_SIDE_URL } from "../utils/constants";
import { BackLink } from "../components/BackLink";
import { useSoknader } from "../hooks/useSoknader.ts";

export default function SoknaderPage() {
  const { soknader, error, loading } = useSoknader();

  let content: JSX.Element | null;
  if (loading) {
    content = (
      <Box className="home-page__loader" aria-live="polite">
        <Loader size="large" title="Laster søknader" />
      </Box>
    );
  } else if (error) {
    content = (
      <Alert variant="error" className="home-page__alert">
        {error}
      </Alert>
    );
  } else if (soknader.length === 0) {
    content = <BodyShort>Du har ingen søknader ennå.</BodyShort>;
  } else {
    content = (
      <VStack as="ul" gap="3" className="home-page__application-list">
        {soknader.map((application) => (
          <li key={application.id}>
            <LinkCard className="home-page__application-card">
              <LinkCard.Title as="h3">
                <LinkCard.Anchor asChild>
                  <RouterLink to={application.href}>{application.title}</RouterLink>
                </LinkCard.Anchor>
              </LinkCard.Title>
              <LinkCard.Description>
                <div className="home-page__application-meta">
                  <BodyShort>{application.description}</BodyShort>
                  <Tag variant={application.tag.variant}>{application.tag.label}</Tag>
                </div>
              </LinkCard.Description>
            </LinkCard>
          </li>
        ))}
      </VStack>
    );
  }

  return (
    <DecoratedPage>
      <main className="home-page">
        <BackLink href={MIN_SIDE_URL}>Tilbake til Min side - arbeidsgiver</BackLink>

        <LinkCard className="home-page__create-card" size="medium" arrowPosition="center">
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

        <Heading level="1" size="xlarge" spacing>
          Søknader
        </Heading>

        {content}
      </main>
    </DecoratedPage>
  );
}
