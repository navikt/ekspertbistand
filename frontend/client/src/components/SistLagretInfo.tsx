import { BodyShort } from "@navikt/ds-react";

export function SistLagretInfo({ timestamp }: { timestamp?: Date | null }) {
  const formattedTimestamp =
    timestamp?.toLocaleString("nb-NO", { dateStyle: "long", timeStyle: "short" }) ?? "-";

  return (
    <BodyShort size="small" textColor="subtle">
      Sist lagret: {formattedTimestamp}
    </BodyShort>
  );
}
