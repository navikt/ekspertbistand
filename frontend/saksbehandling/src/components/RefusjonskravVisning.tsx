import {
  BodyLong,
  BodyShort,
  Box,
  Heading,
  HGrid,
  LocalAlert,
  Loader,
  VStack,
} from "@navikt/ds-react";
import type { RefusjonStatusDto } from "../hooks/useRefusjon";
import { formatKroner } from "../utils/belop";
import { SAKSBEHANDLING_REFUSJON_VEDLEGG_URL } from "../utils/constants";
import type { HttpError } from "../utils/http";
import { PdfForhandsvisningListe } from "./pdf";

type RefusjonskravVisningProps = {
  sakId: string;
  refusjon: RefusjonStatusDto | null;
  error?: HttpError;
  isLoading: boolean;
};

function feilmelding(error: HttpError) {
  switch (error.status) {
    case 403:
      return "Du har ikke tilgang til å se refusjonskravet for denne saken.";
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
    dateStyle: "long",
    timeStyle: "short",
  }).format(new Date(opprettet));
}

export default function RefusjonskravVisning({
  sakId,
  refusjon,
  error,
  isLoading,
}: RefusjonskravVisningProps) {
  if (isLoading) return <Loader size="medium" title="Laster refusjonskrav" />;
  if (error) {
    return (
      <LocalAlert status="error">
        <LocalAlert.Header>
          <LocalAlert.Title>Kunne ikke hente refusjonskravet</LocalAlert.Title>
        </LocalAlert.Header>
        <LocalAlert.Content>{feilmelding(error)}</LocalAlert.Content>
      </LocalAlert>
    );
  }
  if (!refusjon) return <BodyLong>Arbeidsgiver har ikke sendt inn refusjonskrav ennå.</BodyLong>;

  const vedlegg = refusjon.vedlegg.map((fil) => ({
    url: `${SAKSBEHANDLING_REFUSJON_VEDLEGG_URL(sakId, fil.id)}?visning=forhandsvisning`,
    filnavn: fil.filnavn,
    storrelse: fil.storrelse,
  }));

  return (
    <Box padding="space-16">
      <VStack gap="space-24">
        <Heading level="1" size="small">
          Refusjonskrav
        </Heading>

        <HGrid columns="repeat(auto-fit, minmax(min(100%, 220px), 1fr))" gap="space-16">
          <VStack gap="space-4">
            <BodyShort weight="semibold">Beløp</BodyShort>
            <BodyShort>{formatKroner(refusjon.belopKroner)}</BodyShort>
          </VStack>
          <VStack gap="space-4">
            <BodyShort weight="semibold">Sendt inn</BodyShort>
            <BodyShort>{formatDato(refusjon.opprettet)}</BodyShort>
          </VStack>
        </HGrid>

        <VStack gap="space-8">
          <BodyShort weight="semibold">Oppgitte utgifter</BodyShort>
          <BodyLong style={{ whiteSpace: "pre-wrap" }}>{refusjon.utgifter}</BodyLong>
        </VStack>

        <VStack gap="space-8">
          <Heading level="2" size="xsmall">
            Vedlegg
          </Heading>
          {refusjon.vedlegg.length === 0 ? (
            <BodyShort>Ingen vedlegg.</BodyShort>
          ) : (
            <PdfForhandsvisningListe filer={vedlegg} />
          )}
        </VStack>
      </VStack>
    </Box>
  );
}
