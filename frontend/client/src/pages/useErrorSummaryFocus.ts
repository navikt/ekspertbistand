import { useEffect, type RefObject } from "react";

export function focusErrorSummary<ElementType extends HTMLDivElement>(ref: RefObject<ElementType>) {
  ref.current?.focus();
}

type UseErrorSummaryFocusArgs<ElementType extends HTMLDivElement> = {
  ref: RefObject<ElementType>;
  isActive: boolean;
  dependencies?: ReadonlyArray<unknown>;
};

const EMPTY_DEPS: ReadonlyArray<unknown> = [];

export function useErrorSummaryFocus<ElementType extends HTMLDivElement>({
  ref,
  isActive,
  dependencies,
}: UseErrorSummaryFocusArgs<ElementType>) {
  const dependencyList = dependencies ?? EMPTY_DEPS;

  useEffect(() => {
    if (!isActive) return;
    focusErrorSummary(ref);
  }, [dependencyList, isActive, ref]);
}
