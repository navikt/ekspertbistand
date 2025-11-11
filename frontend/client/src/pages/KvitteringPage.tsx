import { useMemo } from "react";
import { useLocation, useParams } from "react-router-dom";
import { Alert, BodyLong, BodyShort, Box, Heading, Loader, VStack } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { SoknadSummary } from "../components/SoknadSummary";
import { draftDtoToInputs, type DraftDto } from "../utils/soknadPayload";
import { APPLICATIONS_PATH, EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { formatDateTime } from "../utils/date";
import { BackLink } from "../components/BackLink";
import useSWR from "swr";

type LocationState = {
  submissionSuccess?: boolean;
};

type KvitteringMetadata = {
  status?: string | null;
  innsendtTidspunkt?: string | null;
};

export default function KvitteringPage() {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const locationState = (location.state as LocationState | null) ?? null;
  const {
    data: draft,
    error,
    isLoading,
  } = useSWR<DraftDto | null>(id ? `${EKSPERTBISTAND_API_PATH}/${id}` : null);

  const formData = useMemo(() => (draft ? draftDtoToInputs(draft) : null), [draft]);
  const metadata = useMemo<KvitteringMetadata>(() => {
    if (!draft) return {};
    return {
      status: draft?.status ?? null,
      innsendtTidspunkt: draft?.innsendtTidspunkt ?? draft?.opprettetTidspunkt ?? null,
    };
  }, [draft]);

  const errorMessage = error
    ? error instanceof Error
      ? error.message
      : "Kunne ikke hente søknaden akkurat nå."
    : null;

  const innsendtTekst = useMemo(() => {
    if (!metadata.innsendtTidspunkt) return null;
    return formatDateTime(metadata.innsendtTidspunkt);
  }, [metadata.innsendtTidspunkt]);

  return (
    <DecoratedPage>
      <VStack gap="8">
        <VStack gap="3">
          <BackLink to={APPLICATIONS_PATH}>Gå til oversikt</BackLink>

          <Heading level="1" size="xlarge">
            Kvittering for søknad om ekspertbistand
          </Heading>
          <BodyLong>
            Vi har mottatt søknaden. Under finner du oppsummering av informasjonen som ble sendt
            inn.
          </BodyLong>
        </VStack>

        {locationState?.submissionSuccess && (
          <Alert variant="success" role="status">
            Søknaden er sendt inn.
          </Alert>
        )}

        {isLoading && (
          <Box aria-live="polite">
            <Loader size="large" title="Laster kvittering" />
          </Box>
        )}

        {errorMessage && (
          <Alert variant="error" role="alert">
            {errorMessage}
          </Alert>
        )}

        {!isLoading && !errorMessage && formData && (
          <VStack gap="6">
            <Box>
              <BodyShort size="small" textColor="subtle">
                Saksnummer: {id}
              </BodyShort>
              {innsendtTekst && (
                <BodyShort size="small" textColor="subtle">
                  Innsendt: {innsendtTekst}
                </BodyShort>
              )}
            </Box>
            <SoknadSummary data={formData} />
          </VStack>
        )}

        <BackLink to={APPLICATIONS_PATH}>Gå til oversikt</BackLink>
      </VStack>
    </DecoratedPage>
  );
}
