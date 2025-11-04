import { useCallback } from "react";
import { type NavigateOptions, useNavigate } from "react-router-dom";
import type { Inputs } from "../pages/types";
import { useSoknadDraft } from "../context/SoknadDraftContext";

type Snapshot = Inputs | (() => Inputs);

const resolveSnapshot = (snapshot: Snapshot): Inputs =>
  typeof snapshot === "function" ? (snapshot as () => Inputs)() : snapshot;

export const useDraftNavigation = () => {
  const navigate = useNavigate();
  const { saveDraft } = useSoknadDraft();

  return useCallback(
    (path: string, snapshot: Snapshot, options?: NavigateOptions) => {
      const data = resolveSnapshot(snapshot);
      saveDraft(data);
      navigate(path, options);
    },
    [navigate, saveDraft]
  );
};
