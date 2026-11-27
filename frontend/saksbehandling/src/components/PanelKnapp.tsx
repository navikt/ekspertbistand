import { BodyShort, HStack } from "@navikt/ds-react";
import type { ReactNode } from "react";
import classes from "./PanelKnapp.module.css";

type PanelKnappProps = {
  tekst: string;
  ikon: ReactNode;
  åpen: boolean;
  panelId: string;
  onClick: () => void;
  antall?: number;
  "aria-label"?: string;
};

/** Knapp som åpner og lukker et sidepanel på saken. Aktiv knapp er understreket. */
export default function PanelKnapp({
  tekst,
  ikon,
  åpen,
  panelId,
  onClick,
  antall,
  "aria-label": ariaLabel,
}: PanelKnappProps) {
  return (
    <button
      type="button"
      className={classes.knapp}
      aria-expanded={åpen}
      aria-controls={åpen ? panelId : undefined}
      aria-label={ariaLabel}
      onClick={onClick}
    >
      <HStack as="span" gap="space-8" align="center" wrap={false}>
        {ikon}
        <BodyShort as="span" weight="semibold">
          {tekst}
        </BodyShort>
        {antall !== undefined && antall > 0 && (
          <BodyShort as="span" size="small" weight="semibold" className={classes.antall}>
            {antall}
          </BodyShort>
        )}
      </HStack>
    </button>
  );
}
