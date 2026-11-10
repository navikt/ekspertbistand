import { createContext, useContext } from "react";
import type { SoknadInputs } from "../features/soknad/schema";
import type { DraftDto } from "../features/soknad/payload";

export type BekreftetDeltaker = {
  fnr: string;
  virksomhetsnummer: string;
};

export type SoknadDraftContextValue = {
  draftId: string;
  draft: SoknadInputs;
  hydrated: boolean;
  status: DraftDto["status"] | null;
  saveDraft: (snapshot: SoknadInputs) => void;
  clearDraft: () => Promise<void>;
  lastPersistedAt: Date | null;
  saveError: string | null;
  deltakerBekreftet: boolean;
  bekreftetDeltaker: BekreftetDeltaker | null;
  opprettetAv: string | null;
  oppdaterUtkast: (utkast: DraftDto) => Promise<void>;
  lastUtkastPaNytt: () => Promise<void>;
};

export const SoknadDraftContext = createContext<SoknadDraftContextValue | undefined>(undefined);

export function useSoknadDraft() {
  const ctx = useContext(SoknadDraftContext);
  if (!ctx) throw new Error("useSoknadDraft must be used inside SoknadDraftProvider");
  return ctx;
}
