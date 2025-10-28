import { Button, Modal, Heading, BodyLong } from "@navikt/ds-react";

type SaveDraftModalProps = {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

export function SaveDraftModal({ open, onClose, onConfirm }: SaveDraftModalProps) {
  return (
    <Modal open={open} onClose={onClose} aria-label="Lagre utkast">
      <Modal.Header>
        <Heading level="2" size="medium">
          Fortsett senere - lagre som utkast
        </Heading>
      </Modal.Header>
      <Modal.Body>
        <BodyLong>
          Søknaden blir lagret som et utkast og alle med tilgang til ekspertbistand i virksomheten
          kan se den.
        </BodyLong>
        <BodyLong>Hvis du ikke fortsetter innen 48 timer, blir utkastet slettet.</BodyLong>
      </Modal.Body>
      <Modal.Footer>
        <Button type="button" onClick={onConfirm}>
          Lagre som utkast
        </Button>
        <Button variant="secondary" type="button" onClick={onClose}>
          Avbryt
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
