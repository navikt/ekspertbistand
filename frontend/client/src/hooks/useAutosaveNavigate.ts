import { useCallback } from "react";
import { useNavigate, type NavigateOptions, type To } from "react-router-dom";
import { useDraftAutosave } from "../context/DraftAutosaveContext";

export const useAutosaveNavigate = () => {
  const navigate = useNavigate();
  const { flushDraft } = useDraftAutosave();

  return useCallback(
    (to: To, options?: NavigateOptions, savedState?: Record<string, unknown>) => {
      flushDraft();
      if (!savedState) {
        navigate(to, options);
        return;
      }
      const baseState = options?.state && typeof options.state === "object" ? options.state : {};
      navigate(to, {
        ...options,
        state: {
          ...baseState,
          ...savedState,
        },
      });
    },
    [flushDraft, navigate]
  );
};
