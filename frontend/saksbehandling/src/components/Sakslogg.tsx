import { ClockDashedIcon } from "@navikt/aksel-icons";
import {
  BodyShort,
  Box,
  HStack,
  Loader,
  LocalAlert,
  Popover,
  Process,
  Tag,
  VStack,
} from "@navikt/ds-react";
import { useId, useState } from "react";
import { type AktorRolle, type SaksloggInnslag, useSakslogg } from "../hooks/useSakslogg";
import HeaderKnapp from "./HeaderKnapp";

const rolleTag: Record<
  AktorRolle,
  { tekst: string; farge: "meta-purple" | "info" | "warning" }
> = {
  SAKSBEHANDLER: { tekst: "Saksbehandler", farge: "meta-purple" },
  BESLUTTER: { tekst: "Beslutter", farge: "info" },
  SYSTEM: { tekst: "System", farge: "warning" },
};

const tidspunktFormat = new Intl.DateTimeFormat("nb-NO", {
  day: "2-digit",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

function formaterTidspunkt(iso: string) {
  return tidspunktFormat.format(new Date(iso));
}

function Innslag({ innslag }: { innslag: SaksloggInnslag }) {
  const rolle = rolleTag[innslag.utfortAvRolle];
  const erSystem = innslag.utfortAvRolle === "SYSTEM";

  return (
    <Process.Event>
      <VStack gap="space-4">
        <HStack gap="space-8" align="center" wrap>
          <BodyShort size="small" textColor="subtle">
            {formaterTidspunkt(innslag.tidspunkt)}
          </BodyShort>
          <BodyShort size="small" aria-hidden>
            •
          </BodyShort>
          <BodyShort weight="semibold">{innslag.utfortAvNavn}</BodyShort>
          {erSystem && (
            <Tag size="xsmall" variant="moderate" data-color={rolle.farge}>
              {rolle.tekst}
            </Tag>
          )}
        </HStack>
        {!erSystem && (
          <div>
            <Tag size="xsmall" variant="moderate" data-color={rolle.farge}>
              {rolle.tekst}
            </Tag>
          </div>
        )}
        {innslag.notat && <BodyShort>{innslag.notat}</BodyShort>}
      </VStack>
    </Process.Event>
  );
}

function SaksloggInnhold({ sakId }: { sakId: string }) {
  const { innslag, error, isLoading } = useSakslogg(sakId);

  if (isLoading) return <Loader size="medium" title="Laster saksloggen" />;
  if (error) {
    return (
      <LocalAlert status="error" size="small">
        <LocalAlert.Header>
          <LocalAlert.Title as="h3">
            {error.status === 403
              ? "Du har ikke tilgang til saksloggen."
              : "Kunne ikke hente saksloggen."}
          </LocalAlert.Title>
        </LocalAlert.Header>
      </LocalAlert>
    );
  }
  if (!innslag || innslag.length === 0) return <BodyShort>Ingen hendelser ennå.</BodyShort>;

  return (
    <Process aria-label="Sakslogg">
      {innslag.map((i) => (
        <Innslag key={i.id} innslag={i} />
      ))}
    </Process>
  );
}

export default function Sakslogg({ sakId }: { sakId: string }) {
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);
  const [open, setOpen] = useState(false);
  const popoverId = useId();

  return (
    <>
      <HeaderKnapp
        ref={setAnchorEl}
        tekst="Logg"
        ikon={<ClockDashedIcon aria-hidden fontSize="1.5rem" />}
        åpen={open}
        onClick={() => setOpen((o) => !o)}
        aria-controls={open ? popoverId : undefined}
      />
      <Popover
        id={popoverId}
        open={open}
        onClose={() => setOpen(false)}
        anchorEl={anchorEl}
        placement="bottom-end"
      >
        <Popover.Content>
          {/* Process.Event med prikk har negativ margin-top, så uten padding klippes første innslag av overflowY. */}
          <Box
            maxWidth="28rem"
            maxHeight="70vh"
            overflowY="auto"
            paddingBlock="space-12 space-4"
            paddingInline="space-4"
          >
            {open && <SaksloggInnhold sakId={sakId} />}
          </Box>
        </Popover.Content>
      </Popover>
    </>
  );
}
