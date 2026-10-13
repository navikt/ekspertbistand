import { FilePdfIcon } from "@navikt/aksel-icons";
import {
  BodyLong,
  BodyShort,
  Box,
  Button,
  Heading,
  HGrid,
  LocalAlert,
  Loader,
  VStack,
} from "@navikt/ds-react";
import type { RefusjonStatusDto } from "../hooks/useRefusjon";
import { useRefusjonsvedleggNedlasting } from "../hooks/useRefusjonsvedleggNedlasting";
import { formatKroner } from "../utils/belop";
import type { HttpError } from "../utils/http";

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

function formatStorrelse(storrelse: number) {
  return `${(storrelse / 1024).toLocaleString("nb-NO", { maximumFractionDigits: 1 })} kB`;
}

export default function RefusjonskravVisning({
  sakId,
  refusjon,
  error,
  isLoading,
}: RefusjonskravVisningProps) {
  const { lastNed, lastesNedId, nedlastingFeil } = useRefusjonsvedleggNedlasting(sakId);

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
          <Heading level="2" size="xsmall">
            Vedlegg
          </Heading>
          {refusjon.vedlegg.length === 0 ? (
            <BodyShort>Ingen vedlegg.</BodyShort>
          ) : (
            <ul role="list" style={{ margin: 0, padding: 0, listStyle: "none" }}>
              {refusjon.vedlegg.map((vedlegg) => (
                <li key={vedlegg.id}>
                  <Box paddingBlock="space-12" borderWidth="0 0 1 0" borderColor="neutral-subtle">
                    <HGrid columns="minmax(0, 1fr) auto" gap="space-16" align="center">
                      <VStack gap="space-4">
                        <BodyShort>
                          <FilePdfIcon aria-hidden /> {vedlegg.filnavn}
                        </BodyShort>
                        <BodyShort size="small">{formatStorrelse(vedlegg.storrelse)}</BodyShort>
                      </VStack>
                      <Button
                        size="small"
                        variant="secondary"
                        loading={lastesNedId === vedlegg.id}
                        disabled={lastesNedId !== null && lastesNedId !== vedlegg.id}
                        aria-label={`Last ned ${vedlegg.filnavn}`}
                        onClick={() => void lastNed(vedlegg.id)}
                      >
                        Last ned
                      </Button>
                    </HGrid>
                  </Box>
                </li>
              ))}
            </ul>
          )}
          {nedlastingFeil && (
            <LocalAlert status="error">
              <LocalAlert.Header>
                <LocalAlert.Title>Kunne ikke laste ned vedlegget</LocalAlert.Title>
              </LocalAlert.Header>
              <LocalAlert.Content>{nedlastingFeil}</LocalAlert.Content>
            </LocalAlert>
          )}
        </VStack>
      </VStack>
    </Box>
  );
}
