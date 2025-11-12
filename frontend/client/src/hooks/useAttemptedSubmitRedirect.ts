import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import type { FieldPath, UseFormReturn } from "react-hook-form";
import type { SoknadInputs } from "../features/soknad/schema";

type AttemptedSubmitState = {
  attemptedSubmit?: boolean;
} | null;

type AttemptedSubmitOptions = {
  fields?: ReadonlyArray<FieldPath<SoknadInputs>>;
  onValidationFailed?: () => void;
};

export const useAttemptedSubmitRedirect = (
  form: UseFormReturn<SoknadInputs>,
  options?: AttemptedSubmitOptions
) => {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const state = (location.state as AttemptedSubmitState) ?? null;
    if (!state?.attemptedSubmit) return;
    const run = async () => {
      const valid = await form.trigger(options?.fields);
      if (!valid) {
        options?.onValidationFailed?.();
      }
      navigate(location.pathname, { replace: true, state: null });
    };
    void run();
  }, [form, location.pathname, location.state, navigate, options]);
};
