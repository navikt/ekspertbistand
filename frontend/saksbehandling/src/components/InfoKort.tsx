import { BodyShort, Box, Heading, Label, VStack } from "@navikt/ds-react";

export function DataRad({ label, value }: { label: string; value: string }) {
  return (
    <VStack gap="space-2" minWidth="0">
      <Label>{label}</Label>
      <BodyShort style={{ whiteSpace: "pre-line", overflowWrap: "anywhere" }}>{value}</BodyShort>
    </VStack>
  );
}

export function InfoKort({ tittel, children }: { tittel: string; children: React.ReactNode }) {
  return (
    <VStack as="section" gap="space-8" minWidth="0">
      <Heading level="2" size="xsmall">
        {tittel}
      </Heading>
      <Box background="neutral-soft" padding="space-8" borderRadius="4" minWidth="0">
        {children}
      </Box>
    </VStack>
  );
}
