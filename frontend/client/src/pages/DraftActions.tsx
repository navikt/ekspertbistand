import React, { useCallback, useState } from "react";
import { Box, Button, HGrid } from "@navikt/ds-react";
import { FloppydiskIcon, TrashIcon } from "@navikt/aksel-icons";
import { DeleteDraftModal } from "../components/DeleteDraftModal";
import { SaveDraftModal } from "../components/SaveDraftModal";

type DraftActionsRenderProps = {
  continueButton: React.ReactElement;
  deleteButton: React.ReactElement;
};

type DraftActionsProps = {
  onContinueLater: () => void | Promise<void>;
  onDeleteDraft: () => void | Promise<void>;
  renderButtons?: (buttons: DraftActionsRenderProps) => React.ReactNode;
};

const defaultRenderButtons = ({ continueButton, deleteButton }: DraftActionsRenderProps) => (
  <HGrid gap={{ xs: "4", sm: "8 4" }} columns={{ xs: 1, sm: 2 }} width={{ sm: "fit-content" }}>
    {continueButton}
    {deleteButton}
  </HGrid>
);

export function DraftActions({
  onContinueLater,
  onDeleteDraft,
  renderButtons = defaultRenderButtons,
}: DraftActionsProps) {
  const [continueModalOpen, setContinueModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const handleContinueConfirm = useCallback(async () => {
    setContinueModalOpen(false);
    await onContinueLater();
  }, [onContinueLater]);

  const handleDeleteConfirm = useCallback(async () => {
    setDeleteModalOpen(false);
    await onDeleteDraft();
  }, [onDeleteDraft]);

  const continueButton = (
    <Box asChild marginBlock={{ xs: "4 0", sm: "0" }}>
      <Button
        type="button"
        variant="tertiary"
        icon={<FloppydiskIcon aria-hidden />}
        iconPosition="left"
        onClick={() => setContinueModalOpen(true)}
      >
        Fortsett senere
      </Button>
    </Box>
  );

  const deleteButton = (
    <Button
      type="button"
      variant="tertiary"
      icon={<TrashIcon aria-hidden />}
      iconPosition="left"
      onClick={() => setDeleteModalOpen(true)}
    >
      Slett søknaden
    </Button>
  );

  return (
    <>
      {renderButtons({ continueButton, deleteButton })}
      <DeleteDraftModal
        open={deleteModalOpen}
        onClose={() => setDeleteModalOpen(false)}
        onConfirm={handleDeleteConfirm}
      />
      <SaveDraftModal
        open={continueModalOpen}
        onClose={() => setContinueModalOpen(false)}
        onConfirm={handleContinueConfirm}
      />
    </>
  );
}
