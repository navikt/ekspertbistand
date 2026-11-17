import { useCallback } from "react";
import type { MouseEventHandler } from "react";
import type { NavigateOptions } from "react-router";
import { SOKNADER_PATH } from "../utils/constants";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { useAutosaveNavigate } from "./useAutosaveNavigate";

export type StepSegment = "steg-1" | "steg-2" | "steg-3" | "oppsummering";

export const useSkjemaNavigation = () => {
  const { draftId } = useSoknadDraft();
  const autosaveNavigate = useAutosaveNavigate();

  const goToSoknader = useCallback(
    (options?: NavigateOptions) => {
      autosaveNavigate(SOKNADER_PATH, options);
    },
    [autosaveNavigate]
  );

  const goToSoknaderWithSaveNotice = useCallback(
    (options?: NavigateOptions) => {
      autosaveNavigate(SOKNADER_PATH, options, { savedDraft: true });
    },
    [autosaveNavigate]
  );

  /** [id] er utkastet som nettopp ble opprettet, når steg 1 ikke hadde noe utkast. */
  const navigateToSegment = useCallback(
    (segment: StepSegment, id: string | null = draftId) => {
      autosaveNavigate(`/v2/skjema/${id}/${segment}`);
    },
    [autosaveNavigate, draftId]
  );

  const goToStep1 = useCallback(() => navigateToSegment("steg-1"), [navigateToSegment]);
  const goToStep2 = useCallback(() => navigateToSegment("steg-2"), [navigateToSegment]);
  const goToStep3 = useCallback(() => navigateToSegment("steg-3"), [navigateToSegment]);
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
    goToSoknaderWithSaveNotice,
    goToStep1,
    goToStep2,
    goToStep3,
    goToSummary,
    goToSegment: navigateToSegment,
    createLinkHandler,
  };
};
