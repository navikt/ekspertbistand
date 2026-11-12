import { Fragment } from "react";
import { BodyShort } from "@navikt/ds-react";

export type NokkeldataRad = [label: string, verdi: string | null | undefined];

export function Nokkeldata({ rader }: { rader: NokkeldataRad[] }) {
  return (
    <dl
      style={{
        display: "grid",
        gridTemplateColumns: "max-content 1fr",
        columnGap: "var(--ax-space-24)",
        rowGap: "var(--ax-space-4)",
        margin: 0,
      }}
    >
      {rader
        .filter((rad): rad is [string, string] => Boolean(rad[1]))
        .map(([label, verdi]) => (
          <Fragment key={label}>
            <BodyShort as="dt" size="large" weight="semibold">
              {label}
            </BodyShort>
            <BodyShort as="dd" size="large" style={{ margin: 0 }}>
              {verdi}
            </BodyShort>
          </Fragment>
        ))}
    </dl>
  );
}
