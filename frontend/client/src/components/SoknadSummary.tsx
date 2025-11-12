import { FormSummary } from "@navikt/ds-react";
import { FormSummaryAnswer } from "@navikt/ds-react/FormSummary";
import type { SoknadInputs } from "../features/soknad/schema";
import { parseIsoDate } from "../utils/dates";

const numberFormatter = new Intl.NumberFormat("nb-NO");

const formatValue = (value: unknown): string => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value.toString() : "—";
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : "—";
  }
  return "—";
};

const formatCurrency = (value: SoknadInputs["behovForBistand"]["estimertKostnad"]): string => {
  if (typeof value === "number" && Number.isFinite(value)) {
    return `${numberFormatter.format(value)} kr`;
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      return "—";
    }
    const asNumber = Number.parseFloat(trimmed);
    if (Number.isFinite(asNumber)) {
      return `${numberFormatter.format(asNumber)} kr`;
    }
    return trimmed;
  }
  return formatValue(value);
};

const formatDate = (value: SoknadInputs["behovForBistand"]["startdato"]): string => {
  const parsed = parseIsoDate(value);
  return parsed ? parsed.toLocaleDateString("nb-NO") : "—";
};

type SoknadSummaryProps = {
  data: SoknadInputs;
  editable?: boolean;
  onEditStep1?: React.MouseEventHandler<HTMLAnchorElement>;
  onEditStep2?: React.MouseEventHandler<HTMLAnchorElement>;
};

export function SoknadSummary({
  data,
  editable = false,
  onEditStep1,
  onEditStep2,
}: SoknadSummaryProps) {
  const { virksomhet, ansatt, ekspert, behovForBistand, nav } = data;

  return (
    <>
      <FormSummary>
        <FormSummary.Header>
          <FormSummary.Heading level="2">Deltakere</FormSummary.Heading>
        </FormSummary.Header>
        <FormSummary.Answers>
          <FormSummary.Answer>
            <FormSummary.Label>Navn på virksomhet</FormSummary.Label>
            <FormSummary.Value>{formatValue(virksomhet.navn)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Organisasjonsnummer</FormSummary.Label>
            <FormSummary.Value>{formatValue(virksomhet.virksomhetsnummer)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummaryAnswer>
            <FormSummary.Label>Kontaktperson i virksomheten</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(virksomhet.kontaktperson.navn)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>E-post</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(virksomhet.kontaktperson.epost)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Telefonnummer</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(virksomhet.kontaktperson.telefonnummer)}
                  </FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummaryAnswer>

          <FormSummaryAnswer>
            <FormSummary.Label>Ansatt</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ansatt.navn)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Fødselsnummer</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ansatt.fnr)}</FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummaryAnswer>

          <FormSummaryAnswer>
            <FormSummary.Label>Ekspert</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ekspert.navn)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Tilknyttet virksomhet</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ekspert.virksomhet)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Kompetanse / autorisasjon</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ekspert.kompetanse)}</FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummaryAnswer>
        </FormSummary.Answers>
        {editable && onEditStep1 && (
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={onEditStep1} />
          </FormSummary.Footer>
        )}
      </FormSummary>

      <FormSummary>
        <FormSummary.Header>
          <FormSummary.Heading level="2">Behov for bistand</FormSummary.Heading>
        </FormSummary.Header>
        <FormSummary.Answers>
          <FormSummary.Answer>
            <FormSummary.Label>
              Beskriv den ansattes arbeidssituasjon, sykefravær og hvorfor dere ser behov for
              ekspertbistand
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(behovForBistand.begrunnelse)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>
              Hva vil dere har hjelp til fra eksperten, og hvor mange timer tror dere at det vil ta?
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(behovForBistand.behov)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>
              Hvilke tiltak for tilrettelegging har dere allerede gjort, vurdert eller forsøkt?
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(behovForBistand.tilrettelegging)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Estimert kostnad for ekspertbistand</FormSummary.Label>
            <FormSummary.Value>{formatCurrency(behovForBistand.estimertKostnad)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Startdato</FormSummary.Label>
            <FormSummary.Value>{formatDate(behovForBistand.startdato)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>
              Hvem i Nav har du drøftet behovet om ekspertbistand i denne saken med?
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(nav.kontaktperson)}</FormSummary.Value>
          </FormSummary.Answer>
        </FormSummary.Answers>
        {editable && onEditStep2 && (
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={onEditStep2} />
          </FormSummary.Footer>
        )}
      </FormSummary>
    </>
  );
}
