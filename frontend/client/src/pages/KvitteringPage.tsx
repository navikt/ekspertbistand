import { useEffect, useMemo, useState } from "react";
import { useLocation, useParams } from "react-router-dom";
import { Alert, BodyLong, BodyShort, Box, Heading, Loader, VStack } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { SoknadSummary } from "../components/SoknadSummary";
import { draftDtoToInputs, type DraftDto } from "../utils/soknadPayload";
import { APPLICATIONS_PATH, EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { parseErrorMessage } from "../utils/http";
import { formatDateTime } from "../utils/date";
import type { Inputs } from "./types";
import { BackLink } from "../components/BackLink";

type LocationState = {
  submissionSuccess?: boolean;
};

type KvitteringMetadata = {
  status?: string | null;
  innsendtTidspunkt?: string | null;
};

export default function KvitteringPage() {
  const { id } = useParams<{ id: string }>();
  const location = useLocation<LocationState>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<Inputs | null>(null);
  const [metadata, setMetadata] = useState<KvitteringMetadata>({});

  useEffect(() => {
    if (!id) return;

    const controller = new AbortController();
    setLoading(true);
    setError(null);

    (async () => {
      try {
        const response = await fetch(`${EKSPERTBISTAND_API_PATH}/${id}`, {
          headers: { Accept: "application/json" },
          signal: controller.signal,
        });

        if (!response.ok) {
          const message = await parseErrorMessage(response);
          throw new Error(message ?? `Kunne ikke hente søknaden (${response.status}).`);
        }

        const payload = (await response.json()) as DraftDto;
        setData(draftDtoToInputs(payload));
        setMetadata({
          status: payload?.status ?? null,
          innsendtTidspunkt: payload?.innsendtTidspunkt ?? payload?.opprettetTidspunkt ?? null,
        });
      } catch (err) {
        if (controller.signal.aborted) return;
        const message =
          err instanceof Error ? err.message : "Kunne ikke hente søknaden akkurat nå.";
        setError(message);
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    })();

    return () => controller.abort();
  }, [id]);

  const innsendtTekst = useMemo(() => {
    if (!metadata.innsendtTidspunkt) return null;
    return formatDateTime(metadata.innsendtTidspunkt);
  }, [metadata.innsendtTidspunkt]);

  return (
    <DecoratedPage blockProps={{ width: "lg", gutters: true }}>
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

        {location.state?.submissionSuccess && (
          <Alert variant="success" role="status">
            Søknaden er sendt inn.
          </Alert>
        )}

        {loading && (
          <Box aria-live="polite">
            <Loader size="large" title="Laster kvittering" />
          </Box>
        )}

        {error && (
          <Alert variant="error" role="alert">
            {error}
          </Alert>
        )}

        {!loading && !error && data && (
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
            <SoknadSummary data={data} />
          </VStack>
        )}

        <BackLink to={APPLICATIONS_PATH}>Gå til oversikt</BackLink>
      </VStack>
    </DecoratedPage>
  );
}
