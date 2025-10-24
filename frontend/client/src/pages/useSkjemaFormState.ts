import { useCallback, useEffect, useRef } from "react";
import type { Location, NavigateFunction } from "react-router-dom";
import type { UseFormReturn } from "react-hook-form";
import { createEmptyInputs, mergeInputs, type Inputs } from "./types";

type LocationState = { formData?: Partial<Inputs> } | null;

export function useSkjemaFormState(
  form: UseFormReturn<Inputs>,
  location: Location,
  navigate: NavigateFunction
) {
  const latestValuesRef = useRef<Inputs>(createEmptyInputs());

  const mergeValues = useCallback((partial: Partial<Inputs>) => {
    latestValuesRef.current = mergeInputs(latestValuesRef.current, partial);
    return latestValuesRef.current;
  }, []);

  useEffect(() => {
    mergeValues(form.getValues());
  }, [form, mergeValues]);

  useEffect(() => {
    const state = (location.state as LocationState)?.formData;
    if (!state) return;

    const merged = mergeValues(state);
    form.reset(merged, { keepValues: false });
    navigate(location.pathname, { replace: true, state: null });
  }, [form, location.pathname, location.state, mergeValues, navigate]);

  const captureSnapshot = useCallback(() => mergeValues(form.getValues()), [form, mergeValues]);

  return { captureSnapshot, mergeValues };
}
