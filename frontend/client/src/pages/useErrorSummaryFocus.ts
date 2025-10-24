import { useEffect, type RefObject } from "react";

export const focusErrorSummary = <ElementType extends HTMLDivElement>(
  ref: RefObject<ElementType>
) => {
  const focus = () => {
    ref.current?.focus();
  };

  if (typeof window !== "undefined" && typeof window.requestAnimationFrame === "function") {
    window.requestAnimationFrame(focus);
    return;
  }

  focus();
};

type UseErrorSummaryFocusArgs<ElementType extends HTMLDivElement> = {
  ref: RefObject<ElementType>;
  isActive: boolean;
  dependencies?: ReadonlyArray<unknown>;
};

const EMPTY_DEPS: ReadonlyArray<unknown> = [];

export const useErrorSummaryFocus = <ElementType extends HTMLDivElement>({
  ref,
  isActive,
  dependencies,
}: UseErrorSummaryFocusArgs<ElementType>) => {
  const dependencyList = dependencies ?? EMPTY_DEPS;

  useEffect(() => {
    if (!isActive) return;
    focusErrorSummary(ref);
  }, [isActive, ref, ...dependencyList]);
};
