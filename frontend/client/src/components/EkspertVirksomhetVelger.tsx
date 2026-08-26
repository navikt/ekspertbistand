import { useMemo, useState } from "react";
import { UNSAFE_Combobox } from "@navikt/ds-react";
import { useEkspertVirksomhetSok } from "../hooks/useEkspertVirksomhetSok";

type EkspertVirksomhetVelgerProps = {
  label: React.ReactNode;
  description?: React.ReactNode;
  /** Fritekstverdien (bevart for bakoverkompatibilitet). */
  value: string;
  onChange: (virksomhet: string) => void;
  /** Kalles når bruker velger en organisasjon fra søkeforslagene. */
  onSelectOrganisasjon: (organisasjon: { navn: string; orgnr: string } | null) => void;
  error?: React.ReactNode;
};

export function EkspertVirksomhetVelger({
  label,
  description,
  value,
  onChange,
  onSelectOrganisasjon,
  error,
}: EkspertVirksomhetVelgerProps) {
  const [sokeord, setSokeord] = useState("");
  const { organisasjoner, isLoading } = useEkspertVirksomhetSok(sokeord);

  const options = useMemo(
    () =>
      organisasjoner.map((org) => ({
        label: `${org.navn} (Org.nr. ${org.organisasjonsnummer})`,
        value: org.organisasjonsnummer,
      })),
    [organisasjoner]
  );

  const selectedOptions = value ? [value] : [];

  return (
    <UNSAFE_Combobox
      id="ekspert.virksomhet"
      label={label}
      description={description}
      options={options}
      selectedOptions={selectedOptions}
      isLoading={isLoading}
      allowNewValues
      shouldAutocomplete
      onChange={(value) => setSokeord(value ?? "")}
      onToggleSelected={(option, isSelected) => {
        if (!isSelected) {
          onChange("");
          onSelectOrganisasjon(null);
          return;
        }
        const valgt = organisasjoner.find((org) => org.organisasjonsnummer === option);
        if (valgt) {
          onChange(valgt.navn);
          onSelectOrganisasjon({ navn: valgt.navn, orgnr: valgt.organisasjonsnummer });
        } else {
          // Fri inntasting: behold fritekst, ingen strukturert organisasjon.
          onChange(option);
          onSelectOrganisasjon(null);
        }
      }}
      error={error}
    />
  );
}
