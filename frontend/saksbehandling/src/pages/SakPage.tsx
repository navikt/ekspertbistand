import { ArrowLeftIcon, CheckmarkCircleIcon, ChevronRightIcon } from "@navikt/aksel-icons";
import {
  Accordion,
  Alert,
  BodyLong,
  BodyShort,
  Box,
  CopyButton,
  ExpansionCard,
  HGrid,
  HStack,
  Heading,
  Label,
  Link,
  Loader,
  Show,
  Tabs,
  VStack,
} from "@navikt/ds-react";
import { useCallback, useState } from "react";
import { Group, Panel } from "react-resizable-panels";
import { NavLink, useParams } from "react-router";
import { DataRad, InfoKort } from "../components/InfoKort";
import KolonneSeparator from "../components/KolonneSeparator";
import Refusjonskontroll from "../components/Refusjonskontroll";
import RefusjonskravVisning from "../components/RefusjonskravVisning";
import Sakslogg from "../components/Sakslogg";
import Sluttrapportkontroll from "../components/Sluttrapportkontroll";
import SluttrapportVisning from "../components/SluttrapportVisning";
import Tildeling from "../components/Tildeling";
import VilkårItem from "../components/VilkårItem";
import { type PdfFil, PdfForhandsvisningListe } from "../components/pdf";
import ForelopigVedtakKvittering from "../components/forelopigVedtak/ForelopigVedtakKvittering";
import ForelopigVedtakOppsummering from "../components/forelopigVedtak/ForelopigVedtakOppsummering";
import ForelopigVedtakSkjema, {
  type OppdaterSkjema,
} from "../components/forelopigVedtak/ForelopigVedtakSkjema";
import {
  type ForelopigVedtakSkjemaTilstand,
  formatPeriode,
  startTilstand,
} from "../components/forelopigVedtak/forelopigVedtak";
import { useForelopigVedtak } from "../hooks/useForelopigVedtak";
import { useRefusjon } from "../hooks/useRefusjon";
import { type SakDetaljer, useSak } from "../hooks/useSak";
import { useSluttrapport } from "../hooks/useSluttrapport";
import { type Vilkår, useVilkår } from "../hooks/useVilkår";
import { useVilkårsvurdering } from "../hooks/useVilkårsvurdering";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import type { HttpError } from "../utils/http";
import { GOSYS_URL, MODIA_URL, OVERSIKT_PATH, isMockEnabled } from "../utils/constants";
import styles from "./SakPage.module.css";

function formatDate(iso: string) {
  return new Intl.DateTimeFormat("nb-NO", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(iso));
}

function formatBeløp(beløp: string) {
  const tall = Number(beløp);
  return Number.isFinite(tall) ? `${tall.toLocaleString("nb-NO")} kr` : beløp;
}

function Spørsmål({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <VStack gap="space-4">
      <Label as="h3">{label}</Label>
      {children}
    </VStack>
  );
}

const DEMO_VEDLEGG: PdfFil[] = [
  { url: "/mock/eksempel.pdf", filnavn: "Eksempelfaktura.pdf", storrelse: 1472 },
];

type Fane = "vilkarsvurdering" | "forelopig-vedtak";
type SaksFane = "soknad" | "sluttrapport" | "refusjon";

type ForelopigVedtakFaneProps = {
  sak: SakDetaljer;
  vilkår: Vilkår[];
  vilkårLaster: boolean;
  vilkårFeilet: boolean;
  tilstand: ForelopigVedtakSkjemaTilstand;
  oppdater: OppdaterSkjema;
  sendTilBeslutter: (input: NonNullable<ForelopigVedtakSkjemaTilstand["forhandsvisning"]>) => void;
  isSaving: boolean;
  sendError: Error | null;
};

