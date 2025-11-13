import { useCallback } from "react";
import type { MouseEventHandler } from "react";
import { SOKNADER_PATH } from "../utils/constants";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { useAutosaveNavigate } from "./useAutosaveNavigate";

type StepSegment = "steg-1" | "steg-2" | "oppsummering";

export const useSkjemaNavigation = () => {
  const { draftId } = useSoknadDraft();
  const autosaveNavigate = useAutosaveNavigate();

  const goToSoknader = useCallback(() => {
    autosaveNavigate(SOKNADER_PATH);
  }, [autosaveNavigate]);

  const navigateToSegment = useCallback(
    (segment: StepSegment) => {
      autosaveNavigate(`/skjema/${draftId}/${segment}`);
    },
    [autosaveNavigate, draftId]
  );

  const goToStep1 = useCallback(() => navigateToSegment("steg-1"), [navigateToSegment]);
  const goToStep2 = useCallback(() => navigateToSegment("steg-2"), [navigateToSegment]);
  const goToSummary = useCallback(() => navigateToSegment("oppsummering"), [navigateToSegment]);

  const createLinkHandler = useCallback(
    (navigateFn: () => void): MouseEventHandler<HTMLAnchorElement> =>
      (event) => {
        event.preventDefault();
        navigateFn();
      },
    []
  );

  return {
    goToSoknader,
    goToStep1,
    goToStep2,
    goToSummary,
    createLinkHandler,
  };
};
