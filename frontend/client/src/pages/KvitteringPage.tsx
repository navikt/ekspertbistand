import { useMemo } from "react";
import { useParams } from "react-router-dom";
import { Alert, BodyLong, Box, FormSummary, Heading, Loader, VStack } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { draftDtoToInputs, type DraftDto } from "../features/soknad/payload";
import { SOKNADER_PATH, EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { BackLink } from "../components/BackLink";
import useSWR from "swr";
import { type SoknadInputs } from "../features/soknad/schema";
import {
  formatCurrency,
  formatDate,
  formatSubmittedDate,
  formatTimer,
  formatValue,
} from "../components/summaryFormatters";

type KvitteringMetadata = {
  status?: string | null;
  innsendtTidspunkt?: string | null;
};

const formatDateTimePretty = (value?: string | null): string | null => {
  if (!value) return null;
  const formatted = formatSubmittedDate(value);
  return formatted === "—" ? null : formatted;
};

type KvitteringSummaryProps = {
  data: SoknadInputs;
  saksnummer?: string;
  innsendtTekst: string | null;
};

function KvitteringSummary({ data, saksnummer, innsendtTekst }: KvitteringSummaryProps) {
  return (
    <VStack gap="4">
      <FormSummary>
        <FormSummary.Header>
          <FormSummary.Heading level="2">Søknad</FormSummary.Heading>
        </FormSummary.Header>
        <FormSummary.Answers>
          <FormSummary.Answer>
            <FormSummary.Label>Saksnummer</FormSummary.Label>
            <FormSummary.Value>{formatValue(saksnummer)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Navn på virksomhet</FormSummary.Label>
            <FormSummary.Value>{formatValue(data.virksomhet.navn)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Organisasjonsnummer</FormSummary.Label>
            <FormSummary.Value>{formatValue(data.virksomhet.virksomhetsnummer)}</FormSummary.Value>
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
                  <FormSummary.Label>Kompetanse / autorisasjon</FormSummary.Label>
                  <FormSummary.Value>{formatValue(data.ekspert.kompetanse)}</FormSummary.Value>
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
            <FormSummary.Label>Estimert kostnad for ekspertbistand</FormSummary.Label>
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

export default function KvitteringPage() {
  const { id } = useParams<{ id: string }>();
  const {
    data: draft,
    error,
    isLoading,
  } = useSWR<DraftDto | null>(id ? `${EKSPERTBISTAND_API_PATH}/${id}` : null);

  const formData = useMemo(() => (draft ? draftDtoToInputs(draft) : null), [draft]);
  const metadata = useMemo<KvitteringMetadata>(() => {
    if (!draft) return {};
    return {
      status: draft?.status ?? null,
      innsendtTidspunkt: draft?.innsendtTidspunkt ?? draft?.opprettetTidspunkt ?? null,
    };
  }, [draft]);

  const errorMessage = error
    ? error instanceof Error
      ? error.message
      : "Kunne ikke hente søknaden akkurat nå."
    : null;

  const innsendtTekst = useMemo(() => {
    return formatDateTimePretty(metadata.innsendtTidspunkt);
  }, [metadata.innsendtTidspunkt]);

  return (
    <DecoratedPage>
      <VStack gap="8" data-aksel-template="receipt">
        <BackLink to={SOKNADER_PATH}>Tilbake til oversikt</BackLink>

        <Alert variant="success" role="status">
          Du har sendt søknaden
        </Alert>

        <VStack gap="2" align="center" style={{ textAlign: "center" }}>
          <Box.New background="neutral-moderate" padding="4">
            <Heading level="1" size="medium">
              Søknaden er sendt
            </Heading>
            <BodyLong>
              Saksbehandlingstiden er vanligvis et par virkedager, og du kan følge saken her. Du får
              beskjed på e-post når søknaden er behandlet. Vent med å ta tiltaket i bruk til du har
              mottatt svar.
            </BodyLong>
          </Box.New>
        </VStack>

        {isLoading && (
          <Box aria-live="polite">
            <Loader size="large" title="Laster kvittering" />
          </Box>
        )}

        {errorMessage && (
          <Alert variant="error" role="alert">
            {errorMessage}
          </Alert>
        )}

        {!isLoading && !errorMessage && formData && (
          <KvitteringSummary data={formData} saksnummer={id} innsendtTekst={innsendtTekst} />
        )}

        {!isLoading && !errorMessage && !formData && (
          <Alert variant="warning" role="alert">
            Fant ikke kvitteringen akkurat nå. Prøv igjen senere fra oversikten.
          </Alert>
        )}

        <BackLink to={SOKNADER_PATH}>Tilbake til oversikt</BackLink>
      </VStack>
    </DecoratedPage>
  );
}
