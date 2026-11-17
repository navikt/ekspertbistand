import { useEffect, useMemo, useState } from "react";
import { Link as RouterLink, Navigate, useLocation, useNavigate, useParams } from "react-router";
import { ArrowRedoIcon, ClockIcon } from "@navikt/aksel-icons";
import {
  Alert,
  Bleed,
  BodyLong,
  Box,
  Button,
  ExpansionCard,
  Heading,
  HStack,
  Loader,
  Skeleton,
  Tag,
  VStack,
} from "@navikt/ds-react";
import useSWR from "swr";
import DecoratedPage from "../components/DecoratedPage";
import { ApplicationPictogram } from "../components/ApplicationPictogram";
import { BackLink } from "../components/BackLink";
import { Nokkeldata, type NokkeldataRad } from "../components/Nokkeldata";
import { SoknadSendtInnKort } from "../components/SoknadSendtInnKort";
import { SluttrapportSendtInnKort } from "../components/SluttrapportSendtInnKort";
import { RefusjonskravSendtInnKort } from "../components/RefusjonskravSendtInnKort";
import { PdfForhandsvisning } from "../components/pdf";
import { A4_FORHOLD } from "../components/pdf/types";
import { formatSubmittedDateOrNull } from "../components/summaryFormatters";
import { draftDtoToInputs, type DraftDto } from "../features/soknad/payload";
import type { Vedtaksbrev, VedtaksbrevUtfall } from "../features/vedtak/vedtak";
import { useVedtak } from "../hooks/useVedtak";
import { useVedtaksbrev } from "../hooks/useVedtaksbrev";
import { useSluttrapportStatus } from "../hooks/useSluttrapportStatus";
import { useRefusjonStatus } from "../hooks/useRefusjonStatus";
import { EKSPERTBISTAND_API_PATH, SOKNADER_PATH } from "../utils/constants";

const MINIATYR_BREDDE = 240;

const UTFALL: Record<
  VedtaksbrevUtfall,
  { status: string; vedtakTittel: string; bakgrunn: "success-moderate" | "danger-moderate" }
> = {
  INNVILGET: {
    status: "Søknad godkjent",
    vedtakTittel: "Vedtak om godkjent søknad",
    bakgrunn: "success-moderate",
  },
  INNVILGET_REDUSERT: {
    status: "Søknad godkjent med redusert beløp",
    vedtakTittel: "Vedtak om godkjent søknad med redusert beløp",
    bakgrunn: "success-moderate",
  },
  AVSLATT: {
    status: "Søknad avslått",
    vedtakTittel: "Vedtak om avslag på søknad",
    bakgrunn: "danger-moderate",
  },
};

const kroner = (belop: string) => `${belop} kr`;

