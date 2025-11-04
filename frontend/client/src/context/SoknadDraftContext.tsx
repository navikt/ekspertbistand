import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { createEmptyInputs, type Inputs } from "../pages/types";
import { buildDraftPayload, draftDtoToInputs, type DraftDto } from "../utils/soknadPayload";
import { SKJEMA_API_PATH } from "../utils/constants";

type DraftContextValue = {
  draftId: string;
  draft: Inputs;
  hydrated: boolean;
  saveDraft: (snapshot: Inputs) => void;
  clearDraft: () => Promise<void>;
  lastPersistedAt: Date | null;
};

const SoknadDraftContext = createContext<DraftContextValue | undefined>(undefined);
const PERSIST_DELAY = 800;

const persistDraft = (draftId: string, data: Inputs): Promise<void> =>
  fetch(`${SKJEMA_API_PATH}/${draftId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(buildDraftPayload(data)),
  })
    .then(() => undefined)
    .catch(() => undefined);

const deleteDraft = (draftId: string): Promise<void> =>
  fetch(`${SKJEMA_API_PATH}/${draftId}`, {
    method: "DELETE",
  })
    .then(() => undefined)
    .catch(() => undefined);

export function SoknadDraftProvider({
  draftId,
  children,
}: {
  draftId: string;
  children: React.ReactNode;
}) {
  const [draft, setDraft] = useState<Inputs>(createEmptyInputs);
  const [hydrated, setHydrated] = useState(false);
  const [lastPersistedAt, setLastPersistedAt] = useState<Date | null>(null);

  const debounceRef = useRef<number | null>(null);
  const lastPersistedRef = useRef<string | null>(null);
  const lastSavedRef = useRef<string | null>(null);
  const clearingRef = useRef(false);
  const activePersistRef = useRef<Promise<void> | null>(null);

  const cancelPendingPersist = useCallback(() => {
    if (debounceRef.current !== null) {
      window.clearTimeout(debounceRef.current);
      debounceRef.current = null;
    }
  }, []);

  const schedulePersist = useCallback(
    (data: Inputs) => {
      const snapshot = JSON.stringify(data);
      if (snapshot === lastPersistedRef.current) return;

      cancelPendingPersist();
      debounceRef.current = window.setTimeout(() => {
        debounceRef.current = null;
        lastPersistedRef.current = snapshot;
        const persistPromise = persistDraft(draftId, data).then(() => {
          if (!clearingRef.current) {
            setLastPersistedAt(new Date());
          }
        });
        activePersistRef.current = persistPromise.finally(() => {
          if (activePersistRef.current === persistPromise) {
            activePersistRef.current = null;
          }
        });
      }, PERSIST_DELAY);
    },
    [cancelPendingPersist, draftId]
  );

  useEffect(() => {
    setHydrated(false);
    setDraft(createEmptyInputs);
    setLastPersistedAt(null);
    lastPersistedRef.current = null;
    lastSavedRef.current = null;
    activePersistRef.current = null;
    clearingRef.current = false;

    const controller = new AbortController();
    let active = true;
    (async () => {
      try {
        const res = await fetch(`${SKJEMA_API_PATH}/${draftId}`, {
          headers: { Accept: "application/json" },
          signal: controller.signal,
        });
        if (!active || controller.signal.aborted) return;
        if (res.ok) {
          const payload = (await res.json()) as DraftDto | null;
          const merged = draftDtoToInputs(payload);
          const snapshot = JSON.stringify(merged);
          setDraft(merged);
          lastPersistedRef.current = snapshot;
          lastSavedRef.current = snapshot;
          const persistedIso = payload?.opprettetTidspunkt ?? payload?.innsendtTidspunkt ?? null;
          setLastPersistedAt(persistedIso ? new Date(persistedIso) : new Date());
        }
      } catch (error) {
        if (import.meta.env.DEV && !controller.signal.aborted) {
          console.warn("Failed to fetch persisted draft, continuing without cached data", error);
        }
      } finally {
        if (active && !controller.signal.aborted) setHydrated(true);
      }
    })();

    return () => {
      active = false;
      cancelPendingPersist();
      controller.abort();
    };
  }, [cancelPendingPersist, draftId]);

  const saveDraft = useCallback(
    (snapshot: Inputs) => {
      if (clearingRef.current) return;
      const snapshotJson = JSON.stringify(snapshot);
      if (snapshotJson !== lastSavedRef.current) {
        setDraft(snapshot);
        lastSavedRef.current = snapshotJson;
      }
      schedulePersist(snapshot);
    },
    [schedulePersist]
  );

  const clearDraft = useCallback(async () => {
    clearingRef.current = true;
    cancelPendingPersist();
    setDraft(createEmptyInputs);
    lastPersistedRef.current = null;
    lastSavedRef.current = null;
    const pendingPersist = activePersistRef.current;
    if (pendingPersist) {
      try {
        await pendingPersist;
      } catch {
        // ignore persist errors while clearing
      }
    }
    try {
      await deleteDraft(draftId);
      setLastPersistedAt(new Date());
    } finally {
      clearingRef.current = false;
    }
  }, [cancelPendingPersist, draftId]);

  const value = useMemo<DraftContextValue>(
    () => ({
      draftId,
      draft,
      hydrated,
      saveDraft,
      clearDraft,
      lastPersistedAt,
    }),
    [clearDraft, draft, draftId, hydrated, lastPersistedAt, saveDraft]
  );

  return <SoknadDraftContext.Provider value={value}>{children}</SoknadDraftContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSoknadDraft() {
  const ctx = useContext(SoknadDraftContext);
  if (!ctx) throw new Error("useSoknadDraft must be used inside SoknadDraftProvider");
  return ctx;
}
