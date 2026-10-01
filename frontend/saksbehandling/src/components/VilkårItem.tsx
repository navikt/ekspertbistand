import {
  Accordion,
  Alert,
  BodyLong,
  BodyShort,
  Button,
  Detail,
  HStack,
  Label,
  Tag,
  Textarea,
  VStack,
} from "@navikt/ds-react";
import { useState } from "react";
import type { Vilkår, Vilkårstatus } from "../hooks/useVilkår";

type Props = {
  vilkår: Vilkår;
  isSaving: boolean;
  error: Error | null;
  onLagre: (input: {
    vilkårId: string;
    status: Exclude<Vilkårstatus, "ikke_vurdert">;
    kommentar?: string;
  }) => Promise<boolean>;
};

function StatusTag({ status }: { status: Vilkårstatus }) {
  if (status === "oppfylt") {
    return (
      <Tag variant="success" size="small">
        Oppfylt
      </Tag>
    );
  }

  if (status === "ikke_oppfylt") {
    return (
      <Tag variant="error" size="small">
        Ikke oppfylt
      </Tag>
    );
  }
}

function formatTidspunkt(iso: string | undefined) {
  return iso
    ? new Intl.DateTimeFormat("nb-NO", {
        day: "numeric",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }).format(new Date(iso))
    : null;
}

export default function VilkårItem({ vilkår, isSaving, error, onLagre }: Props) {
  const [redigerer, setRedigerer] = useState(false);
  const [kommentar, setKommentar] = useState(vilkår.vurdering?.kommentar ?? "");

  const erVurdert = vilkår.vurdering?.status !== "ikke_vurdert";

  const åpneRedigering = () => {
    setKommentar(vilkår.vurdering?.kommentar ?? "");
    setRedigerer(true);
  };

  const lagre = async (status: Exclude<Vilkårstatus, "ikke_vurdert">) => {
    const lagret = await onLagre({
      vilkårId: vilkår.id,
      status,
      kommentar: kommentar.trim() || undefined,
    });

    if (lagret) {
      setRedigerer(false);
    }
  };

  return (
    <Accordion.Item defaultOpen>
      <Accordion.Header>{vilkår.tittel}</Accordion.Header>
      <Accordion.Content>
        <VStack gap="space-16">
          <BodyShort>{vilkår.beskrivelse}</BodyShort>

          {erVurdert && !redigerer && (
            <HStack>
              <StatusTag status={vilkår.vurdering.status} />
            </HStack>
          )}

          {!redigerer &&
            !vilkår.vurdering.automatisk &&
            vilkår.vurdering.status !== "ikke_vurdert" && (
              <VStack gap="space-4">
                {vilkår.vurdering.kommentar && (
                  <>
                    <Label size="small">Kommentar</Label>
                    <BodyLong size="small">{vilkår.vurdering.kommentar}</BodyLong>
                  </>
                )}
                <Detail>
                  Vurdert av {vilkår.vurdering.vurdertAv}{" "}
                  {formatTidspunkt(vilkår.vurdering.vurdertTidspunkt)}
                </Detail>
              </VStack>
            )}

          {error && redigerer && (
            <Alert variant="error" size="small" inline>
              {error.message}
            </Alert>
          )}

          {redigerer ? (
            <VStack gap="space-12">
              <Textarea
                label="Begrunnelse"
                minRows={1}
                value={kommentar}
                onChange={(event) => setKommentar(event.target.value)}
              />
              <HStack gap="space-8" wrap>
                <Button
                  variant="primary"
                  data-color="success"
                  size="small"
                  loading={isSaving}
                  onClick={() => void lagre("oppfylt")}
                >
                  Oppfylt
                </Button>
                <Button
                  variant="primary"
                  data-color="danger"
                  size="small"
                  disabled={isSaving}
                  onClick={() => void lagre("ikke_oppfylt")}
                >
                  Ikke oppfylt
                </Button>
                <Button
                  variant="tertiary"
                  size="small"
                  disabled={isSaving}
                  onClick={() => setRedigerer(false)}
                >
                  Avbryt
                </Button>
              </HStack>
            </VStack>
          ) : (
            <HStack>
              {erVurdert ? (
                <Button variant="tertiary" size="small" onClick={åpneRedigering}>
                  Endre
                </Button>
              ) : (
                <Button variant="primary" size="small" onClick={åpneRedigering}>
                  {"Vurder"}
                </Button>
              )}
            </HStack>
          )}
        </VStack>
      </Accordion.Content>
    </Accordion.Item>
  );
}
