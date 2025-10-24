import { useEffect, useRef } from "react";
import type { Inputs } from "./types";

type UseDraftAutosaveArgs = {
  captureSnapshot: () => Inputs;
  saveDraft: (snapshot: Inputs) => void;
  hydrated: boolean;
  dependencies?: ReadonlyArray<unknown>;
};

const EMPTY_DEPS: ReadonlyArray<unknown> = [];

export function useDraftAutosave({
  captureSnapshot,
  saveDraft,
  hydrated,
  dependencies,
}: UseDraftAutosaveArgs) {
  const lastSnapshotRef = useRef<string | null>(null);

  const dependencyList = dependencies ?? EMPTY_DEPS;

  useEffect(() => {
    if (!hydrated) return;
    const snapshot = captureSnapshot();
    const serialized = JSON.stringify(snapshot);
    if (serialized === lastSnapshotRef.current) return;
    lastSnapshotRef.current = serialized;
    saveDraft(snapshot);
  }, [captureSnapshot, hydrated, saveDraft, ...dependencyList]);
}
