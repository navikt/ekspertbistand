import { Button, Modal, Heading } from "@navikt/ds-react";

type DeleteDraftModalProps = {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

export function DeleteDraftModal({ open, onClose, onConfirm }: DeleteDraftModalProps) {
  return (
    <Modal open={open} onClose={onClose} aria-label="Slett Søknad">
      <Modal.Header>
        <Heading level="2" size="medium">
          Slett søknad?
        </Heading>
      </Modal.Header>
      <Modal.Body>
        Du er i ferd med å slette denne søknaden. Handlingen fjerner søknaden for alle som har
        tilgang til den, og kan ikke angres.
      </Modal.Body>
      <Modal.Footer>
        <Button type="button" onClick={onConfirm}>
          Slett søknad
        </Button>
        <Button variant="secondary" type="button" onClick={onClose}>
          Avbryt
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
