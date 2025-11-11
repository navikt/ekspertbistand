import { useEffect, useRef, useMemo } from "react";
import type { ReactNode } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { createEmptyInputs, soknadSchema, type SoknadInputs } from "../features/soknad/schema";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { useAutosaveDraft } from "../hooks/useAutosaveDraft";
import { DraftAutosaveContext } from "./DraftAutosaveContext";

type HydratedSnapshot = {
  id: string;
  snapshot: string;
};

export function SkjemaFormProvider({ children }: { children: ReactNode }) {
  const { draft, draftId, hydrated, saveDraft } = useSoknadDraft();
  const resolver = useMemo(() => zodResolver(soknadSchema), []);

  const form = useForm<SoknadInputs>({
    mode: "onSubmit",
    reValidateMode: "onSubmit",
    shouldFocusError: false,
    shouldUnregister: false,
    defaultValues: createEmptyInputs(),
    resolver,
  });

  const { isDirty } = form.formState;
  const appliedSnapshotRef = useRef<HydratedSnapshot | null>(null);

  useEffect(() => {
    if (!hydrated) return;
    const snapshot = JSON.stringify(draft);
    const applied = appliedSnapshotRef.current;
    const hasAppliedSnapshot = applied?.id === draftId && applied.snapshot === snapshot;
    if (hasAppliedSnapshot) return;
    if (isDirty && applied?.id === draftId) return;
    form.reset(draft);
    appliedSnapshotRef.current = { id: draftId, snapshot };
  }, [draft, draftId, form, hydrated, isDirty]);

  const { flushDraft } = useAutosaveDraft(form, hydrated, saveDraft);

  const autosaveContextValue = useMemo(() => ({ flushDraft }), [flushDraft]);

  return (
    <DraftAutosaveContext.Provider value={autosaveContextValue}>
      <FormProvider {...form}>{children}</FormProvider>
    </DraftAutosaveContext.Provider>
  );
}
