import { Fragment, useMemo, useState } from "react";
import { Link as RouterLink, Navigate, useParams } from "react-router";
import { ArrowRedoIcon, ClockIcon, FileTextIcon } from "@navikt/aksel-icons";
import {
  Alert,
  Bleed,
  BodyLong,
  BodyShort,
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
import { KvitteringSummary } from "../components/KvitteringSummary";
import { PdfForhandsvisning } from "../components/pdf";
import { A4_FORHOLD } from "../components/pdf/types";
import { formatSubmittedDateOrNull } from "../components/summaryFormatters";
import { draftDtoToInputs, type DraftDto } from "../features/soknad/payload";
import type { Vedtaksbrev, VedtaksbrevUtfall } from "../features/vedtak/vedtak";
import { useVedtak } from "../hooks/useVedtak";
import { useVedtaksbrev } from "../hooks/useVedtaksbrev";
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
  const {
    data: draft,
    error: soknadError,
    isLoading: soknadLoading,
  } = useSWR<DraftDto | null>(id ? `${EKSPERTBISTAND_API_PATH}/${id}` : null);
  const { vedtak, error: vedtakError, isLoading: vedtakLoading } = useVedtak(id);
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

  return (
    <DecoratedPage blockProps={{ width: "text" }}>
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
          <Box background={UTFALL[vedtak.utfall].bakgrunn} padding="space-24">
            <Heading level="2" size="large" align="center">
              {UTFALL[vedtak.utfall].status}
            </Heading>
          </Box>

          {vedtak.utfall !== "AVSLATT" && vedtak.innvilgetBelop && (
            <Heading level="3" size="medium">
              {kroner(vedtak.innvilgetBelop)} satt av til ekspertbistand
            </Heading>
          )}

          <Nokkeldata vedtak={vedtak} ekspert={soknad?.ekspert.navn} />

          {vedtak.utfall !== "AVSLATT" && (
            <>
              <div>
                <Heading level="3" size="small">
                  Vilkår og informasjon om utbetaling
                </Heading>
                <BodyLong>
                  Du må sende inn sluttrapport og dokumentere faktiske utgifter knyttet til
                  ekspertbistand innen fristen.
                </BodyLong>
              </div>
              <VStack gap="space-24" align="start">
                <Button as={RouterLink} to={`/v2/skjema/${id}/sluttrapport`} variant="secondary">
                  Send inn sluttrapport fra eksperten
                </Button>
                <HStack gap="space-24" align="center">
                  <Button as={RouterLink} to={`/v2/skjema/${id}/refusjon`} variant="secondary">
                    Søk refusjon
                  </Button>
                  {vedtak.refusjonsfrist && (
                    <Tag variant="outline" data-color="warning" icon={<ClockIcon aria-hidden />}>
                      Frist {vedtak.refusjonsfrist}
                    </Tag>
                  )}
                </HStack>
              </VStack>
            </>
          )}

          <VStack gap="space-16">
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
                  <KvitteringSummary
                    data={soknad}
                    saksnummer={id}
                    innsendtTekst={innsendtTekst}
                    headingLevel="3"
                  />
                </ExpansionCard.Content>
              </ExpansionCard>
            )}
          </VStack>
        </VStack>
      )}
    </DecoratedPage>
  );
}

function Nokkeldata({ vedtak, ekspert }: { vedtak: Vedtaksbrev; ekspert?: string }) {
  const rader: Array<[string, string | null | undefined]> = [
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

  return (
    <dl
      style={{
        display: "grid",
        gridTemplateColumns: "max-content 1fr",
        columnGap: "var(--ax-space-24)",
        rowGap: "var(--ax-space-4)",
        margin: 0,
      }}
    >
      {rader
        .filter((rad): rad is [string, string] => Boolean(rad[1]))
        .map(([label, verdi]) => (
          <Fragment key={label}>
            <BodyShort as="dt" size="large" weight="semibold">
              {label}
            </BodyShort>
            <BodyShort as="dd" size="large" style={{ margin: 0 }}>
              {verdi}
            </BodyShort>
          </Fragment>
        ))}
    </dl>
  );
}
