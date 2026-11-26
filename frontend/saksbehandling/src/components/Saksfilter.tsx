import { BodyShort, HStack, UNSAFE_Combobox, VStack } from "@navikt/ds-react";
import type { KommuneValg, Saksfilter as Filter } from "../hooks/useSaksfilter";
import type { Virksomhetstype } from "../hooks/useSaker";
import { virksomhetstypeVisning } from "../hooks/useVirksomhetstypefilter";
import type { AnsattEnhet } from "../mock/ansatt";
import classes from "./Saksfilter.module.css";

type Props = {
  enheter: ReadonlyArray<AnsattEnhet>;
  filter: Filter;
  kommuneValg: KommuneValg[];
  harSaker: boolean;
  skjulte: number;
  onEndre: (filter: Filter) => void;
};

const virksomhetstypeValg = Object.entries(virksomhetstypeVisning).map(([value, { tekst }]) => ({
  label: tekst,
  value: value as Virksomhetstype,
}));

export function Saksfilter({ enheter, filter, kommuneValg, harSaker, skjulte, onEndre }: Props) {
  if (enheter.length < 2 && kommuneValg.length === 0 && !harSaker) return null;

  return (
    <VStack gap="space-8">
      <HStack gap="space-16" align="end">
        {enheter.length > 1 && (
          <UNSAFE_Combobox
            label="Enhet"
            size="small"
            className={classes.enhet}
            options={enheter.map((enhet) => ({ label: enhet.navn, value: enhet.nummer }))}
            selectedOptions={enheter
              .filter((enhet) => enhet.nummer === filter.enhet)
              .map((enhet) => ({ label: enhet.navn, value: enhet.nummer }))}
            onToggleSelected={(nummer, valgt) => {
              if (valgt && nummer !== filter.enhet)
                onEndre({ ...filter, enhet: nummer, kommuner: [] });
            }}
          />
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
        {harSaker && (
          <UNSAFE_Combobox
            label="Type virksomhet"
            size="small"
            className={classes.virksomhetstype}
            isMultiSelect
            options={virksomhetstypeValg}
            selectedOptions={virksomhetstypeValg.filter((v) =>
              filter.virksomhetstyper.includes(v.value)
            )}
            onToggleSelected={(verdi, valgt) => {
              const type = verdi as Virksomhetstype;
              onEndre({
                ...filter,
                virksomhetstyper: valgt
                  ? [...filter.virksomhetstyper, type]
                  : filter.virksomhetstyper.filter((v) => v !== type),
              });
            }}
          />
        )}
      </HStack>
      {skjulte > 0 && (
        <BodyShort size="small" textColor="subtle">
          {skjulte === 1 ? "1 sak skjult" : `${skjulte} saker skjult`} av filteret
        </BodyShort>
      )}
    </VStack>
  );
}
