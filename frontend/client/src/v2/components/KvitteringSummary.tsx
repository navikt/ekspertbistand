import { FormSummary, VStack } from "@navikt/ds-react";
import { type SoknadInputs } from "../features/soknad/schema";
import {
  formatCurrency,
  formatDate,
  formatStringArray,
  formatTimer,
  formatValue,
} from "./summaryFormatters";

type KvitteringSummaryProps = {
  data: SoknadInputs;
  saksnummer?: string;
  innsendtTekst: string | null;
  headingLevel?: "2" | "3";
};

export function KvitteringSummary({
  data,
  saksnummer,
  innsendtTekst,
  headingLevel = "2",
}: KvitteringSummaryProps) {
  return (
    <VStack gap="space-32">
      <FormSummary>
        <FormSummary.Header>
          <FormSummary.Heading level={headingLevel}>Søknad</FormSummary.Heading>
        </FormSummary.Header>
        <FormSummary.Answers>
          <FormSummary.Answer>
            <FormSummary.Label>Saksnummer</FormSummary.Label>
            <FormSummary.Value>{formatValue(saksnummer)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Navn på virksomhet</FormSummary.Label>
            <FormSummary.Value>{formatValue(data.virksomhet.virksomhetsnavn)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Organisasjonsnummer</FormSummary.Label>
            <FormSummary.Value>{formatValue(data.virksomhet.virksomhetsnummer)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Beliggenhetsadresse</FormSummary.Label>
            <FormSummary.Value>
              {formatValue(data.virksomhet.beliggenhetsadresse)}
            </FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Kontaktperson i virksomheten</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(data.virksomhet.kontaktperson.navn)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>E-post</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(data.virksomhet.kontaktperson.epost)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Telefon</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(data.virksomhet.kontaktperson.telefonnummer)}
                  </FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Ansatt</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>{formatValue(data.ansatt.navn)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Fødselsnummer</FormSummary.Label>
                  <FormSummary.Value>{formatValue(data.ansatt.fnr)}</FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Ekspert</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>{formatValue(data.ekspert.navn)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Tilknyttet virksomhet</FormSummary.Label>
                  <FormSummary.Value>{formatValue(data.ekspert.virksomhet)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>
                    Offentlig godkjent utdanning eller autorisasjon
                  </FormSummary.Label>
                  <FormSummary.Value>
                    {formatStringArray(data.ekspert.godkjentUtdanningEllerAutorisasjon)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Relevant kompetanse for denne saken</FormSummary.Label>
                  <FormSummary.Value>
                    {formatStringArray(data.ekspert.relevantKompetanse)}
                  </FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Hvem i Nav har du drøftet ekspertbistand med?</FormSummary.Label>
            <FormSummary.Value>{formatValue(data.nav.kontaktperson)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Beskriv den ansattes arbeidssituasjon</FormSummary.Label>
            <FormSummary.Value>{formatValue(data.behovForBistand.begrunnelse)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Hva vil dere ha hjelp til fra eksperten?</FormSummary.Label>
            <FormSummary.Value>{formatValue(data.behovForBistand.behov)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>
              Hvilke tiltak for tilrettelegging har dere vurdert eller forsøkt?
            </FormSummary.Label>
            <FormSummary.Value>
              {formatValue(data.behovForBistand.tilrettelegging)}
            </FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Hvor mange timer skal eksperten hjelpe dere?</FormSummary.Label>
            <FormSummary.Value>{formatTimer(data.behovForBistand.timer)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Søknadssum</FormSummary.Label>
            <FormSummary.Value>
              {formatCurrency(data.behovForBistand.estimertKostnad)}
            </FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Startdato</FormSummary.Label>
            <FormSummary.Value>{formatDate(data.behovForBistand.startdato)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Sendt inn til Nav</FormSummary.Label>
            <FormSummary.Value>{formatValue(innsendtTekst)}</FormSummary.Value>
          </FormSummary.Answer>
        </FormSummary.Answers>
      </FormSummary>
    </VStack>
  );
}
