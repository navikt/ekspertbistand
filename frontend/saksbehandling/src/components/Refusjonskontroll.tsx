import { ThumbDownIcon, ThumbUpIcon } from "@navikt/aksel-icons";
import {
  BodyShort,
  Box,
  Button,
  HGrid,
  InfoCard,
  Radio,
  RadioGroup,
  Textarea,
  VStack,
} from "@navikt/ds-react";
import { Fragment, useState } from "react";
import type { RefusjonStatusDto } from "../hooks/useRefusjon";
import { formatKroner } from "../utils/belop";

type Vurdering = "godkjenn" | "avslaa";

type Props = {
  refusjon: RefusjonStatusDto;
  deltaker: string;
  arbeidsgiver: string;
};

export default function Refusjonskontroll({ refusjon, deltaker, arbeidsgiver }: Props) {
  const [vurdering, setVurdering] = useState<Vurdering | null>(null);
  const [begrunnelse, setBegrunnelse] = useState("");
  const rader = [
    { label: "Tilskudd", verdi: "Ikke tilgjengelig" },
    { label: "Beløp i refusjonskravet", verdi: formatKroner(refusjon.belopKroner) },
    { label: "Kontonummer", verdi: "Ikke tilgjengelig" },
    { label: "Deltaker", verdi: deltaker },
    { label: "Arbeidsgiver", verdi: arbeidsgiver },
  ];
  const Ikon = vurdering === "godkjenn" ? ThumbUpIcon : ThumbDownIcon;

  return (
    <VStack gap="space-24" padding="space-16" paddingBlock="space-32">
      <RadioGroup
        legend="Kontroll av refusjonskrav"
        description="Valget og en eventuell begrunnelse lagres ikke."
        value={vurdering ?? ""}
        onChange={(verdi: string) => {
          if (verdi === "godkjenn" || verdi === "avslaa") setVurdering(verdi);
        }}
      >
        <Radio value="godkjenn">Godkjenn refusjonskrav. Utgiftene er dokumentert korrekt.</Radio>
        <Radio value="avslaa">Avslå refusjonskrav</Radio>
      </RadioGroup>

      {vurdering && (
        <>
          <InfoCard data-color={vurdering === "godkjenn" ? "success" : "danger"} size="small">
            <InfoCard.Header icon={<Ikon aria-hidden />}>
              <InfoCard.Title as="h2">
                Vurdering: {vurdering === "godkjenn" ? "Godkjenn" : "Avslå"} refusjonskrav
              </InfoCard.Title>
            </InfoCard.Header>
            <InfoCard.Content>
              <HGrid columns="minmax(0, 1fr) minmax(0, 1fr)" gap="space-2 space-8">
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

          {vurdering === "avslaa" && (
            <Textarea
              label="Arbeidsgiver: tilpass begrunnelsen"
              description="Dette er et utkast og blir ikke sendt til arbeidsgiver."
              value={begrunnelse}
              onChange={(event) => setBegrunnelse(event.target.value)}
              minRows={3}
            />
          )}

          <Box>
            <Button type="button" disabled>
              {vurdering === "godkjenn" ? "Send til utbetaling" : "Forhåndsvis foreløpig vedtak"}
            </Button>
            <BodyShort size="small">Handlingen er ikke tilgjengelig ennå.</BodyShort>
          </Box>
        </>
      )}
    </VStack>
  );
}
