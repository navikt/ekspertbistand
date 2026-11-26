import { useState } from "react";
import { CheckmarkIcon, HourglassIcon } from "@navikt/aksel-icons";
import {
  BodyShort,
  Box,
  Button,
  Detail,
  Heading,
  HStack,
  Label,
  LocalAlert,
  Select,
  Tag,
  VStack,
} from "@navikt/ds-react";
import { type SluttrapportStatusDto, useSluttrapportVurdering } from "../hooks/useSluttrapport";

type Props = {
  sakId: string;
  sluttrapport: SluttrapportStatusDto;
  erTildeltSaksbehandler: boolean;
};

type Valg = "BEKREFTET" | "NY_INNSENDING_KREVES";

const TEKST_NY_INNSENDING_TIL_ARBEIDSGIVER =
  "Send sluttrapporten på nytt. Kontroller at vedlegget er riktig dokument og gjelder riktig person.";
const TEKST_JOURNALFORING = "Sluttrapporten blir journalført på personmappa i Gosys.";

const datoFormat = new Intl.DateTimeFormat("nb-NO", { dateStyle: "medium" });
const datoOgTidFormat = new Intl.DateTimeFormat("nb-NO", {
  dateStyle: "long",
  timeStyle: "short",
});

function vurdertAv(sluttrapport: SluttrapportStatusDto, format: Intl.DateTimeFormat) {
  const person = sluttrapport.vurdertAvNavn
    ? `${sluttrapport.vurdertAvNavn} (${sluttrapport.vurdertAvIdent})`
    : sluttrapport.vurdertAvIdent;
  const tidspunkt = sluttrapport.vurdertTidspunkt
    ? format.format(new Date(sluttrapport.vurdertTidspunkt))
    : null;
  return [person, tidspunkt].filter(Boolean).join(" ");
}

function Skillelinje() {
  return <Box borderWidth="0 0 1 0" borderColor="neutral-subtle" />;
}

function Vurderingsskjema({
  sakId,
  sluttrapport,
}: {
  sakId: string;
  sluttrapport: SluttrapportStatusDto;
}) {
  const [valg, setValg] = useState<Valg>("BEKREFTET");
  const { vurder, isSaving, error } = useSluttrapportVurdering(sakId);

  const lagre = () =>
    valg === "BEKREFTET"
      ? vurder(sluttrapport, { utfall: "BEKREFTET" })
      : vurder(sluttrapport, {
          utfall: "NY_INNSENDING_KREVES",
          begrunnelse: TEKST_NY_INNSENDING_TIL_ARBEIDSGIVER,
        });

  return (
    <VStack gap="space-24" align="start">
      {error && (
        <div aria-live="polite">
          <LocalAlert status="error">
            <LocalAlert.Header>
              <LocalAlert.Title>Kunne ikke lagre vurderingen</LocalAlert.Title>
            </LocalAlert.Header>
            <LocalAlert.Content>{error.message}</LocalAlert.Content>
          </LocalAlert>
        </div>
      )}
      <Select
        label="Kontroll av sluttrapport"
        value={valg}
        onChange={(event) => setValg(event.target.value as Valg)}
        disabled={isSaving}
      >
        <option value="BEKREFTET">Mottatt sluttrapport dokumenterer tiltaket</option>
        <option value="NY_INNSENDING_KREVES">Be arbeidsgiver sende sluttrapporten på nytt</option>
      </Select>
      {valg === "NY_INNSENDING_KREVES" && (
        <VStack gap="space-8">
          <Label as="p">Tekst som vises til arbeidsgiver</Label>
          <BodyShort>{TEKST_NY_INNSENDING_TIL_ARBEIDSGIVER}</BodyShort>
        </VStack>
      )}
      <Button type="button" onClick={lagre} loading={isSaving} disabled={isSaving}>
        {valg === "BEKREFTET" ? "Registrer som mottatt" : "Be om sluttrapport på nytt"}
      </Button>
      {valg === "BEKREFTET" && <BodyShort>{TEKST_JOURNALFORING}</BodyShort>}
    </VStack>
  );
}

function Bekreftet({ sluttrapport }: { sluttrapport: SluttrapportStatusDto }) {
  return (
    <VStack gap="space-16">
      <HStack justify="space-between" align="center" gap="space-8" wrap={false}>
        <Heading level="2" size="xsmall">
          Sluttrapport registrert som mottatt
        </Heading>
        <Tag
          size="small"
          variant="moderate"
          data-color="success"
          icon={<CheckmarkIcon aria-hidden />}
        >
          Mottatt
        </Tag>
      </HStack>
      <VStack gap="space-2">
        <Detail weight="semibold">Registrert av</Detail>
        <Detail>{vurdertAv(sluttrapport, datoFormat)}</Detail>
      </VStack>
      <BodyShort>{TEKST_JOURNALFORING}</BodyShort>
    </VStack>
  );
}

function NyInnsendingKreves({ sluttrapport }: { sluttrapport: SluttrapportStatusDto }) {
  return (
    <VStack gap="space-16">
      <HStack justify="space-between" align="center" gap="space-8" wrap={false}>
        <Heading level="2" size="xsmall">
          Sluttrapport avvist
        </Heading>
        <Tag size="small" variant="outline" data-color="info" icon={<HourglassIcon aria-hidden />}>
          Venter på ny sluttrapport
        </Tag>
      </HStack>
      <VStack gap="space-2">
        <Detail weight="semibold">Avvist av</Detail>
        <Detail>{vurdertAv(sluttrapport, datoOgTidFormat)}</Detail>
      </VStack>
      <Skillelinje />
      <VStack gap="space-4">
        <Detail weight="semibold">Beskjed til arbeidsgiver</Detail>
        {sluttrapport.begrunnelse && <BodyShort>{sluttrapport.begrunnelse}</BodyShort>}
        <Detail>
          Hvis arbeidsgiver ikke sender ny sluttrapport innen en uke, sender Nav en påminnelse.
        </Detail>
      </VStack>
      <Skillelinje />
    </VStack>
  );
}

export default function Sluttrapportkontroll({
  sakId,
  sluttrapport,
  erTildeltSaksbehandler,
}: Props) {
  return (
    <Box padding="space-16" paddingBlock="space-32">
      {sluttrapport.status === "BEKREFTET" ? (
        <Bekreftet sluttrapport={sluttrapport} />
      ) : sluttrapport.status === "NY_INNSENDING_KREVES" ? (
        <NyInnsendingKreves sluttrapport={sluttrapport} />
      ) : erTildeltSaksbehandler ? (
        <Vurderingsskjema sakId={sakId} sluttrapport={sluttrapport} />
      ) : (
        <VStack gap="space-16">
          <Heading level="2" size="xsmall">
            Kontroll av sluttrapport
          </Heading>
          <BodyShort>Det er bare tildelt saksbehandler som kan vurdere sluttrapporten.</BodyShort>
        </VStack>
      )}
    </Box>
  );
}
