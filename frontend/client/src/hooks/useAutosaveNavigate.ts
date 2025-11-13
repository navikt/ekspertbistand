import { useCallback } from "react";
import { useNavigate, type NavigateOptions, type To } from "react-router-dom";
import { useDraftAutosave } from "../context/DraftAutosaveContext";

export const useAutosaveNavigate = () => {
  const navigate = useNavigate();
  const { flushDraft } = useDraftAutosave();

  return useCallback(
    (to: To, options?: NavigateOptions) => {
      flushDraft();
      navigate(to, options);
    },
    [flushDraft, navigate]
  );
};
