import { createContext, useContext } from "react";

type DraftAutosaveContextValue = { flushDraft: () => boolean } | null;

export const DraftAutosaveContext = createContext<DraftAutosaveContextValue>(null);

export const useDraftAutosave = () => {
  const ctx = useContext(DraftAutosaveContext);
  if (!ctx) {
    throw new Error("useDraftAutosave must be used within SkjemaFormProvider");
  }
  return ctx;
};
