import { useCallback, useState } from "react";

type InitialValue = number | (() => number);

export const useErrorFocus = (initialValue: InitialValue = 0) => {
  const [focusKey, setFocusKey] = useState(initialValue);

  const bumpFocusKey = useCallback(() => {
    setFocusKey((key) => key + 1);
  }, []);

  return { focusKey, bumpFocusKey } as const;
};
