import { useRef, useState } from "react";
import { BodyShort, Button, Heading, LocalAlert, Textarea, VStack } from "@navikt/ds-react";
import { type SluttrapportStatusDto, useSluttrapportVurdering } from "../hooks/useSluttrapport";

type Props = {
  sakId: string;
  sluttrapport: SluttrapportStatusDto;
  erTildeltSaksbehandler: boolean;
};

export default function Sluttrapportkontroll({
  sakId,
  sluttrapport,
  erTildeltSaksbehandler,
}: Props) {
  const [visBegrunnelse, setVisBegrunnelse] = useState(false);
  const [begrunnelse, setBegrunnelse] = useState("");
  const [begrunnelseFeil, setBegrunnelseFeil] = useState<string | undefined>();
  const begrunnelseRef = useRef<HTMLTextAreaElement>(null);
  const { vurder, isSaving, error } = useSluttrapportVurdering(sakId);
  const kanVurdere = sluttrapport.status === "MOTTATT" && erTildeltSaksbehandler;

  const krevNyInnsending = async () => {
    const trimmetBegrunnelse = begrunnelse.trim();
    if (!trimmetBegrunnelse) {
      setBegrunnelseFeil("Skriv en begrunnelse for hvorfor ny innsending kreves.");
      begrunnelseRef.current?.focus();
      return;
    }
    if (trimmetBegrunnelse.length > 4000) {
      setBegrunnelseFeil("Begrunnelsen kan ikke være lengre enn 4000 tegn.");
      begrunnelseRef.current?.focus();
      return;
    }

    setBegrunnelseFeil(undefined);
    const lagret = await vurder(sluttrapport, {
      utfall: "NY_INNSENDING_KREVES",
      begrunnelse: trimmetBegrunnelse,
    });
    if (lagret) setVisBegrunnelse(false);
  };

  const bekreft = () => vurder(sluttrapport, { utfall: "BEKREFTET" });

  return (
    <VStack gap="space-16" padding="space-16" paddingBlock="space-32">
      <Heading level="2" size="xsmall">
        Kontroll av sluttrapport
      </Heading>
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
        visBegrunnelse ? (
          <VStack gap="space-16">
            <Textarea
              ref={begrunnelseRef}
              label="Begrunnelse for ny innsending"
              description="Skriv hva arbeidsgiver må rette eller sende inn på nytt. Maks 4000 tegn."
              value={begrunnelse}
              maxLength={4000}
              error={begrunnelseFeil}
              onChange={(event) => {
                setBegrunnelse(event.target.value);
                if (begrunnelseFeil) setBegrunnelseFeil(undefined);
              }}
              disabled={isSaving}
            />
            <Button type="button" onClick={krevNyInnsending} loading={isSaving} disabled={isSaving}>
              Bekreft krav om ny innsending
            </Button>
            <Button
              type="button"
              variant="tertiary"
              onClick={() => setVisBegrunnelse(false)}
              disabled={isSaving}
            >
              Avbryt
            </Button>
          </VStack>
        ) : (
          <VStack gap="space-12" align="start">
            <Button type="button" onClick={bekreft} loading={isSaving} disabled={isSaving}>
              Bekreft
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setVisBegrunnelse(true)}
              disabled={isSaving}
            >
              Krev ny innsending
            </Button>
          </VStack>
        )
      ) : sluttrapport.status === "MOTTATT" ? (
        <BodyShort>Det er bare tildelt saksbehandler som kan vurdere sluttrapporten.</BodyShort>
      ) : (
        <BodyShort>Sluttrapporten er allerede vurdert.</BodyShort>
      )}
    </VStack>
  );
}