function ForelopigVedtakFane({
  sak,
  vilkår,
  vilkårLaster,
  vilkårFeilet,
  tilstand,
  oppdater,
  sendTilBeslutter,
  isSaving,
  sendError,
}: ForelopigVedtakFaneProps) {
  const innloggetAnsatt = useInnloggetAnsatt();
  const erTildeltMeg = !!innloggetAnsatt && sak.saksbehandlerIdent === innloggetAnsatt.id;

  if (sak.forelopigVedtak && sak.status === "TIL_BESLUTNING") {
    return <ForelopigVedtakOppsummering modus="sendt" sak={sak} vedtak={sak.forelopigVedtak} />;
  }

  if (sak.status === "UNDER_BEHANDLING" && erTildeltMeg) {
    if (vilkårLaster) return <Loader size="medium" title="Laster vilkår" />;
    if (vilkårFeilet) {
      return (
        <Alert variant="error" size="small">
          Kunne ikke hente vilkårene.
        </Alert>
      );
    }
    const alleVurdert =
      vilkår.length > 0 && vilkår.every((v) => v.vurdering.status !== "ikke_vurdert");
    if (!alleVurdert) {
      return (
        <Alert variant="info" size="small">
          Alle vilkår må vurderes før du kan fatte foreløpig vedtak.
        </Alert>
      );
    }
    if (tilstand.forhandsvisning) {
      const input = tilstand.forhandsvisning;
      return (
        <ForelopigVedtakOppsummering
          modus="forhandsvisning"
          sak={sak}
          vedtak={input}
          onForrige={() => oppdater({ forhandsvisning: null })}
          onSend={() => sendTilBeslutter(input)}
          isSaving={isSaving}
          error={sendError}
        />
      );
    }
    return (
      <ForelopigVedtakSkjema sak={sak} vilkår={vilkår} tilstand={tilstand} oppdater={oppdater} />
    );
  }

  if (!erTildeltMeg && (sak.status === "OPPRETTET" || sak.status === "UNDER_BEHANDLING")) {
    return (
      <Alert variant="info" size="small">
        Du må ha saken for å fatte foreløpig vedtak.
      </Alert>
    );
  }

  return <BodyShort>Foreløpig vedtak er ikke tilgjengelig ennå.</BodyShort>;
}

function feilmelding(error: HttpError | undefined) {
  switch (error?.status) {
    case 403:
      return "Du har ikke tilgang til denne saken.";
    case 404:
      return "Fant ikke saken.";
    case 503:
      return "Vi kunne ikke sjekke tilgangen din akkurat nå. Prøv igjen om litt.";
    default:
      return "Kunne ikke hente saken.";
  }
}

function Saksopplysninger({ soknad }: { soknad: SakDetaljer["soknad"] }) {
  const { ansatt, virksomhet, ekspert } = soknad;

  return (
    <VStack gap="space-32" minWidth="0">
      <InfoKort tittel="Arbeidsgiver">
        <HGrid columns="repeat(auto-fit, minmax(min(100%, 160px), 1fr))" gap="space-8 space-16">
          <DataRad label="Navn" value={virksomhet.virksomhetsnavn} />
          <DataRad label="Kontaktperson" value={virksomhet.kontaktperson.navn} />
          <DataRad label="Org.nr" value={virksomhet.virksomhetsnummer} />
          <DataRad label="E-post" value={virksomhet.kontaktperson.epost} />
          <DataRad label="Beliggenhetsadresse" value={virksomhet.beliggenhetsadresse ?? "–"} />
          <DataRad label="Telefon" value={virksomhet.kontaktperson.telefonnummer} />
        </HGrid>
      </InfoKort>

      <InfoKort tittel="Deltakere">
        <HGrid columns="repeat(auto-fit, minmax(min(100%, 160px), 1fr))" gap="space-8 space-16">
          <VStack gap="space-8" minWidth="0">
            <DataRad label="Navn" value={ansatt.navn} />
            <DataRad label="Fødselsnummer" value={ansatt.fnr} />
            <VStack gap="space-2" minWidth="0">
              <Label>Arbeidsforhold</Label>
              <Link href="#" target="_blank">
                <CheckmarkCircleIcon
                  aria-hidden
                  style={{ color: "var(--ax-text-success-decoration)" }}
                />
                Se Aa-registret
              </Link>
            </VStack>
          </VStack>
          <VStack gap="space-12" minWidth="0">
            <Link href={`${MODIA_URL}/sykefravær`} target="modia">
              Sykefraværshistorikk
            </Link>
            <Link href={`${MODIA_URL}/person`} target="modia">
              Personoversikt - Modia
            </Link>
            <Link href={`${MODIA_URL}/aktivitetsplan`} target="modia">
              Aktivitetsplan - Modia
            </Link>
            <Link href={GOSYS_URL} target="gosys">
              Gosys - personmappe
            </Link>
          </VStack>
        </HGrid>
      </InfoKort>

      <InfoKort tittel="Ekspert">
        <VStack gap="space-8" minWidth="0">
          <DataRad label="Navn" value={ekspert.navn} />
          <DataRad
            label="Godkjent utdanning/autorisasjon"
            value={ekspert.godkjentUtdanningEllerAutorisasjon.join(", ") || ekspert.kompetanse}
          />
          <DataRad
            label="Tilknyttet virksomhet"
            value={ekspert.virksomhetNavn ?? ekspert.virksomhet}
          />
          <DataRad label="Org.nr." value={ekspert.virksomhetOrgnr ?? "–"} />
        </VStack>
      </InfoKort>
    </VStack>
  );
}

