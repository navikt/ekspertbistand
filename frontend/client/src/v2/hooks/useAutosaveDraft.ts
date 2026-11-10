import { useCallback, useEffect, useRef } from "react";
import { type UseFormReturn } from "react-hook-form";
import type { SoknadInputs } from "../features/soknad/schema";

const AUTOSAVE_DELAY = 800;

export const useAutosaveDraft = (
  form: UseFormReturn<SoknadInputs>,
  enabled: boolean,
  saveDraft: (snapshot: SoknadInputs) => void
): { flushDraft: () => boolean } => {
  const lastSnapshotRef = useRef<string>(JSON.stringify(form.getValues()));
  const timerRef = useRef<number | null>(null);

  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const persistSnapshot = useCallback((): boolean => {
    if (!enabled) return false;
    const values = form.getValues();
    const snapshot = JSON.stringify(values);
    if (snapshot === lastSnapshotRef.current) return false;
    lastSnapshotRef.current = snapshot;
    saveDraft(values);
    return true;
  }, [enabled, form, saveDraft]);

  const schedulePersist = useCallback(() => {
    if (!enabled) return;
    clearTimer();
    timerRef.current = window.setTimeout(() => {
      timerRef.current = null;
      persistSnapshot();
    }, AUTOSAVE_DELAY);
  }, [clearTimer, enabled, persistSnapshot]);

  useEffect(() => {
    if (!enabled) {
      clearTimer();
      lastSnapshotRef.current = JSON.stringify(form.getValues());
      return;
    }
    const subscription = form.watch(() => {
      schedulePersist();
    });
    return () => {
      subscription.unsubscribe();
    };
  }, [clearTimer, enabled, form, schedulePersist]);

  const flushDraft = useCallback((): boolean => {
    clearTimer();
    return persistSnapshot();
  }, [clearTimer, persistSnapshot]);

  return { flushDraft };
};
