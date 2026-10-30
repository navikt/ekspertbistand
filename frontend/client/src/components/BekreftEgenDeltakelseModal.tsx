import { useRef } from "react";
import { BodyLong, Button, Modal } from "@navikt/ds-react";

type BekreftEgenDeltakelseModalProps = {
  open: boolean;
  onBekreft: () => void;
  onAvbryt: () => void;
};

/**
 * Varsler arbeidsgiver om at fødselsnummeret er brukerens eget. Valget kjøres først når
 * dialogen er lukket, slik at fokus kan flyttes uten at dialogen flytter det tilbake.
 * Esc og lukkeknappen behandles som «Nei».
 */
export function BekreftEgenDeltakelseModal({
  open,
  onBekreft,
  onAvbryt,
}: BekreftEgenDeltakelseModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const valgRef = useRef<(() => void) | null>(null);

  const velg = (handling: () => void) => {
    valgRef.current = handling;
    dialogRef.current?.close();
  };

  const handleClose = () => {
    const handling = valgRef.current ?? onAvbryt;
    valgRef.current = null;
    handling();
  };

  return (
    <Modal
      ref={dialogRef}
      open={open}
      onClose={handleClose}
      header={{ heading: "Er du selv deltakeren?" }}
    >
      <Modal.Body>
        <BodyLong>
          Fødselsnummeret du har skrevet inn, er ditt eget. Bekreft at du søker om ekspertbistand
          der du selv er deltakeren.
        </BodyLong>
      </Modal.Body>
      <Modal.Footer>
        <Button type="button" variant="primary" onClick={() => velg(onBekreft)}>
          Ja, jeg er deltakeren
        </Button>
        <Button type="button" variant="secondary" onClick={() => velg(onAvbryt)}>
          Nei, endre fødselsnummer
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
