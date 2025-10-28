import { useEffect, useRef } from "react";
import type { ReactNode } from "react";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import type { Inputs } from "../pages/types.ts";
import { useSoknadDraft } from "../context/SoknadDraftContext.tsx";

export function SkjemaFormProvider({ children }: { children: ReactNode }) {
  const { draft, hydrated, saveDraft } = useSoknadDraft();
  const form = useForm<Inputs>({
    mode: "onSubmit",
    reValidateMode: "onSubmit",
    shouldFocusError: false,
    shouldUnregister: false,
  });

  const hasInitialisedRef = useRef(false);

  useEffect(() => {
    if (!hydrated || hasInitialisedRef.current) return;
    form.reset(draft, { keepValues: false });
    hasInitialisedRef.current = true;
  }, [draft, form, hydrated]);

  const values = useWatch<Inputs>({ control: form.control });
  const { getValues } = form;

  useEffect(() => {
    if (!hydrated) return;
    saveDraft(getValues());
  }, [getValues, hydrated, saveDraft, values]);

  return <FormProvider {...form}>{children}</FormProvider>;
}
