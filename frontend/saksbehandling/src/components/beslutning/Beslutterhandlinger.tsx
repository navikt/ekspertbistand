import { Alert, Button, HStack, VStack } from "@navikt/ds-react";
import { useState } from "react";
import type { ReturInput } from "../../hooks/useBeslutning";
import ReturModal from "./ReturModal";

type Props = {
  forelopigVedtakVersjon: number;
  /** Innlogget sendte det foreløpige vedtaket. Backend avviser godkjenning uansett. */
  erInhabil: boolean;
  onGodkjenn: () => void;
  onReturner: (input: ReturInput) => Promise<boolean>;
  isSaving: boolean;
  error: Error | null;
  nullstillFeil: () => void;
};

export default function Beslutterhandlinger({
  forelopigVedtakVersjon,
  erInhabil,
  onGodkjenn,
  onReturner,
  isSaving,
  error,
  nullstillFeil,
}: Props) {
  const [visReturModal, setVisReturModal] = useState(false);

  const åpneReturModal = () => {
    nullstillFeil();
    setVisReturModal(true);
  };

  const lukkReturModal = () => {
    nullstillFeil();
    setVisReturModal(false);
  };

  return (
    <VStack gap="space-16">
      {erInhabil && (
        <Alert variant="info" size="small">
          Du sendte det foreløpige vedtaket og kan ikke fatte vedtaket.
        </Alert>
      )}
      {error && !visReturModal && (
        <Alert variant="error" size="small">
          {error.message}
        </Alert>
      )}
      <HStack gap="space-16">
        <Button
          type="button"
          variant="secondary"
          onClick={åpneReturModal}
          disabled={isSaving && !visReturModal}
        >
          Send i retur til saksbehandler
        </Button>
        {!erInhabil && (
          <Button type="button" onClick={onGodkjenn} loading={isSaving && !visReturModal}>
            Godkjenn
          </Button>
        )}
      </HStack>
      <ReturModal
        open={visReturModal}
        onClose={lukkReturModal}
        forelopigVedtakVersjon={forelopigVedtakVersjon}
        onSend={onReturner}
        isSaving={isSaving}
        error={error}
      />
    </VStack>
  );
}
