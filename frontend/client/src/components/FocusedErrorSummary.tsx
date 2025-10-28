import { ErrorSummary } from "@navikt/ds-react";
import { useEffect, useRef } from "react";
import type { ComponentPropsWithoutRef } from "react";

type FocusedErrorSummaryProps = {
  isActive: boolean;
  focusKey?: unknown;
} & ComponentPropsWithoutRef<typeof ErrorSummary>;

export function FocusedErrorSummary({ isActive, focusKey, ...props }: FocusedErrorSummaryProps) {
  const errorSummaryRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isActive) return;
    errorSummaryRef.current?.focus();
  }, [focusKey, isActive]);

  return <ErrorSummary ref={errorSummaryRef} {...props} />;
}
