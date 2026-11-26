import { useState } from "react";
import { BodyShort, Button, Heading, Label, LocalAlert, Select, VStack } from "@navikt/ds-react";
import { type SluttrapportStatusDto, useSluttrapportVurdering } from "../hooks/useSluttrapport";

type Props = {
  sakId: string;
  sluttrapport: SluttrapportStatusDto;
  erTildeltSaksbehandler: boolean;
};

type Valg = "BEKREFTET" | "NY_INNSENDING_KREVES";

const TEKST_NY_INNSENDING_TIL_ARBEIDSGIVER =
  "Send sluttrapporten på nytt. Kontroller at vedlegget er riktig dokument og gjelder riktig person.";

export default function Sluttrapportkontroll({
  sakId,
  sluttrapport,
  erTildeltSaksbehandler,
}: Props) {
  const [valg, setValg] = useState<Valg>("BEKREFTET");
  const { vurder, isSaving, error } = useSluttrapportVurdering(sakId);
  const kanVurdere = sluttrapport.status === "MOTTATT" && erTildeltSaksbehandler;

  const lagre = () =>
    valg === "BEKREFTET"
      ? vurder(sluttrapport, { utfall: "BEKREFTET" })
      : vurder(sluttrapport, {
          utfall: "NY_INNSENDING_KREVES",
          begrunnelse: TEKST_NY_INNSENDING_TIL_ARBEIDSGIVER,
        });

  return (
    <VStack gap="space-16" padding="space-16" paddingBlock="space-32">
      {!kanVurdere && (
        <Heading level="2" size="xsmall">
          Kontroll av sluttrapport
        </Heading>
      )}
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
      {kanVurdere ? (
        <VStack gap="space-24" align="start">
          <Select
            label="Kontroll av sluttrapport"
            value={valg}
            onChange={(event) => setValg(event.target.value as Valg)}
            disabled={isSaving}
          >
            <option value="BEKREFTET">Mottatt sluttrapport dokumenterer tiltaket</option>
            <option value="NY_INNSENDING_KREVES">
              Be arbeidsgiver sende sluttrapporten på nytt
            </option>
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
          {valg === "BEKREFTET" && (
            <BodyShort>Veileder blir varslet om at sluttrapporten er mottatt.</BodyShort>
          )}
        </VStack>
      ) : sluttrapport.status === "MOTTATT" ? (
        <BodyShort>Det er bare tildelt saksbehandler som kan vurdere sluttrapporten.</BodyShort>
      ) : (
        <BodyShort>Sluttrapporten er allerede vurdert.</BodyShort>
      )}
    </VStack>
  );
}
