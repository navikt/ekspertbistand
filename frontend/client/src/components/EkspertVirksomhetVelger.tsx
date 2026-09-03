import { useMemo, useState } from "react";
import { UNSAFE_Combobox } from "@navikt/ds-react";
import { useEkspertVirksomhetSok } from "../hooks/useEkspertVirksomhetSok";

type EkspertVirksomhetVelgerProps = {
  label: React.ReactNode;
  description?: React.ReactNode;
  /** Fritekstverdien (bevart for bakoverkompatibilitet). */
  value: string;
  /**
   * Kalles ved endring. [virksomhet] er den berikede fritekstverdien for et valgt treff,
   * og [organisasjon] er den valgte organisasjonen — begge tømmes (""/null) når valget fjernes.
   */
  onChange: (organisasjon: { navn: string; orgnr: string } | null) => void;
  error?: React.ReactNode;
};

/** Beriker fritekstfeltet med navn + organisasjonsnummer, f.eks. «Ekspert & Co AS (910825226)». */
export const formaterVirksomhet = (navn: string | null, orgnr: string | null) => {
  if (navn == null || orgnr == null) {
    return "";
  }
  return `${navn} (${orgnr})`;
};

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

  /**
   * Combobox sammenligner valgt verdi mot [value] i options-listen, som her er
   * organisasjonsnummeret. Sendes fritekstverdien inn som en ren streng, blir den
   * aldri gjenkjent som valgt (og et nytt klikk på treffet velger det på nytt i
   * stedet for å fjerne det).
   */
  const valgtOrgnr = value.match(/\((\d{9})\)\s*$/)?.[1];
  const selectedOptions = value ? [{ label: value, value: valgtOrgnr ?? value }] : [];

  return (
    <UNSAFE_Combobox
      id="ekspert.virksomhet"
      label={label}
      description={description}
      options={options}
      /**
       * Søket gjøres i Ereg. Uten [filteredOptions] filtrerer Combobox treffene på
       * nytt internt, med et enkelt «label.includes(det du har skrevet)». Da forsvinner
       * gyldige treff der Ereg matcher annerledes enn ren delstreng (annen ordstilling,
       * doble mellomrom i sammensattnavn, treff på tidligere navn), og listen blir tom
       * eller henger igjen på forrige søk mens man skriver.
       */
      filteredOptions={options}
      selectedOptions={selectedOptions}
      isLoading={isLoading}
      onChange={(sok) => setSokeord(sok ?? "")}
      onToggleSelected={(option, isSelected) => {
        if (!isSelected) {
          onChange(null);
          return;
        }
        const valgt = organisasjoner.find((org) => org.organisasjonsnummer === option);
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
