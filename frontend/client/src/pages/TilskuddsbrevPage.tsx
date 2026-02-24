import { useParams } from "react-router-dom";
import { Alert, Box, Heading, Loader, VStack } from "@navikt/ds-react";
import useSWR from "swr";
import DecoratedPage from "../components/DecoratedPage";
import { BackLink } from "../components/BackLink";
import { fetchTilskuddsbrevHtmlForTilsagnNummer } from "../features/tilsagn/tilsagn";
import { SOKNADER_PATH } from "../utils/constants";

export default function TilskuddsbrevPage() {
  const { tilsagnNummer } = useParams<{ tilsagnNummer: string }>();
  const {
    data: tilskuddsbrevHtml,
    error,
    isLoading,
  } = useSWR(tilsagnNummer ? ["tilskuddsbrev-html-tilsagn", tilsagnNummer] : null, ([, id]) =>
    fetchTilskuddsbrevHtmlForTilsagnNummer(id)
  );

  const errorMessage = error
    ? error instanceof Error
      ? error.message
      : "Kunne ikke hente tilskuddsbrevet akkurat nå."
    : null;
  const hasTilskuddsbrev = Boolean(tilskuddsbrevHtml?.tilsagnNummer && tilskuddsbrevHtml?.html);
  const shouldShowApproved = !isLoading && !errorMessage && hasTilskuddsbrev;

  return (
    <DecoratedPage>
      <VStack gap="space-32">
        <BackLink to={SOKNADER_PATH}>Tilbake til oversikt</BackLink>

        {shouldShowApproved && (
          <VStack gap="space-2" align="center">
            <Box background="success-moderate" padding="space-12">
              <Heading level="1" size="medium" align="center">
                Søknaden godkjent
              </Heading>
            </Box>
          </VStack>
        )}

        {isLoading && (
          <Box aria-live="polite">
            <Loader size="large" title="Laster tilskuddsbrev" />
          </Box>
        )}

        {errorMessage && (
          <Alert variant="error" role="alert">
            {errorMessage}
          </Alert>
        )}

        {!isLoading && !errorMessage && hasTilskuddsbrev && tilskuddsbrevHtml && (
          <VStack gap="space-12">
            <Heading level="2" size="small">
              Tilskuddsbrev {tilskuddsbrevHtml.tilsagnNummer}
            </Heading>
            <Box dangerouslySetInnerHTML={{ __html: tilskuddsbrevHtml.html }} />
          </VStack>
        )}

        {!isLoading && !errorMessage && !tilskuddsbrevHtml && (
          <Alert variant="warning" role="alert">
            Fant ikke tilskuddsbrevet akkurat nå. Prøv igjen senere.
          </Alert>
        )}

        <BackLink to={SOKNADER_PATH}>Tilbake til oversikt</BackLink>
      </VStack>
    </DecoratedPage>
  );
}