export default function VedtakPage() {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const navigeringsstate = location.state as {
    sluttrapportSendtInn?: boolean;
    refusjonSendtInn?: boolean;
    statusOppdateringFeilet?: boolean;
  } | null;
  const fraSluttrapport = navigeringsstate?.sluttrapportSendtInn === true;
  const fraRefusjon = navigeringsstate?.refusjonSendtInn === true;
  const [innsending] = useState(() => ({
    soknadId: fraSluttrapport || fraRefusjon ? id : null,
    type: fraRefusjon ? "REFUSJON" : fraSluttrapport ? "SLUTTRAPPORT" : null,
    statusOppdateringFeilet: navigeringsstate?.statusOppdateringFeilet === true,
  }));
  const innsendingBekreftet = id !== undefined && innsending.soknadId === id;
  const sluttrapportSendtInn = innsendingBekreftet && innsending.type === "SLUTTRAPPORT";
  const refusjonSendtInn = innsendingBekreftet && innsending.type === "REFUSJON";

  useEffect(() => {
    if (fraSluttrapport || fraRefusjon) {
      navigate(".", { replace: true, state: null });
    }
  }, [fraSluttrapport, fraRefusjon, navigate]);

  const {
    data: draft,
    error: soknadError,
    isLoading: soknadLoading,
  } = useSWR<DraftDto | null>(id ? `${EKSPERTBISTAND_API_PATH}/${id}` : null);
  const { vedtak, error: vedtakError, isLoading: vedtakLoading } = useVedtak(id);
  const innvilgetSoknadId = vedtak && vedtak.utfall !== "AVSLATT" ? id : undefined;
  const {
    sluttrapport,
    isLoading: sluttrapportLoading,
    error: sluttrapportError,
  } = useSluttrapportStatus(innvilgetSoknadId);
  const {
    refusjon,
    isLoading: refusjonLoading,
    error: refusjonError,
  } = useRefusjonStatus(innvilgetSoknadId);
  const [vedtakApent, setVedtakApent] = useState(false);
  const brev = useVedtaksbrev(id, vedtakApent);

  const soknad = useMemo(() => (draft ? draftDtoToInputs(draft) : null), [draft]);
  const innsendtTekst = formatSubmittedDateOrNull(
    draft?.innsendtTidspunkt ?? draft?.opprettetTidspunkt
  );

  if (id && vedtak === null) {
    return <Navigate to={`/v2/skjema/${id}/kvittering`} replace />;
  }

  const error = vedtakError ?? soknadError;
  const sluttrapportStatusFeilet =
    sluttrapportError ||
    (sluttrapportSendtInn &&
      !sluttrapport &&
      (innsending.statusOppdateringFeilet || !sluttrapportLoading));
  const refusjonStatusFeilet =
    refusjonError ||
    (refusjonSendtInn && !refusjon && (innsending.statusOppdateringFeilet || !refusjonLoading));
  const refusjonMottatt = Boolean(innvilgetSoknadId && (refusjon || refusjonSendtInn));
  const visHandlinger = Boolean(
    !sluttrapport || sluttrapportError || !refusjonMottatt || refusjonError || refusjonLoading
  );

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

      <BackLink to={SOKNADER_PATH}>Tilbake til oversikt</BackLink>

      {sluttrapportSendtInn && (
        <Alert variant="success" role="status">
          Sluttrapporten er sendt inn.
        </Alert>
      )}
      {refusjonSendtInn && (
        <Alert variant="success" role="status">
          Refusjonskravet er sendt inn.
        </Alert>
      )}

      {(vedtakLoading || soknadLoading) && (
        <Box aria-live="polite">
          <Loader size="large" title="Laster vedtaket" />
        </Box>
      )}

      {error && (
        <Alert variant="error" role="alert">
          {error instanceof Error ? error.message : "Kunne ikke hente vedtaket akkurat nå."}
        </Alert>
      )}

      {id && vedtak && !error && !soknadLoading && (
        <VStack gap="space-24">
          <Box
            background={refusjonMottatt ? "info-moderate" : UTFALL[vedtak.utfall].bakgrunn}
            borderColor={refusjonMottatt ? "info" : undefined}
            borderWidth={refusjonMottatt ? "1" : undefined}
            padding="space-24"
          >
            <Heading level="2" size="large" align="center">
              {refusjonMottatt ? "Nav behandler refusjonskravet" : UTFALL[vedtak.utfall].status}
            </Heading>
          </Box>

          {vedtak.utfall !== "AVSLATT" && vedtak.innvilgetBelop && (
            <Heading level="3" size="medium">
              {kroner(vedtak.innvilgetBelop)} satt av til ekspertbistand
            </Heading>
          )}

          <VedtakNokkeldata vedtak={vedtak} ekspert={soknad?.ekspert.navn} />

          {vedtak.utfall !== "AVSLATT" && (
            <>
              {!refusjonMottatt && (
                <div>
                  <Heading level="3" size="small">
                    Vilkår og informasjon om utbetaling
                  </Heading>
                  <BodyLong>
                    Du må sende inn sluttrapport og dokumentere faktiske utgifter knyttet til
                    ekspertbistand innen fristen.
                  </BodyLong>
                </div>
              )}
              {visHandlinger && (
                <VStack gap="space-24" align="start">
                  {sluttrapportStatusFeilet ? (
                    <Alert variant="error" role="alert">
                      {sluttrapportSendtInn
                        ? "Sluttrapporten er sendt inn, men opplysningene kunne ikke hentes. Prøv å laste siden på nytt."
                        : "Kunne ikke hente status for sluttrapporten. Prøv å laste siden på nytt."}
                    </Alert>
                  ) : sluttrapportLoading ? (
                    <Box aria-live="polite">
                      <Loader title="Laster status for sluttrapporten" />
                    </Box>
                  ) : !sluttrapport && !sluttrapportSendtInn ? (
                    <Button
                      as={RouterLink}
                      to={`/v2/skjema/${id}/sluttrapport`}
                      variant="secondary"
                    >
                      Send inn sluttrapport fra eksperten
                    </Button>
                  ) : null}
                  {refusjonStatusFeilet ? (
                    <Alert variant="error" role="alert">
                      {refusjonSendtInn
                        ? "Refusjonskravet er sendt inn, men opplysningene kunne ikke hentes. Prøv å laste siden på nytt."
                        : "Kunne ikke hente status for refusjonskravet. Prøv å laste siden på nytt."}
                    </Alert>
                  ) : refusjonLoading ? (
                    <Box aria-live="polite">
                      <Loader title="Laster status for refusjonskravet" />
                    </Box>
                  ) : !refusjonMottatt ? (
                    <HStack gap="space-24" align="center">
                      <Button as={RouterLink} to={`/v2/skjema/${id}/refusjon`} variant="secondary">
                        Søk refusjon
                      </Button>
                      {vedtak.refusjonsfrist && (
                        <Tag
                          variant="outline"
                          data-color="warning"
                          icon={<ClockIcon aria-hidden />}
                        >
                          Frist {vedtak.refusjonsfrist}
                        </Tag>
                      )}
                    </HStack>
                  ) : null}
                </VStack>
              )}
            </>
          )}

          <VStack gap="space-16">
            {innvilgetSoknadId && refusjon && !refusjonError && (
              <RefusjonskravSendtInnKort soknadId={innvilgetSoknadId} refusjon={refusjon} />
            )}
            {vedtak.utfall !== "AVSLATT" && sluttrapport && !sluttrapportError && (
              <SluttrapportSendtInnKort sluttrapport={sluttrapport} />
            )}
            <ExpansionCard
              size="small"
              aria-label={UTFALL[vedtak.utfall].vedtakTittel}
              open={vedtakApent}
              onToggle={setVedtakApent}
            >
              <ExpansionCard.Header>
                <HStack wrap={false} gap="space-16" align="center">
                  <ArrowRedoIcon aria-hidden fontSize="2rem" />
                  <div>
                    <ExpansionCard.Title as="h2" size="small">
                      {UTFALL[vedtak.utfall].vedtakTittel}
                    </ExpansionCard.Title>
                    <ExpansionCard.Description>{vedtak.vedtaksdato}</ExpansionCard.Description>
                  </div>
                </HStack>
              </ExpansionCard.Header>
              <ExpansionCard.Content>
                {brev.error ? (
                  <Alert variant="error" role="alert">
                    Kunne ikke hente vedtaksbrevet akkurat nå. Prøv igjen senere.
                  </Alert>
                ) : brev.fil ? (
                  <PdfForhandsvisning fil={brev.fil} bredde={MINIATYR_BREDDE} />
                ) : (
                  <Skeleton
                    variant="rectangle"
                    width={MINIATYR_BREDDE}
                    height={MINIATYR_BREDDE * A4_FORHOLD}
                  />
                )}
              </ExpansionCard.Content>
            </ExpansionCard>

            {soknad && (
              <SoknadSendtInnKort soknad={soknad} saksnummer={id} innsendtTekst={innsendtTekst} />
            )}
          </VStack>
        </VStack>
      )}
    </DecoratedPage>
  );
}

function VedtakNokkeldata({ vedtak, ekspert }: { vedtak: Vedtaksbrev; ekspert?: string }) {
  const rader: NokkeldataRad[] = [
    [
      "Tiltaksperiode",
      vedtak.tiltaksperiode && `${vedtak.tiltaksperiode.fra} – ${vedtak.tiltaksperiode.til}`,
    ],
    ["Deltaker", vedtak.deltakerNavn],
    ["Ekspert", ekspert],
    ["Arbeidsgiver", vedtak.virksomhetsnavn],
    [
      "Søkt beløp",
      vedtak.utfall !== "INNVILGET" ? vedtak.soknadssum && kroner(vedtak.soknadssum) : null,
    ],
  ];

  return <Nokkeldata rader={rader} />;
}
