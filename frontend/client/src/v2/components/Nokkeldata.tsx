import { Fragment } from "react";
import { BodyShort } from "@navikt/ds-react";

export type NokkeldataRad = [label: string, verdi: string | null | undefined];

export function Nokkeldata({
  rader,
  kompakt = false,
}: {
  rader: NokkeldataRad[];
  kompakt?: boolean;
}) {
  return (
    <dl
      style={{
        display: "grid",
        gridTemplateColumns: kompakt ? "max-content minmax(0, 1fr)" : "max-content 1fr",
        columnGap: "var(--ax-space-24)",
        rowGap: "var(--ax-space-4)",
        margin: 0,
      }}
    >
      {rader
        .filter((rad): rad is [string, string] => Boolean(rad[1]))
        .map(([label, verdi]) => (
          <Fragment key={label}>
            <BodyShort
              as="dt"
              size={kompakt ? "small" : "large"}
              weight={kompakt ? "regular" : "semibold"}
            >
              {label}
            </BodyShort>
            <BodyShort
              as="dd"
              size={kompakt ? "small" : "large"}
              weight={kompakt ? "semibold" : "regular"}
              style={{
                margin: 0,
                minWidth: kompakt ? 0 : undefined,
                overflowWrap: kompakt ? "anywhere" : undefined,
              }}
            >
              {verdi}
            </BodyShort>
          </Fragment>
        ))}
    </dl>
  );
}
