import { useCallback } from "react";
import type { NavigateFunction } from "react-router-dom";
import { useFormContext } from "react-hook-form";
import type { Inputs } from "./types";
import { useSoknadDraft } from "../context/SoknadDraftContext";

export function useDraftNavigation({ navigate }: { navigate: NavigateFunction }) {
  const form = useFormContext<Inputs>();
  const { saveDraft } = useSoknadDraft();
  return useCallback(
    (path: string, options?: Parameters<NavigateFunction>[1]) => {
      const snapshot = form.getValues();
      saveDraft(snapshot);
      navigate(path, options);
    },
    [form, navigate, saveDraft]
  );
}