function SaksinnholdLayout({
  soknad,
  midtreStandardstorrelse,
  kontroll,
  children,
}: {
  soknad: SakDetaljer["soknad"];
  midtreStandardstorrelse: string;
  kontroll?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <VStack className={styles.saksinnhold} gap="space-16" padding="space-16" minWidth="0">
      <Show below="xl">
        <ExpansionCard size="small" defaultOpen={false} aria-label="Saksopplysninger">
          <ExpansionCard.Header>
            <ExpansionCard.Title as="span" size="small">
              Saksopplysninger
            </ExpansionCard.Title>
            <ExpansionCard.Description style={{ overflowWrap: "anywhere" }}>
              {soknad.virksomhet.virksomhetsnavn} · {soknad.ansatt.navn}
            </ExpansionCard.Description>
          </ExpansionCard.Header>
          <ExpansionCard.Content>
            <Box padding="space-16" minWidth="0">
              <Saksopplysninger soknad={soknad} />
            </Box>
          </ExpansionCard.Content>
        </ExpansionCard>
      </Show>
      <Group
        className={styles.saksinnholdGroup}
        orientation="horizontal"
        style={{ minHeight: "calc(100vh - 160px)" }}
      >
        <Show above="xl" asChild>
          <Panel defaultSize="25%" minSize="260px">
            <Box padding="space-16" paddingBlock="space-32" minWidth="0">
              <Saksopplysninger soknad={soknad} />
            </Box>
          </Panel>
        </Show>
        <KolonneSeparator />
        <Panel defaultSize={midtreStandardstorrelse} minSize="320px">
          {children}
        </Panel>
        {kontroll && (
          <>
            <KolonneSeparator />
            <Panel defaultSize="30%" minSize="260px">
              {kontroll}
            </Panel>
          </>
        )}
      </Group>
    </VStack>
  );
}

