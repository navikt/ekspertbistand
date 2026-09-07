import { useMemo, useState } from "react";
import { UNSAFE_Combobox } from "@navikt/ds-react";
import { useEkspertVirksomhetSok } from "../hooks/useEkspertVirksomhetSok";
import { formaterVirksomhet } from "./formaterVirksomhet";

type EkspertVirksomhetVelgerProps = {
  /** Fritekstverdien (bevart for bakoverkompatibilitet). */
  value: string;
  /**
   * Kalles ved endring. [virksomhet] er den berikede fritekstverdien for et valgt treff,
   * og [organisasjon] er den valgte organisasjonen — begge tømmes (""/null) når valget fjernes.
   */
  onChange: (organisasjon: { navn: string; orgnr: string } | null) => void;
  error?: React.ReactNode;
};

export function EkspertVirksomhetVelger({
  value,
  onChange,
  error,
}: EkspertVirksomhetVelgerProps) {
  const [sokeord, setSokeord] = useState("");
  const { organisasjoner, isLoading } = useEkspertVirksomhetSok(sokeord);

  const options = useMemo(
    () =>
      organisasjoner.map((org) => ({
        label: formaterVirksomhet(org.navn, org.organisasjonsnummer),
        value: formaterVirksomhet(org.navn, org.organisasjonsnummer),
      })),
    [organisasjoner]
  );

  const selectedOptions = value ? [value] : [];

  return (
      <UNSAFE_Combobox
        id="ekspert.virksomhet"
        label="Tilknyttet virksomhet"
        description="Søk på virksomhetsnavn eller skriv fullt organisasjonsnummer."
        options={options}
        filteredOptions={options}
        selectedOptions={selectedOptions}
        isLoading={isLoading}
        onChange={(value) => setSokeord(value ?? "")}
        onToggleSelected={(option, isSelected) => {
          if (!isSelected) {
            onChange(null);
            return;
          }
          const valgt = organisasjoner.find(
            (org) => formaterVirksomhet(org.navn, org.organisasjonsnummer) === option
          );
          if (valgt) {
            onChange({
              navn: valgt.navn,
              orgnr: valgt.organisasjonsnummer,
            });
          }
        }}
        error={error}
      />
  );
}
