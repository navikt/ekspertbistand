import React, { useId } from "react";
import { BodyShort, ErrorMessage, Label, Loader, VStack } from "@navikt/ds-react";
import {
  Virksomhetsvelger as NavVirksomhetsvelger,
  type Organisasjon,
} from "@navikt/virksomhetsvelger";
import "@navikt/virksomhetsvelger/dist/assets/style.css";
import { useOrganisasjoner } from "../hooks/useOrganisasjoner";

type VirksomhetPickerProps = {
  id?: string;
  ref?: React.Ref<HTMLButtonElement>;
  label: React.ReactNode;
  value: string;
  onChange: (organisasjonsnummer: string, virksomhet?: Organisasjon) => void;
  error?: React.ReactNode;
  description?: React.ReactNode;
};

export function VirksomhetVelger({
  id,
  ref,
  label,
  value,
  onChange,
  error,
  description,
}: VirksomhetPickerProps) {
  const { organisasjoner, isLoading } = useOrganisasjoner();
  const errorId = useId();

  return (
    <VStack gap="space-8">
      <Label>{label}</Label>
      {description ? <BodyShort size="small">{description}</BodyShort> : null}
      {isLoading && (
        <BodyShort size="small">
          <Loader size="small" title="Laster virksomheter" aria-live="polite" /> Laster virksomheter
          ...
        </BodyShort>
      )}
      {organisasjoner.length > 0 ? (
        <NavVirksomhetsvelger
          ref={ref}
          id={id}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? errorId : undefined}
          organisasjoner={organisasjoner}
          initValgtOrgnr={value || undefined}
          onChange={(org) => {
            onChange(org.orgnr, org);
          }}
          friKomponent
        />
      ) : null}
      {error ? <ErrorMessage id={errorId}>{error}</ErrorMessage> : null}
    </VStack>
  );
}
