import {
  Alert,
  BodyLong,
  Button,
  Checkbox,
  CheckboxGroup,
  Modal,
  Textarea,
  VStack,
} from "@navikt/ds-react";
import { useState } from "react";
import type { ReturInput } from "../../hooks/useBeslutning";
import type { ReturArsak } from "../../hooks/useSak";
import { ugyldigeTegnFeil } from "../forelopigVedtak/forelopigVedtak";
import { RETUR_ARSAK_TEKST } from "./beslutning";

const MAKS_FORKLARING_LENGDE = 1000;

const ARSAKER = Object.keys(RETUR_ARSAK_TEKST) as ReturArsak[];

type Props = {
  open: boolean;
  onClose: () => void;
  forelopigVedtakVersjon: number;
  onSend: (input: ReturInput) => Promise<boolean>;
  isSaving: boolean;
  error: Error | null;
};

type Feil = { aarsaker?: string; forklaring?: string };

function valider(aarsaker: ReturArsak[], forklaring: string): Feil {
  const feil: Feil = {};
  if (aarsaker.length === 0) feil.aarsaker = "Du må velge minst én årsak.";
  if (!forklaring.trim()) feil.forklaring = "Du må skrive en forklaring.";
  else if (forklaring.length > MAKS_FORKLARING_LENGDE) {
    feil.forklaring = `Forklaringen kan ikke være lengre enn ${MAKS_FORKLARING_LENGDE} tegn.`;
  } else {
    feil.forklaring = ugyldigeTegnFeil(forklaring, "Forklaringen") ?? undefined;
  }
  return feil;
}

export default function ReturModal({
  open,
  onClose,
  forelopigVedtakVersjon,
  onSend,
  isSaving,
  error,
}: Props) {
  const [aarsaker, setAarsaker] = useState<ReturArsak[]>([]);
  const [forklaring, setForklaring] = useState("");
  const [visFeil, setVisFeil] = useState(false);

  const feil = visFeil ? valider(aarsaker, forklaring) : {};

  const nullstill = () => {
    setAarsaker([]);
    setForklaring("");
    setVisFeil(false);
  };

  const lukk = () => {
    if (isSaving) return;
    nullstill();
    onClose();
  };

  const send = async () => {
    const valideringsfeil = valider(aarsaker, forklaring);
    if (valideringsfeil.aarsaker || valideringsfeil.forklaring) {
      setVisFeil(true);
      return;
    }
    const ok = await onSend({ forelopigVedtakVersjon, aarsaker, forklaring: forklaring.trim() });
    if (ok) {
      nullstill();
      onClose();
    }
  };

  return (
    <Modal
      open={open}
      onClose={lukk}
      header={{ heading: "Send i retur til saksbehandler" }}
      width="medium"
    >
      <Modal.Body>
        <VStack gap="space-24">
          <BodyLong>Saksbehandler vil få beskjed om årsaker og forklaring til returen.</BodyLong>
          <CheckboxGroup
            legend="Årsak for retur"
            value={aarsaker}
            onChange={(verdi: ReturArsak[]) => setAarsaker(verdi)}
            error={feil.aarsaker}
          >
            {ARSAKER.map((a) => (
              <Checkbox key={a} value={a}>
                {RETUR_ARSAK_TEKST[a]}
              </Checkbox>
            ))}
          </CheckboxGroup>
          <Textarea
            label="Forklaring"
            value={forklaring}
            onChange={(e) => setForklaring(e.target.value)}
            maxLength={MAKS_FORKLARING_LENGDE}
            minRows={4}
            error={feil.forklaring}
          />
          {error && (
            <Alert variant="error" size="small">
              {error.message}
            </Alert>
          )}
        </VStack>
      </Modal.Body>
      <Modal.Footer>
        <Button type="button" onClick={() => void send()} loading={isSaving}>
          Send i retur
        </Button>
        <Button type="button" variant="secondary" onClick={lukk} disabled={isSaving}>
          Avbryt
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
