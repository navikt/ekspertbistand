import { Alert, BodyShort, VStack } from "@navikt/ds-react";

export function SistLagretInfo({
  timestamp,
  saveError,
}: {
  timestamp?: Date | null;
  saveError?: string | null;
}) {
  const formattedTimestamp =
    timestamp?.toLocaleString("nb-NO", { dateStyle: "long", timeStyle: "short" }) ?? "-";

  return (
    <VStack gap="space-8">
      <BodyShort size="small" textColor="subtle">
        Sist lagret: {formattedTimestamp}
      </BodyShort>
      {saveError && (
        <Alert variant="error" size="small" inline>
          Utkastet ble ikke lagret: {saveError}
        </Alert>
      )}
    </VStack>
  );
}
