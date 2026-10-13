import { BodyLong, BodyShort, Box, Heading, LocalAlert, Loader, VStack } from "@navikt/ds-react";
import type { SluttrapportStatusDto } from "../hooks/useSluttrapport";
import { SAKSBEHANDLING_SLUTTRAPPORT_FIL_URL } from "../utils/constants";
import type { HttpError } from "../utils/http";
import { PdfForhandsvisningListe } from "./pdf";

type Props = {
  sakId: string;
  sluttrapport: SluttrapportStatusDto | null;
  tiltaksperiode: string | null;
  error?: HttpError;
  isLoading: boolean;
};

function feilmelding(error: HttpError) {
  switch (error.status) {
    case 403:
      return "Du har ikke tilgang til å se sluttrapporten for denne saken.";
    case 404:
      return "Vi fant ikke saken. Last inn siden på nytt og prøv igjen.";
    case 503:
      return "Tjenesten er midlertidig utilgjengelig. Prøv igjen om litt.";
    default:
      return "Noe gikk galt. Prøv igjen senere.";
  }
}

function formatDato(opprettet: string) {
  return new Intl.DateTimeFormat("nb-NO", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(opprettet));
}

export default function SluttrapportVisning({
  sakId,
  sluttrapport,
  tiltaksperiode,
  error,
  isLoading,
}: Props) {
  if (isLoading) return <Loader size="medium" title="Laster sluttrapport" />;
  if (error) {
    return (
      <LocalAlert status="error">
        <LocalAlert.Header>
          <LocalAlert.Title>Kunne ikke hente sluttrapporten</LocalAlert.Title>
        </LocalAlert.Header>
        <LocalAlert.Content>{feilmelding(error)}</LocalAlert.Content>
      </LocalAlert>
    );
  }
  if (!sluttrapport) {
    return <BodyLong>Arbeidsgiver har ikke sendt inn sluttrapport ennå.</BodyLong>;
  }

  const fil = {
    url: `${SAKSBEHANDLING_SLUTTRAPPORT_FIL_URL(sakId)}?visning=forhandsvisning`,
    filnavn: sluttrapport.filnavn,
    storrelse: sluttrapport.storrelse,
  };

  return (
    <Box padding="space-16">
      <VStack gap="space-24">
        <Heading level="1" size="small">
          Sluttrapport
        </Heading>

        {tiltaksperiode && (
          <VStack gap="space-4">
            <BodyShort weight="semibold">Tiltaksperiode</BodyShort>
            <BodyShort>{tiltaksperiode}</BodyShort>
          </VStack>
        )}

        <VStack gap="space-4">
          <BodyShort weight="semibold">Sendt inn til Nav</BodyShort>
          <BodyShort>{formatDato(sluttrapport.opprettet)}</BodyShort>
        </VStack>

        <VStack gap="space-8">
          <Heading level="2" size="xsmall">
            Vedlegg
          </Heading>
          <PdfForhandsvisningListe filer={[fil]} />
        </VStack>
      </VStack>
    </Box>
  );
}
