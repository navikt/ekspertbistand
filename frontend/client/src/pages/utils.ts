import type { CSSProperties, MouseEvent } from "react";

export const FORM_COLUMN_STYLE: CSSProperties = { width: "100%", maxWidth: "36rem" };

export const withPreventDefault =
  <ElementType extends HTMLElement = HTMLElement>(action: () => void) =>
  (event: MouseEvent<ElementType>) => {
    event.preventDefault();
    action();
  };
