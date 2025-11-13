import React from "react";
import { BodyShort, ErrorMessage, Label, Loader, VStack } from "@navikt/ds-react";
import {
  Virksomhetsvelger as NavVirksomhetsvelger,
  type Organisasjon,
} from "@navikt/virksomhetsvelger";
import "@navikt/virksomhetsvelger/dist/assets/style.css";
import { useOrganisasjoner } from "../hooks/useOrganisasjoner";

type VirksomhetPickerProps = {
  label: React.ReactNode;
  value: string;
  onChange: (organisasjonsnummer: string, virksomhet?: Organisasjon) => void;
  error?: React.ReactNode;
};

export function VirksomhetVelger({ label, value, onChange, error }: VirksomhetPickerProps) {
  const { organisasjoner, isLoading } = useOrganisasjoner();

  return (
    <VStack gap="1">
      <Label>{label}</Label>
      {isLoading && (
        <BodyShort size="small">
          <Loader size="small" title="Laster virksomheter" aria-live="polite" /> Laster virksomheter
          ...
        </BodyShort>
      )}
      {organisasjoner.length > 0 ? (
        <NavVirksomhetsvelger
          organisasjoner={organisasjoner}
          initValgtOrgnr={value || undefined}
          onChange={(org) => {
            onChange(org.orgnr, org);
          }}
          friKomponent
        />
      ) : null}
      {error ? <ErrorMessage>{error}</ErrorMessage> : null}
    </VStack>
  );
}
