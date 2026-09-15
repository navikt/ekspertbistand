import {
  CheckmarkCircleFillIcon,
  ClockDashedIcon,
  XMarkOctagonFillIcon,
} from "@navikt/aksel-icons";
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
import type { Vilkår, Vilkårstatus } from "../hooks/useSak";

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

const iconStyle = (color: string) => ({ color, flexShrink: 0 }) as const;

function StatusIkon({ status }: { status: Vilkårstatus }) {
  if (status === "oppfylt") {
    return (
      <CheckmarkCircleFillIcon
        aria-hidden
        style={iconStyle("var(--ax-color-success-icon)")}
        fontSize="1.25rem"
      />
    );
  }

  if (status === "ikke_oppfylt") {
    return (
      <XMarkOctagonFillIcon
        aria-hidden
        style={iconStyle("var(--ax-color-danger-icon)")}
        fontSize="1.25rem"
      />
    );
  }

  return (
    <ClockDashedIcon
      aria-hidden
      style={iconStyle("var(--ax-color-warning-icon)")}
      fontSize="1.25rem"
    />
  );
}

function StatusTag({ status }: { status: Vilkårstatus }) {
  if (status === "oppfylt") {
    return (
      <Tag variant="success" size="xsmall">
        Oppfylt
      </Tag>
    );
  }

  if (status === "ikke_oppfylt") {
    return (
      <Tag variant="error" size="xsmall">
        Ikke oppfylt
      </Tag>
    );
  }

  return (
    <Tag variant="warning" size="xsmall">
      Ikke vurdert
    </Tag>
  );
}

function formatTidspunkt(iso: string | undefined) {
  return iso? new Intl.DateTimeFormat("nb-NO", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(iso)) : null;
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
      <Accordion.Header>
        <HStack gap="space-8" align="center">
          <StatusIkon status={vilkår.vurdering?.status} />
          {vilkår.tittel}
        </HStack>
      </Accordion.Header>
      <Accordion.Content>
        <VStack gap="space-12">
          <BodyShort size="small">{vilkår.beskrivelse}</BodyShort>

          <HStack gap="space-8" align="center" wrap>
            <StatusTag status={vilkår.vurdering?.status} />
            {vilkår.vurdering.automatisk && (
              <Tag variant="neutral" size="xsmall">
                Automatisk
              </Tag>
            )}
          </HStack>

          {!redigerer && !vilkår.vurdering.automatisk && vilkår.vurdering.status !== "ikke_vurdert" && (
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
                size="small"
                minRows={3}
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
              <Button variant="primary" size="small" onClick={åpneRedigering}>
                {erVurdert ? "Endre vurdering" : "Vurder manuelt"}
              </Button>
            </HStack>
          )}
        </VStack>
      </Accordion.Content>
    </Accordion.Item>
  );
}
