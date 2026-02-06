import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { ArrowRightIcon } from "@navikt/aksel-icons";
import {
  Alert,
  BodyShort,
  BodyLong,
  Box,
  ExpansionCard,
  FormSummary,
  Heading,
  HStack,
  Link,
  Loader,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { draftDtoToInputs, type DraftDto } from "../features/soknad/payload";
import { fetchTilskuddsbrevHtmlForSkjema } from "../features/tilsagn/tilsagn";
import {
  SOKNADER_PATH,
  EKSPERTBISTAND_API_PATH,
  REFUSJON_URL,
  HENT_FORSTESIDE_URL,
} from "../utils/constants";
import { BackLink } from "../components/BackLink";
import useSWR from "swr";
import { type SoknadInputs } from "../features/soknad/schema";
import {
  formatCurrency,
  formatDate,
  formatSubmittedDateOrNull,
  formatTimer,
  formatValue,
} from "../components/summaryFormatters";

type KvitteringMetadata = {
  status?: string | null;
  innsendtTidspunkt?: string | null;
};

type KvitteringSummaryProps = {
  data: SoknadInputs;
  saksnummer?: string;
  innsendtTekst: string | null;
};

function KvitteringSummary({ data, saksnummer, innsendtTekst }: KvitteringSummaryProps) {
  return (
    <VStack gap="space-32">
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
  const location = useLocation();
  const navigate = useNavigate();
  const {
    data: draft,
    error,
    isLoading,
  } = useSWR<DraftDto | null>(id ? `${EKSPERTBISTAND_API_PATH}/${id}` : null);
  const statusKey = (draft?.beslutning?.status ?? draft?.status ?? "").toLowerCase();
  const shouldLoadTilsagn = statusKey === "godkjent";
  const {
    data: tilskuddsbrevHtml,
    error: tilskuddsbrevError,
    isLoading: tilskuddsbrevLoading,
  } = useSWR(shouldLoadTilsagn && id ? ["tilskuddsbrev-html", id] : null, ([, skjemaId]) =>
    fetchTilskuddsbrevHtmlForSkjema(skjemaId)
  );

  const formData = useMemo(() => (draft ? draftDtoToInputs(draft) : null), [draft]);
  const metadata = useMemo<KvitteringMetadata>(() => {
    if (!draft) return {};
    return {
      status: draft?.status ?? null,
      innsendtTidspunkt: draft?.innsendtTidspunkt ?? draft?.opprettetTidspunkt ?? null,
    };
  }, [draft]);
  const isApproved = statusKey === "godkjent";
  const isRejected = statusKey === "avlyst";
  const isSubmitted = statusKey === "innsendt";
  const submissionSuccess = Boolean(
    (location.state as { submissionSuccess?: boolean } | null)?.submissionSuccess
  );
  const [showSubmittedAlert, setShowSubmittedAlert] = useState(false);

  useEffect(() => {
    if (!isSubmitted || !submissionSuccess) return;
    navigate(".", { replace: true, state: {} });
    const showTimeoutId = window.setTimeout(() => {
      setShowSubmittedAlert(true);
    }, 0);
    const hideTimeoutId = window.setTimeout(() => {
      setShowSubmittedAlert(false);
    }, 5000);
    return () => {
      window.clearTimeout(showTimeoutId);
      window.clearTimeout(hideTimeoutId);
    };
  }, [isSubmitted, navigate, submissionSuccess]);

  const errorMessage = error
    ? error instanceof Error
      ? error.message
      : "Kunne ikke hente søknaden akkurat nå."
    : null;
  const tilsagnErrorMessage = tilskuddsbrevError
    ? "Kunne ikke hente tilskuddsbrev akkurat nå."
    : null;

  const tilsagnCount = tilskuddsbrevHtml?.length ?? 0;

  const innsendtTekst = useMemo(() => {
    return formatSubmittedDateOrNull(metadata.innsendtTidspunkt);
  }, [metadata.innsendtTidspunkt]);

  return (
    <DecoratedPage>
      <VStack gap="space-32">
        {isApproved ? (
          <HStack
            align="center"
            justify="space-between"
            wrap={false}
            style={{ alignItems: "baseline" }}
          >
            <BackLink to={SOKNADER_PATH}>Tilbake til oversikt</BackLink>
            <VStack gap="space-4" align="end">
              <BodyShort size="small">
                <strong>Snarveier</strong>
              </BodyShort>
              <BodyLong>
                <Link href={REFUSJON_URL} target="_blank" rel="noreferrer">
                  <span style={{ display: "inline-flex", alignItems: "center", gap: "0.5rem" }}>
                    Søk refusjon
                    <ArrowRightIcon aria-hidden focusable="false" />
                  </span>
                </Link>
              </BodyLong>
              <BodyLong>
                {HENT_FORSTESIDE_URL ? (
                  <Link href={HENT_FORSTESIDE_URL} target="_blank" rel="noreferrer">
                    <span style={{ display: "inline-flex", alignItems: "center", gap: "0.5rem" }}>
                      Hent førsteside
                      <ArrowRightIcon aria-hidden focusable="false" />
                    </span>
                  </Link>
                ) : (
                  "Hent førsteside"
                )}
              </BodyLong>
            </VStack>
          </HStack>
        ) : (
          <BackLink to={SOKNADER_PATH}>Tilbake til oversikt</BackLink>
        )}

        {showSubmittedAlert && !isApproved && !isRejected && (
          <Alert variant="success" role="status">
            Du har sendt søknaden
          </Alert>
        )}

        <Heading level="1" size="large">
          Søknad om tilskudd til ekspertbistand
        </Heading>

        {!isApproved && !isRejected && (
          <VStack gap="space-12" align="center">
            <Box background="neutral-moderate" padding="space-16">
              <Heading level="2" size="medium" align="center">
                Nav har mottatt søknaden
              </Heading>
              <BodyLong align="center">
                Saksbehandlingstiden er vanligvis en uke, og du kan følge saken her. Du får beskjed
                på e-post når søknaden er behandlet. Vent med å ta tiltaket i bruk til du har
                mottatt svar.
              </BodyLong>
            </Box>
          </VStack>
        )}
        {isApproved && (
          <VStack gap="space-8">
            <Box background="success-moderate" padding="space-12">
              <Heading level="2" size="medium" align="center">
                Søknaden godkjent
              </Heading>
            </Box>
          </VStack>
        )}
        {isRejected && (
          <VStack gap="space-2" align="center">
            <Box background="danger-moderate" padding="space-12">
              <Heading level="2" size="medium" align="center">
                Søknad trukket eller avslått
              </Heading>
              <BodyLong align="center">
                Har du trukket søknaden, trenger du ikke gjøre noe.
              </BodyLong>
              <BodyLong align="center">
                Hvis Nav har avslått søknaden, får du vedtaket i posten med informasjon om hvordan
                du kan klage på det.
              </BodyLong>
            </Box>
          </VStack>
        )}

        {!tilskuddsbrevLoading && !tilsagnErrorMessage && shouldLoadTilsagn && tilsagnCount > 0 && (
          <VStack gap="space-12">
            {tilskuddsbrevHtml?.map((tilskuddsbrev) => {
              return (
                <ExpansionCard
                  key={tilskuddsbrev.tilsagnNummer}
                  size="small"
                  aria-label="Tilsagnsdetaljer"
                >
                  <ExpansionCard.Header>
                    <ExpansionCard.Title as="h3">
                      <HStack align="center" wrap={false} justify="space-between">
                        Dere har fått innvilget tilskudd til ekspertbistand:{" "}
                        {tilskuddsbrev.tilsagnNummer}
                      </HStack>
                    </ExpansionCard.Title>
                  </ExpansionCard.Header>
                  <ExpansionCard.Content>
                    <Box dangerouslySetInnerHTML={{ __html: tilskuddsbrev.html }} />
                  </ExpansionCard.Content>
                </ExpansionCard>
              );
            })}
          </VStack>
        )}

        {tilsagnErrorMessage && shouldLoadTilsagn && (
          <Alert variant="warning" role="alert">
            {tilsagnErrorMessage}
          </Alert>
        )}

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