export default function SakPage() {
  const { sakId: sakIdParam } = useParams<{ sakId: string }>();
  const { sak, error, isLoading } = useSak(sakIdParam ?? "");
  const sakId = sak?.sakId;
  const erArenaSak = sak?.kildeTilBehandling === "ARENA";
  const {
    vilkår,
    error: vilkårError,
    isLoading: vilkårLaster,
  } = useVilkår(erArenaSak ? undefined : sakId);
  const { lagreVurdering, isSaving, error: lagreError } = useVilkårsvurdering(sakId ?? "");
  const [tildelingError, setTildelingError] = useState<Error | null>(null);
  const forelopigVedtak = useForelopigVedtak(sakId ?? "");
  const [fane, setFane] = useState<Fane>("vilkarsvurdering");
  const [saksFane, setSaksFane] = useState<SaksFane>("soknad");
  const {
    refusjon,
    error: refusjonError,
    isLoading: refusjonLaster,
  } = useRefusjon(sakId ?? "", !!sak && !erArenaSak && saksFane === "refusjon");
  const {
    sluttrapport,
    error: sluttrapportError,
    isLoading: sluttrapportLaster,
  } = useSluttrapport(sakId ?? "", !!sak && !erArenaSak && saksFane === "sluttrapport");
  // Holdes her, fordi fanepanelene avmonteres når de ikke vises.
  const [skjema, setSkjema] = useState<ForelopigVedtakSkjemaTilstand | null>(null);
  const [kvitteringForSak, setKvitteringForSak] = useState<string | null>(null);

  const startdato = sak?.soknad.behovForBistand.startdato ?? "";
  const oppdaterSkjema: OppdaterSkjema = useCallback(
    (endring) => {
      if (!sakId) return;
      setSkjema((forrige) => {
        const gjeldende = forrige?.sakId === sakId ? forrige : startTilstand(sakId, startdato);
        const delvis = typeof endring === "function" ? endring(gjeldende) : endring;
        return { ...gjeldende, ...delvis };
      });
    },
    [sakId, startdato]
  );

  if (isLoading) return <Loader size="large" title="Laster sak" />;
  if (error || !sak) {
    const begrunnelse = error?.status === 403 ? error.begrunnelse : undefined;
    return (
      <Box padding="space-24">
        <Alert variant="error">
          <VStack gap="space-8">
            <BodyShort weight="semibold">{feilmelding(error)}</BodyShort>
            {begrunnelse && <BodyShort>{begrunnelse}</BodyShort>}
          </VStack>
        </Alert>
      </Box>
    );
  }

  const { soknad } = sak;
  const { ansatt, behovForBistand } = soknad;
  const skjemaTilstand =
    skjema?.sakId === sak.sakId ? skjema : startTilstand(sak.sakId, behovForBistand.startdato);
  const visKvittering = kvitteringForSak === sak.sakId && sak.forelopigVedtak !== null;

  const sendTilBeslutter = async (
    input: NonNullable<ForelopigVedtakSkjemaTilstand["forhandsvisning"]>
  ) => {
    if (await forelopigVedtak.sendTilBeslutter(input)) {
      setKvitteringForSak(sak.sakId);
      setSkjema(null);
    }
  };

  return (
    <>
      <Box background="soft" paddingBlock="space-8" paddingInline="space-24">
        <HStack gap="space-8" align="center" wrap>
          <BodyShort weight="semibold" style={{ overflowWrap: "anywhere" }}>
            {ansatt.navn}
          </BodyShort>
          <CopyButton copyText={ansatt.navn} size="xsmall" />
          <BodyShort>/</BodyShort>
          <BodyShort style={{ overflowWrap: "anywhere" }}>{ansatt.fnr}</BodyShort>
          <CopyButton copyText={ansatt.fnr} size="xsmall" />
        </HStack>
      </Box>

      <Box
        background="default"
        paddingInline="space-24 space-0"
        borderWidth="0 0 1 0"
        borderColor="neutral-subtle"
      >
        <HStack justify="space-between" align="stretch" gap="space-16" wrap>
          <HStack align="center">
            <Link as={NavLink} to={OVERSIKT_PATH} underline={false}>
              <ArrowLeftIcon aria-hidden />
              <BodyShort as="span" weight="semibold" style={{ overflowWrap: "anywhere" }}>
                Tilbake til liste av saker
              </BodyShort>
            </Link>
          </HStack>
          {!visKvittering && (
            <HStack align="stretch" wrap>
              <Tildeling sak={sak} onFeil={setTildelingError} />
              <Sakslogg sakId={sakId ?? ""} />
            </HStack>
          )}
        </HStack>
        {tildelingError && !visKvittering && (
          <Box paddingBlock="space-8" paddingInline="space-0 space-24">
            <Alert variant="error" size="small">
              {tildelingError.message}
            </Alert>
          </Box>
        )}
      </Box>

      {visKvittering && sak.forelopigVedtak ? (
        <main>
          <ForelopigVedtakKvittering sak={sak} vedtak={sak.forelopigVedtak} />
        </main>
      ) : (
        <main>
          <Tabs
            value={erArenaSak ? "soknad" : saksFane}
            onChange={(value) => setSaksFane(value as SaksFane)}
          >
            <Tabs.List aria-label="Saksinnhold">
              <Tabs.Tab value="soknad" label="Søknad" />
              {!erArenaSak && <Tabs.Tab value="sluttrapport" label="Sluttrapport" />}
              {!erArenaSak && <Tabs.Tab value="refusjon" label="Refusjon" />}
            </Tabs.List>
            <Tabs.Panel value="soknad">
              <SaksinnholdLayout
                soknad={soknad}
                midtreStandardstorrelse="45%"
                kontroll={
                  erArenaSak ? (
                    <Box padding="space-16">
                      <Alert variant="info" size="small">
                        Saken behandles i Arena. Vilkårsvurdering, vedtak og utbetaling gjøres der.
                      </Alert>
                    </Box>
                  ) : (
                    <Tabs value={fane} onChange={(value) => setFane(value as Fane)}>
                      <Tabs.List>
                        <Tabs.Tab value="vilkarsvurdering" label="Vilkårsvurdering" />
                        <Tabs.Tab value="forelopig-vedtak" label="Foreløpig vedtak" />
                      </Tabs.List>
                      <Tabs.Panel value="vilkarsvurdering">
                        <VStack
                          gap="space-16"
                          paddingBlock="space-8 space-32"
                          paddingInline="space-8 space-16"
                        >
                          {vilkårLaster ? (
                            <Loader size="medium" title="Laster vilkår" />
                          ) : vilkårError ? (
                            <Alert variant="error" size="small">
                              Kunne ikke hente vilkårene.
                            </Alert>
                          ) : (
                            <Accordion>
                              {vilkår.map((v) => (
                                <VilkårItem
                                  key={v.id}
                                  vilkår={v}
                                  isSaving={isSaving}
                                  error={lagreError}
                                  onLagre={lagreVurdering}
                                />
                              ))}
                            </Accordion>
                          )}
                          <Box paddingInline="space-16">
                            <Link
                              as="button"
                              type="button"
                              onClick={() => setFane("forelopig-vedtak")}
                            >
                              Fatte foreløpig vedtak
                              <ChevronRightIcon aria-hidden />
                            </Link>
                          </Box>
                        </VStack>
                      </Tabs.Panel>
                      <Tabs.Panel value="forelopig-vedtak">
                        <Box padding="space-16" paddingBlock="space-24 space-32">
                          <ForelopigVedtakFane
                            sak={sak}
                            vilkår={vilkår}
                            vilkårLaster={vilkårLaster}
                            vilkårFeilet={!!vilkårError}
                            tilstand={skjemaTilstand}
                            oppdater={oppdaterSkjema}
                            sendTilBeslutter={(input) => void sendTilBeslutter(input)}
                            isSaving={forelopigVedtak.isSaving}
                            sendError={forelopigVedtak.error}
                          />
                        </Box>
                      </Tabs.Panel>
                    </Tabs>
                  )
                }
              >
                <Box padding="space-16" paddingBlock="space-32">
                  <VStack gap="space-48">
                    <VStack as="section" gap="space-16">
                      <Heading level="2" size="xsmall">
                        Situasjonen
                      </Heading>
                      <Spørsmål label="Beskriv den ansattes arbeidssituasjon">
                        <BodyLong>{behovForBistand.begrunnelse}</BodyLong>
                      </Spørsmål>
                      <Spørsmål label="Beskriv ansatt sykefravær, og hvilken oppfølging og tilrettelegging dere allerede har tilbudt/prøvd ut?">
                        <BodyLong>{behovForBistand.tilrettelegging}</BodyLong>
                      </Spørsmål>
                    </VStack>

                    <VStack as="section" gap="space-16">
                      <Heading level="2" size="xsmall">
                        Ekspertbistand
                      </Heading>
                      <Spørsmål label="Hva skal eksperten hjelpe dere med?">
                        <BodyLong>{behovForBistand.behov}</BodyLong>
                      </Spørsmål>
                      <Spørsmål label="Hvor mange timer skal eksperten hjelpe dere?">
                        <BodyShort>{behovForBistand.timer} timer</BodyShort>
                      </Spørsmål>
                      <Spørsmål label="Søknadssum">
                        <BodyShort>{formatBeløp(behovForBistand.estimertKostnad)}</BodyShort>
                      </Spørsmål>
                      <Spørsmål label="Startdato">
                        <BodyShort>{formatDate(behovForBistand.startdato)}</BodyShort>
                      </Spørsmål>
                      <Spørsmål label="Sendt inn til Nav">
                        <BodyShort>{formatDate(soknad.innsendtTidspunkt)}</BodyShort>
                      </Spørsmål>
                    </VStack>

                    {/* TODO: fjernes når vedlegg kobles på ekte datakilde */}
                    {isMockEnabled() && (
                      <VStack as="section" gap="space-16">
                        <Heading level="2" size="xsmall">
                          Vedlegg (demo)
                        </Heading>
                        <PdfForhandsvisningListe filer={DEMO_VEDLEGG} />
                      </VStack>
                    )}
                  </VStack>
                </Box>
              </SaksinnholdLayout>
            </Tabs.Panel>
            {!erArenaSak && (
              <Tabs.Panel value="sluttrapport">
                <SaksinnholdLayout
                  key={`${sak.sakId}:${
                    sluttrapport && !sluttrapportError ? "med-rapport" : "uten-rapport"
                  }`}
                  soknad={soknad}
                  midtreStandardstorrelse={sluttrapport && !sluttrapportError ? "45%" : "75%"}
                  kontroll={
                    sluttrapport && !sluttrapportError ? <Sluttrapportkontroll /> : undefined
                  }
                >
                  <SluttrapportVisning
                    key={`${sak.sakId}:${sluttrapport?.opprettet ?? "ingen"}`}
                    sakId={sak.sakId}
                    sluttrapport={sluttrapport}
                    tiltaksperiode={formatPeriode(
                      sak.forelopigVedtak?.tiltaksperiodeFra ?? null,
                      sak.forelopigVedtak?.tiltaksperiodeTil ?? null
                    )}
                    error={sluttrapportError}
                    isLoading={sluttrapportLaster}
                  />
                </SaksinnholdLayout>
              </Tabs.Panel>
            )}
            {!erArenaSak && (
              <Tabs.Panel value="refusjon">
                <SaksinnholdLayout
                  key={`${sak.sakId}:${refusjon && !refusjonError ? "med-krav" : "uten-krav"}`}
                  soknad={soknad}
                  midtreStandardstorrelse={refusjon && !refusjonError ? "45%" : "75%"}
                  kontroll={
                    refusjon && !refusjonError ? (
                      <Refusjonskontroll
                        key={`${sak.sakId}:${refusjon.opprettet}`}
                        refusjon={refusjon}
                        deltaker={soknad.ansatt.navn}
                        arbeidsgiver={soknad.virksomhet.virksomhetsnavn}
                      />
                    ) : undefined
                  }
                >
                  <RefusjonskravVisning
                    key={`${sak.sakId}:${refusjon?.opprettet ?? "ingen"}`}
                    sakId={sak.sakId}
                    refusjon={refusjon}
                    error={refusjonError}
                    isLoading={refusjonLaster}
                  />
                </SaksinnholdLayout>
              </Tabs.Panel>
            )}
          </Tabs>
        </main>
      )}
    </>
  );
}
