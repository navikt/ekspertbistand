import { ArrowLeftIcon, CheckmarkCircleIcon, ChevronRightIcon } from "@navikt/aksel-icons";
import {
  Accordion,
  ActionMenu,
  Alert,
  BodyLong,
  BodyShort,
  Box,
  Button,
  CopyButton,
  HGrid,
  HStack,
  Heading,
  Label,
  Link,
  Loader,
  Tabs,
  VStack,
} from "@navikt/ds-react";
import { useState } from "react";
import { Group, Panel } from "react-resizable-panels";
import { NavLink, useNavigate, useParams } from "react-router";
import HeaderKnapp from "../components/HeaderKnapp";
import { DataRad, InfoKort } from "../components/InfoKort";
import KolonneSeparator from "../components/KolonneSeparator";
import Sakslogg from "../components/Sakslogg";
import VilkårItem from "../components/VilkårItem";
import { type SakDetaljer, useSak } from "../hooks/useSak";
import { useTildeling } from "../hooks/useTildeling";
import { useVilkår } from "../hooks/useVilkår";
import { useVilkårsvurdering } from "../hooks/useVilkårsvurdering";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import type { HttpError } from "../utils/http";
import { GOSYS_URL, MODIA_URL, OVERSIKT_PATH } from "../utils/constants";

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

type TildelingProps = {
  sak: SakDetaljer;
  tildelMeg: () => Promise<boolean>;
  frigjoer: () => Promise<boolean>;
  isSaving: boolean;
};

function Tildeling({ sak, tildelMeg, frigjoer, isSaving }: TildelingProps) {
  const innloggetAnsatt = useInnloggetAnsatt();
  const navigate = useNavigate();
  const [menyÅpen, setMenyÅpen] = useState(false);

  const frigjoerOgGåTilOversikten = async () => {
    if (await frigjoer()) navigate(OVERSIKT_PATH);
  };

  if (innloggetAnsatt && sak.saksbehandlerIdent === innloggetAnsatt.id) {
    return (
      <HStack gap="space-16" align="stretch">
        <HStack align="center">
          <BodyShort>Saken er tildelt meg</BodyShort>
        </HStack>
        <ActionMenu open={menyÅpen} onOpenChange={setMenyÅpen}>
          <ActionMenu.Trigger>
            <HeaderKnapp tekst="Meny" åpen={menyÅpen} loading={isSaving} />
          </ActionMenu.Trigger>
          <ActionMenu.Content>
            <ActionMenu.Group label="Legg behandlingen tilbake">
              <ActionMenu.Item onSelect={() => void frigjoerOgGåTilOversikten()}>
                Frigjør oppgave
              </ActionMenu.Item>
            </ActionMenu.Group>
          </ActionMenu.Content>
        </ActionMenu>
      </HStack>
    );
  }

  return (
    <HStack gap="space-16" align="center">
      {sak.saksbehandlerNavn && <BodyShort>Saken er tildelt {sak.saksbehandlerNavn}</BodyShort>}
      {sak.kanTildeleMeg && (
        <Button
          size="small"
          variant="secondary"
          loading={isSaving}
          onClick={() => void tildelMeg()}
        >
          Tildel meg
        </Button>
      )}
    </HStack>
  );
}

type Fane = "vilkarsvurdering" | "forelopig-vedtak";

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

