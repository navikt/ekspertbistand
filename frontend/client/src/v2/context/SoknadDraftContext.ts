import { createContext, useContext } from "react";
import type { SoknadInputs } from "../features/soknad/schema";
import type { DraftDto } from "../features/soknad/payload";

export type BekreftetDeltaker = {
  fnr: string;
  virksomhetsnummer: string;
};

export type SoknadDraftContextValue = {
  /** Null i steg 1 før utkastet er opprettet. Utkastet opprettes først når deltakeren er bekreftet. */
  draftId: string | null;
  draft: SoknadInputs;
  hydrated: boolean;
  status: DraftDto["status"] | null;
  saveDraft: (snapshot: SoknadInputs) => void;
  /**
   * Venter til den siste lagringen er ferdig. Feilet den, prøver den én gang til.
   * Gir false hvis utkastet ikke er lagret.
   */
  ventPaLagring: () => Promise<boolean>;
  clearDraft: () => Promise<void>;
  lastPersistedAt: Date | null;
  saveError: string | null;
  bekreftetDeltaker: BekreftetDeltaker | null;
  opprettetAv: string | null;
  oppdaterUtkast: (utkast: DraftDto) => Promise<void>;
};

export const SoknadDraftContext = createContext<SoknadDraftContextValue | undefined>(undefined);

export function useSoknadDraft() {
  const ctx = useContext(SoknadDraftContext);
  if (!ctx) throw new Error("useSoknadDraft must be used inside SoknadDraftProvider");
  return ctx;
}

/** For sidene etter steg 1. Der finnes utkastet alltid. */
export function useLagretUtkastId(): string {
  const { draftId } = useSoknadDraft();
  if (!draftId) throw new Error("Siden krever et lagret utkast");
  return draftId;
}
