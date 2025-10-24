import type { CSSProperties, MouseEvent } from "react";

export const DEFAULT_LANGUAGE_LINKS = [
  { locale: "nb", url: "https://www.nav.no" },
  { locale: "en", url: "https://www.nav.no/en" },
];

export const FORM_COLUMN_STYLE: CSSProperties = { width: "100%", maxWidth: "36rem" };

export const withPreventDefault =
  <ElementType extends HTMLElement = HTMLElement>(action: () => void) =>
  (event: MouseEvent<ElementType>) => {
    event.preventDefault();
    action();
  };
