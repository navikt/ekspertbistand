import { useCallback } from "react";
import type { NavigateFunction } from "react-router-dom";
import type { Inputs } from "./types";

type DraftNavigateOptions = Omit<Parameters<NavigateFunction>[1], "state"> & {
  state?: Record<string, unknown>;
};

type UseDraftNavigationArgs = {
  captureSnapshot: () => Inputs;
  saveDraft: (snapshot: Inputs) => void;
  navigate: NavigateFunction;
};

export function useDraftNavigation({
  captureSnapshot,
  saveDraft,
  navigate,
}: UseDraftNavigationArgs) {
  return useCallback(
    (path: string, options?: DraftNavigateOptions) => {
      const snapshot = captureSnapshot();
      saveDraft(snapshot);
      navigate(path, {
        ...options,
        state: { ...options?.state, formData: snapshot },
      });
    },
    [captureSnapshot, navigate, saveDraft]
  );
}
