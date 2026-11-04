import { useEffect, useRef } from "react";
import { ErrorSummary } from "@navikt/ds-react";
import { get, type FieldErrors, type FieldValues, type Path } from "react-hook-form";

type SummaryItem = {
  id: string;
  message: string;
  href?: string;
};

type FormErrorSummaryProps<TFieldValues extends FieldValues> = {
  errors: FieldErrors<TFieldValues>;
  fields: ReadonlyArray<Path<TFieldValues>>;
  heading: string;
  extraItems?: ReadonlyArray<SummaryItem>;
  focusKey?: unknown;
};

const EMPTY_ITEMS: ReadonlyArray<SummaryItem> = [];

const buildSummaryItems = <TFieldValues extends FieldValues>(
  errors: FieldErrors<TFieldValues>,
  fields: ReadonlyArray<Path<TFieldValues>>
): SummaryItem[] =>
  fields.reduce<SummaryItem[]>((items, field) => {
    const fieldError = get(errors, field);
    const message = typeof fieldError?.message === "string" ? fieldError.message : null;
    if (message) {
      items.push({ id: field, message });
    }
    return items;
  }, []);

export function FormErrorSummary<TFieldValues extends FieldValues>({
  errors,
  fields,
  heading,
  extraItems = EMPTY_ITEMS,
  focusKey,
}: FormErrorSummaryProps<TFieldValues>) {
  const summaryItems = [...buildSummaryItems(errors, fields), ...extraItems];
  const summaryRef = useRef<HTMLDivElement | null>(null);
  const serializedItems = summaryItems.map(({ id, message }) => `${id}:${message}`).join("|");

  useEffect(() => {
    if (summaryItems.length === 0) return;
    summaryRef.current?.focus();
  }, [serializedItems, summaryItems.length, focusKey]);

  if (summaryItems.length === 0) return null;

  return (
    <ErrorSummary heading={heading} ref={summaryRef}>
      {summaryItems.map(({ id, message, href }) => (
        <ErrorSummary.Item key={id} href={href ?? `#${id}`}>
          {message}
        </ErrorSummary.Item>
      ))}
    </ErrorSummary>
  );
}
