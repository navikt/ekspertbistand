import { BodyShort, Box, Heading, Label, VStack } from "@navikt/ds-react";

export function DataRad({ label, value }: { label: string; value: string }) {
  return (
    <VStack gap="space-2">
      <Label size="small">{label}</Label>
      <BodyShort size="small">{value}</BodyShort>
    </VStack>
  );
}

export function InfoKort({ tittel, children }: { tittel: string; children: React.ReactNode }) {
  return (
    <Box background="soft" padding="space-16" borderRadius="8">
      <VStack gap="space-16">
        <Heading level="2" size="small">
          {tittel}
        </Heading>
        {children}
      </VStack>
    </Box>
  );
}
