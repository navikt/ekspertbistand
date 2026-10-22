import { BodyShort, HStack, Select, UNSAFE_Combobox, VStack } from "@navikt/ds-react";
import type { Kommunefilter as Filter, KommuneValg } from "../hooks/useKommunefilter";
import type { AnsattEnhet } from "../mock/ansatt";
import classes from "./Kommunefilter.module.css";

type Props = {
  enheter: ReadonlyArray<AnsattEnhet>;
  filter: Filter;
  kommuneValg: KommuneValg[];
  skjulte: number;
  onEndre: (filter: Filter) => void;
};

export function Kommunefilter({ enheter, filter, kommuneValg, skjulte, onEndre }: Props) {
  if (enheter.length < 2 && kommuneValg.length === 0) return null;

  return (
    <VStack gap="space-8">
      <HStack gap="space-16" align="end">
        {enheter.length > 1 && (
          <Select
            label="Enhet"
            size="small"
            className={classes.enhet}
            value={filter.enhet}
            onChange={(e) => onEndre({ enhet: e.target.value, kommuner: [] })}
          >
            {enheter.map((enhet) => (
              <option key={enhet.nummer} value={enhet.nummer}>
                {enhet.navn}
              </option>
            ))}
          </Select>
        )}
        {kommuneValg.length > 0 && (
          <UNSAFE_Combobox
            label="Kommune"
            size="small"
            className={classes.kommune}
            isMultiSelect
            options={kommuneValg}
            selectedOptions={kommuneValg.filter((k) => filter.kommuner.includes(k.value))}
            onToggleSelected={(nummer, valgt) =>
              onEndre({
                ...filter,
                kommuner: valgt
                  ? [...filter.kommuner, nummer]
                  : filter.kommuner.filter((k) => k !== nummer),
              })
            }
          />
        )}
      </HStack>
      {skjulte > 0 && (
        <BodyShort size="small" textColor="subtle">
          {skjulte === 1 ? "1 sak skjult" : `${skjulte} saker skjult`} av kommunefilteret
        </BodyShort>
      )}
    </VStack>
  );
}
