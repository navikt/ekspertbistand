import { useState } from "react";
import { HourglassIcon } from "@navikt/aksel-icons";
import {
  BodyShort,
  Box,
  Button,
  Checkbox,
  CheckboxGroup,
  Detail,
  Heading,
  HStack,
  Label,
  LocalAlert,
  Select,
  Tag,
  VStack,
} from "@navikt/ds-react";
import {
  refusjonBeskjed,
  REFUSJON_ÅRSAKER,
  REFUSJON_ÅRSAKSREKKEFØLGE,
  type RefusjonStatusDto,
  type RefusjonskravArsak,
  useRefusjonVurdering,
} from "../hooks/useRefusjon";

type Props = {
  sakId: string;
  refusjon: RefusjonStatusDto;
  erTildeltSaksbehandler: boolean;
  erInnvilget: boolean;
  sluttrapportBekreftet: boolean;
  sluttrapportLaster: boolean;
  sluttrapportFeilet: boolean;
};

export default function Refusjonskontroll({
  sakId,
  refusjon,
  erTildeltSaksbehandler,
  erInnvilget,
  sluttrapportBekreftet,
  sluttrapportLaster,
  sluttrapportFeilet,
}: Props) {
  const [valg, setValg] = useState("");
  const [arsaker, setArsaker] = useState<RefusjonskravArsak[]>([]);
  const [arsaksfeil, setArsaksfeil] = useState(false);
  const { vurder, isSaving, error, erMottatt } = useRefusjonVurdering(sakId);
  const kanVurdere = erTildeltSaksbehandler && erInnvilget && sluttrapportBekreftet;

  if (refusjon.status === "NY_INNSENDING_KREVES") {
    const tidspunkt = refusjon.vurdertTidspunkt
      ? new Intl.DateTimeFormat("nb-NO", { dateStyle: "long", timeStyle: "short" }).format(
          new Date(refusjon.vurdertTidspunkt)
        )
      : "";
    return (
      <VStack gap="space-16" padding="space-16" paddingBlock="space-32">
        <HStack gap="space-8" justify="space-between" align="center">
          <Heading level="2" size="xsmall">
            Refusjonskrav avvist
          </Heading>
          <Tag
            size="small"
            variant="outline"
            data-color="info"
            icon={<HourglassIcon aria-hidden />}
          >
            Venter nytt refusjonskrav
          </Tag>
        </HStack>
        <VStack gap="space-2">
          <Detail weight="semibold">Avvist av</Detail>
          <Detail>
            {refusjon.vurdertAvNavn} ({refusjon.vurdertAvIdent}) {tidspunkt}
          </Detail>
        </VStack>
        <Box borderWidth="0 0 1 0" borderColor="neutral-subtle" />
        <Label as="p">Begrunnelse</Label>
        <ul>
          {refusjon.arsaker.map((arsak) => (
            <li key={arsak}>{REFUSJON_ÅRSAKER[arsak].label}</li>
          ))}
        </ul>
        <Label as="p">Beskjed til arbeidsgiver</Label>
        <BodyShort style={{ whiteSpace: "pre-line" }}>{refusjon.begrunnelse}</BodyShort>
      </VStack>
    );
  }

  return (
    <VStack gap="space-24" align="start" padding="space-16" paddingBlock="space-32">
      {!kanVurdere ? (
        <>
          <Heading level="2" size="xsmall">
            Kontroll av refusjonskrav
          </Heading>
          <BodyShort>
            {!erTildeltSaksbehandler
              ? "Det er bare tildelt saksbehandler som kan vurdere refusjonskravet."
              : !erInnvilget
                ? "Saken må være innvilget før refusjonskravet kan vurderes."
                : sluttrapportFeilet
                  ? "Kunne ikke hente status for sluttrapporten. Last siden på nytt."
                  : sluttrapportLaster
                    ? "Laster status for sluttrapporten."
                    : "Sluttrapporten må være bekreftet før refusjonskravet kan vurderes."}
          </BodyShort>
        </>
      ) : (
        <>
          {error && (
            <LocalAlert status="error" role="alert">
              <LocalAlert.Header>
                <LocalAlert.Title>Kunne ikke bekrefte vurderingen</LocalAlert.Title>
              </LocalAlert.Header>
              <LocalAlert.Content>{error}</LocalAlert.Content>
            </LocalAlert>
          )}
          {erMottatt ? (
            <BodyShort role="status">
              Vurderingen er mottatt. Venter på at lagringen bekreftes.
            </BodyShort>
          ) : (
            <>
              <Select
                label="Kan refusjonskravet behandles med dokumentasjonen som er mottatt?"
                value={valg}
                onChange={(event) => {
                  setValg(event.target.value);
                  setArsaker([]);
                  setArsaksfeil(false);
                }}
                disabled={isSaving}
              >
                <option value="">Velg</option>
                <option value="ja">Ja</option>
                <option value="nei">Nei, arbeidsgiver må sende inn refusjonskravet på nytt</option>
              </Select>
              {valg === "ja" && (
                <BodyShort>
                  Videre behandling av refusjonskravet er ikke tilgjengelig ennå.
                </BodyShort>
              )}
              {valg === "nei" && (
                <>
                  <CheckboxGroup
                    legend="Beskriv hva arbeidsgiver må rette opp i"
                    value={arsaker}
                    onChange={(verdier) => {
                      setArsaker(
                        REFUSJON_ÅRSAKSREKKEFØLGE.filter((arsak) => verdier.includes(arsak))
                      );
                      setArsaksfeil(false);
                    }}
                    error={arsaksfeil ? "Velg minst én årsak." : undefined}
                    disabled={isSaving}
                  >
                    {REFUSJON_ÅRSAKSREKKEFØLGE.map((arsak) => (
                      <Checkbox key={arsak} value={arsak}>
                        {REFUSJON_ÅRSAKER[arsak].label}
                      </Checkbox>
                    ))}
                  </CheckboxGroup>
                  <VStack gap="space-8">
                    <Label as="p">Tekst som vises til arbeidsgiver</Label>
                    <BodyShort style={{ whiteSpace: "pre-line" }}>
                      {refusjonBeskjed(arsaker)}
                    </BodyShort>
                  </VStack>
                </>
              )}
            </>
          )}
          {(valg === "nei" || erMottatt) && (
            <Button
              type="button"
              loading={isSaving}
              disabled={isSaving}
              onClick={() => {
                if (!erMottatt && arsaker.length === 0) {
                  setArsaksfeil(true);
                  return;
                }
                void vurder(refusjon, arsaker);
              }}
            >
              {erMottatt ? "Hent status på nytt" : "Avvis, be arbeidsgiver sende på nytt"}
            </Button>
          )}
        </>
      )}
    </VStack>
  );
}
