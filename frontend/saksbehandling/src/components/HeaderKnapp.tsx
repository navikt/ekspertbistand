import { ChevronDownIcon, ChevronUpIcon } from "@navikt/aksel-icons";
import { BodyShort, HStack, Loader } from "@navikt/ds-react";
import type { ComponentPropsWithRef, ReactNode } from "react";
import classes from "./HeaderKnapp.module.css";

type HeaderKnappProps = Omit<ComponentPropsWithRef<"button">, "children"> & {
  tekst: string;
  ikon?: ReactNode;
  åpen: boolean;
  loading?: boolean;
};

export default function HeaderKnapp({
  tekst,
  ikon,
  åpen,
  loading = false,
  className,
  ...rest
}: HeaderKnappProps) {
  return (
    <button
      type="button"
      {...rest}
      className={className ? `${classes.knapp} ${className}` : classes.knapp}
      aria-expanded={åpen}
      aria-busy={loading || undefined}
      disabled={loading || rest.disabled}
    >
      <HStack as="span" gap="space-8" align="center">
        {ikon}
        <BodyShort as="span" weight="semibold">
          {tekst}
        </BodyShort>
      </HStack>
      {loading ? (
        <Loader size="small" title="Lagrer" />
      ) : åpen ? (
        <ChevronUpIcon aria-hidden fontSize="1.5rem" />
      ) : (
        <ChevronDownIcon aria-hidden fontSize="1.5rem" />
      )}
    </button>
  );
}
