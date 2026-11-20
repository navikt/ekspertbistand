import { FileTextIcon } from "@navikt/aksel-icons";
import { ExpansionCard, HStack } from "@navikt/ds-react";
import { type SoknadInputs } from "../features/soknad/schema";
import { KvitteringSummary } from "./KvitteringSummary";

type SoknadSendtInnKortProps = {
  soknad: SoknadInputs;
  saksnummer?: string;
  innsendtTekst: string | null;
};

export function SoknadSendtInnKort({ soknad, saksnummer, innsendtTekst }: SoknadSendtInnKortProps) {
  return (
    <ExpansionCard size="small" aria-label="Søknad sendt inn">
      <ExpansionCard.Header>
        <HStack wrap={false} gap="space-16" align="center">
          <FileTextIcon aria-hidden fontSize="2rem" />
          <div>
            <ExpansionCard.Title as="h2" size="small">
              Søknad sendt inn
            </ExpansionCard.Title>
            {innsendtTekst && (
              <ExpansionCard.Description>{innsendtTekst}</ExpansionCard.Description>
            )}
          </div>
        </HStack>
      </ExpansionCard.Header>
      <ExpansionCard.Content>
        <KvitteringSummary data={soknad} saksnummer={saksnummer} innsendtTekst={innsendtTekst} />
      </ExpansionCard.Content>
    </ExpansionCard>
  );
}
