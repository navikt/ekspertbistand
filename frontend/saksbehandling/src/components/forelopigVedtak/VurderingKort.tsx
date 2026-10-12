import { ThumbDownIcon, ThumbUpIcon } from "@navikt/aksel-icons";
import { BodyShort, HGrid, InfoCard } from "@navikt/ds-react";
import { Fragment } from "react";
import type { ForelopigUtfall } from "../../hooks/useSak";
import { type Rad, UTFALL_TEKST } from "./forelopigVedtak";

const FARGE = {
  INNVILGE: "success",
  INNVILGE_REDUSERT: "warning",
  AVSLAA: "danger",
} as const satisfies Record<ForelopigUtfall, string>;

type Props = {
  utfall: ForelopigUtfall;
  rader: Rad[];
  tittelNivå: "h2" | "h3";
};

export default function VurderingKort({ utfall, rader, tittelNivå }: Props) {
  const Ikon = utfall === "AVSLAA" ? ThumbDownIcon : ThumbUpIcon;
  return (
    <InfoCard data-color={FARGE[utfall]} size="small">
      <InfoCard.Header icon={<Ikon aria-hidden />}>
        <InfoCard.Title as={tittelNivå}>Vurdering: {UTFALL_TEKST[utfall]}</InfoCard.Title>
      </InfoCard.Header>
      <InfoCard.Content>
        <HGrid columns="max-content 1fr" gap="space-2 space-8">
          {rader.map(({ label, verdi }) => (
            <Fragment key={label}>
              <BodyShort size="small">{label}:</BodyShort>
              <BodyShort size="small" weight="semibold">
                {verdi}
              </BodyShort>
            </Fragment>
          ))}
        </HGrid>
      </InfoCard.Content>
    </InfoCard>
  );
}