export default function SakPage() {
  const { sakId: sakIdParam } = useParams<{ sakId: string }>();
  const { sak, error, isLoading } = useSak(sakIdParam ?? "");
  const sakId = sak?.sakId;
  const { vilkår, error: vilkårError, isLoading: vilkårLaster } = useVilkår(sakId);
  const { lagreVurdering, isSaving, error: lagreError } = useVilkårsvurdering(sakId ?? "");
  const { error: tildelingError, ...tildeling } = useTildeling(sakId ?? "");
  const [fane, setFane] = useState<Fane>("vilkarsvurdering");

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
  const { ansatt, virksomhet, ekspert, behovForBistand } = soknad;

  return (
    <>
      <Box background="soft" paddingBlock="space-8" paddingInline="space-24">
        <HStack gap="space-8" align="center">
          <BodyShort weight="semibold">{ansatt.navn}</BodyShort>
          <CopyButton copyText={ansatt.navn} size="xsmall" />
          <BodyShort>/</BodyShort>
          <BodyShort>{ansatt.fnr}</BodyShort>
          <CopyButton copyText={ansatt.fnr} size="xsmall" />
        </HStack>
      </Box>

      <Box
        background="default"
        paddingInline="space-24 space-0"
        borderWidth="0 0 1 0"
        borderColor="neutral-subtle"
      >
        <HStack justify="space-between" align="stretch" gap="space-16">
          <HStack align="center">
            <Link as={NavLink} to={OVERSIKT_PATH} underline={false}>
              <ArrowLeftIcon aria-hidden />
              <BodyShort as="span" weight="semibold">
                Tilbake til liste av saker
              </BodyShort>
            </Link>
          </HStack>
          <HStack gap="space-16" align="stretch">
            <Tildeling sak={sak} {...tildeling} />
            <Sakslogg sakId={sakId ?? ""} />
          </HStack>
        </HStack>
        {tildelingError && (
          <Box paddingBlock="space-8" paddingInline="space-0 space-24">
            <Alert variant="error" size="small">
              {tildelingError.message}
            </Alert>
          </Box>
        )}
      </Box>

      <main>
        <Group orientation="horizontal" style={{ minHeight: "calc(100vh - 160px)" }}>
          {/* Venstre kolonne */}
          <Panel defaultSize={25} minSize={15}>
            <Box padding="space-16" paddingBlock="space-32">
              <VStack gap="space-32">
                <InfoKort tittel="Arbeidsgiver">
                  <HGrid columns="repeat(auto-fit, minmax(160px, 1fr))" gap="space-8 space-16">
                    <DataRad label="Navn" value={virksomhet.virksomhetsnavn} />
                    <DataRad label="Kontaktperson" value={virksomhet.kontaktperson.navn} />
                    <DataRad label="Org.nr" value={virksomhet.virksomhetsnummer} />
                    <DataRad label="E-post" value={virksomhet.kontaktperson.epost} />
                    <DataRad
                      label="Beliggenhetsadresse"
                      value={virksomhet.beliggenhetsadresse ?? "–"}
                    />
                    <DataRad label="Telefon" value={virksomhet.kontaktperson.telefonnummer} />
                  </HGrid>
                </InfoKort>

                <InfoKort tittel="Deltakere">
                  <HGrid columns="repeat(auto-fit, minmax(160px, 1fr))" gap="space-8 space-16">
                    <VStack gap="space-8">
                      <DataRad label="Navn" value={ansatt.navn} />
                      <DataRad label="Fødselsnummer" value={ansatt.fnr} />
                      <VStack gap="space-2">
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
                    <VStack gap="space-12">
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
                  <VStack gap="space-8">
                    <DataRad label="Navn" value={ekspert.navn} />
                    <DataRad
                      label="Godkjent utdanning/autorisasjon"
                      value={
                        ekspert.godkjentUtdanningEllerAutorisasjon.join(", ") || ekspert.kompetanse
                      }
                    />
                    <DataRad
                      label="Tilknyttet virksomhet"
                      value={ekspert.virksomhetNavn ?? ekspert.virksomhet}
                    />
                    <DataRad label="Org.nr." value={ekspert.virksomhetOrgnr ?? "–"} />
                  </VStack>
                </InfoKort>
              </VStack>
            </Box>
          </Panel>

          <KolonneSeparator />

          {/* Midtre kolonne */}
          <Panel defaultSize={45} minSize={30}>
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
              </VStack>
            </Box>
          </Panel>

          <KolonneSeparator />

          {/* Høyre kolonne */}
          <Panel defaultSize={30} minSize={20}>
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
                    <Link as="button" type="button" onClick={() => setFane("forelopig-vedtak")}>
                      Fatte foreløpig vedtak
                      <ChevronRightIcon aria-hidden />
                    </Link>
                  </Box>
                </VStack>
              </Tabs.Panel>
              <Tabs.Panel value="forelopig-vedtak">
                <Box padding="space-16">
                  <BodyShort>Foreløpig vedtak er ikke tilgjengelig ennå.</BodyShort>
                </Box>
              </Tabs.Panel>
            </Tabs>
          </Panel>
        </Group>
      </main>
    </>
  );
}
