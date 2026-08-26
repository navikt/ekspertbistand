import { useMemo, useState } from "react";
import { UNSAFE_Combobox } from "@navikt/ds-react";
import { useEkspertVirksomhetSok } from "../hooks/useEkspertVirksomhetSok";

type EkspertVirksomhetVelgerProps = {
  label: React.ReactNode;
  description?: React.ReactNode;
  /** Fritekstverdien (bevart for bakoverkompatibilitet). */
  value: string;
  /**
   * Kalles ved endring. [virksomhet] er den (berikede) fritekstverdien, og [organisasjon]
   * er satt når bruker velger et søketreff, ellers null (fri inntasting eller fjernet valg).
   */
  onChange: (
    virksomhet: string,
    organisasjon: { navn: string; orgnr: string } | null
  ) => void;
  error?: React.ReactNode;
};

/** Beriker fritekstfeltet med navn + organisasjonsnummer, f.eks. «Ekspert & Co AS (910825226)». */
const formaterVirksomhet = (navn: string, orgnr: string) => `${navn} (${orgnr})`;

export function EkspertVirksomhetVelger({
  label,
  description,
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
          onChange("", null);
          return;
        }
        const valgt = organisasjoner.find((org) => org.organisasjonsnummer === option);
        if (valgt) {
          onChange(formaterVirksomhet(valgt.navn, valgt.organisasjonsnummer), {
            navn: valgt.navn,
            orgnr: valgt.organisasjonsnummer,
          });
        } else {
          // Fri inntasting: behold fritekst, ingen strukturert organisasjon.
          onChange(option, null);
        }
      }}
      error={error}
    />
  );
}
