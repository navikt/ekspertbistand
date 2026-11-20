import { useEffect, useMemo, useState } from "react";
import { Navigate, useLocation, useNavigate, useParams } from "react-router";
import { ArrowRightIcon, FilePdfIcon } from "@navikt/aksel-icons";
import {
  Alert,
  Bleed,
  BodyShort,
  BodyLong,
  Box,
  Button,
  ExpansionCard,
  Heading,
  HStack,
  Link,
  Loader,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { draftDtoToInputs, type DraftDto } from "../features/soknad/payload";
import {
  fetchTilskuddsbrevHtmlForSkjema,
  fetchTilskuddsbrevPdfForTilsagnNummer,
} from "../features/tilsagn/tilsagn";
import {
  SOKNADER_PATH,
  EKSPERTBISTAND_API_PATH,
  REFUSJON_URL,
  HENT_FORSTESIDE_URL,
} from "../utils/constants";
import { ApplicationPictogram } from "../components/ApplicationPictogram";
import { BackLink } from "../components/BackLink";
import { Nokkeldata } from "../components/Nokkeldata";
import useSWR from "swr";
import { formatDate, formatSubmittedDateOrNull } from "../components/summaryFormatters";
import { SoknadSendtInnKort } from "../components/SoknadSendtInnKort";
import { useVedtak } from "../hooks/useVedtak";

type KvitteringMetadata = {
  status?: string | null;
  innsendtTidspunkt?: string | null;
};

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
  const { vedtak, isLoading: vedtakLoading } = useVedtak(
    id,
    statusKey === "godkjent" || statusKey === "avslatt"
  );
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
  const isAvslatt = statusKey === "avslatt";
  const isSubmitted = statusKey === "innsendt";
  const submissionSuccess = Boolean(
    (location.state as { submissionSuccess?: boolean } | null)?.submissionSuccess
  );
  const [showSubmittedAlert, setShowSubmittedAlert] = useState(false);
  const [pdfLoadingMap, setPdfLoadingMap] = useState<Record<string, boolean>>({});

  async function lastNedPdf(tilsagnNummer: string) {
    setPdfLoadingMap((prev) => ({ ...prev, [tilsagnNummer]: true }));
    try {
      const blob = await fetchTilskuddsbrevPdfForTilsagnNummer(tilsagnNummer);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `tilskuddsbrev-${tilsagnNummer}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } finally {
      setPdfLoadingMap((prev) => ({ ...prev, [tilsagnNummer]: false }));
    }
  }

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

  if (id && vedtak) {
    return <Navigate to={`/v2/skjema/${id}/vedtak`} replace />;
  }

  if (vedtakLoading) {
    return (
      <DecoratedPage>
        <Box aria-live="polite">
          <Loader size="large" title="Laster kvittering" />
        </Box>
      </DecoratedPage>
    );
  }

  return (
    <DecoratedPage>
      <VStack gap="space-12">
        <Bleed asChild marginInline={{ lg: "space-128" }}>
          <Box
            width={{ xs: "64px", lg: "96px" }}
            height={{ xs: "64px", lg: "96px" }}
            asChild
            position={{ xs: "relative", lg: "absolute" }}
          >
            <ApplicationPictogram />
          </Box>
        </Bleed>
        <Heading level="1" size="xlarge">
          Tilskudd til ekspertbistand
        </Heading>
      </VStack>

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
              <Link href={REFUSJON_URL}>
                <span style={{ display: "inline-flex", alignItems: "center", gap: "0.5rem" }}>
                  Søk refusjon
                  <ArrowRightIcon aria-hidden focusable="false" />
                </span>
              </Link>
            </BodyLong>
            <BodyLong>
              {HENT_FORSTESIDE_URL ? (
                <Link href={HENT_FORSTESIDE_URL}>
                  <span style={{ display: "inline-flex", alignItems: "center", gap: "0.5rem" }}>
                    Førsteside til sluttrapport
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

      {showSubmittedAlert && !isApproved && !isRejected && !isAvslatt && (
        <Alert variant="success" role="status">
          Du har sendt søknaden
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

      <VStack gap="space-24">
        {!isApproved && !isRejected && !isAvslatt && (
          <>
            <Box background="info-moderate" padding="space-24">
              <Heading level="2" size="large" align="center">
                Til behandling
              </Heading>
            </Box>
            <div>
              <Heading level="3" size="small">
                Nav har mottatt søknaden
              </Heading>
              <BodyLong>
                Saksbehandlingstiden er vanligvis en uke, og du kan følge saken her. Du får beskjed
                på e-post når søknaden er behandlet. Vent med å ta tiltaket i bruk til du har
                mottatt svar.
              </BodyLong>
            </div>
          </>
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
        {isAvslatt && (
          <VStack gap="space-2" align="center">
            <Box background="danger-moderate" padding="space-12">
              <Heading level="2" size="medium" align="center">
                Søknad avslått
              </Heading>
              <BodyLong align="center">
                Du får vedtaket med begrunnelse og informasjon om hvordan du kan klage på det.
              </BodyLong>
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

        {!isLoading && !errorMessage && formData && (
          <Nokkeldata
            rader={[
              [
                "Startdato",
                formData.behovForBistand.startdato
                  ? formatDate(formData.behovForBistand.startdato)
                  : null,
              ],
              ["Deltaker", formData.ansatt.navn],
              ["Ekspert", formData.ekspert.navn],
              ["Arbeidsgiver", formData.virksomhet.virksomhetsnavn],
            ]}
          />
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
                    <VStack gap="space-8">
                      <HStack justify="end">
                        <Button
                          variant="tertiary"
                          size="small"
                          icon={<FilePdfIcon aria-hidden />}
                          loading={pdfLoadingMap[tilskuddsbrev.tilsagnNummer] ?? false}
                          onClick={() => lastNedPdf(tilskuddsbrev.tilsagnNummer)}
                        >
                          Last ned PDF
                        </Button>
                      </HStack>
                      <Box dangerouslySetInnerHTML={{ __html: tilskuddsbrev.html }} />
                    </VStack>
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

        {!isLoading && !errorMessage && formData && (
          <SoknadSendtInnKort soknad={formData} saksnummer={id} innsendtTekst={innsendtTekst} />
        )}

        {!isLoading && !errorMessage && !formData && (
          <Alert variant="warning" role="alert">
            Fant ikke kvitteringen akkurat nå. Prøv igjen senere fra oversikten.
          </Alert>
        )}
      </VStack>
    </DecoratedPage>
  );
}
