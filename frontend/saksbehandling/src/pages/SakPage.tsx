import { CheckmarkCircleFillIcon, ClockDashedIcon, ArrowLeftIcon } from "@navikt/aksel-icons";
import {
  BodyLong,
  BodyShort,
  Accordion,
  Box,
  Button,
  CopyButton,
  HGrid,
  HStack,
  Heading,
  Label,
  Link,
  Loader,
  Page,
  Tag,
  VStack,
} from "@navikt/ds-react";
import { Group, Panel, Separator } from "react-resizable-panels";
import { NavLink, useParams } from "react-router";
import { useSak, type Vilkår } from "../hooks/useSak";
import { GOSYS_URL, MODIA_URL, OVERSIKT_PATH } from "../utils/constants";

function formatDate(iso: string) {
  return new Intl.DateTimeFormat("nb-NO", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(iso));
}

function DataRad({ label, value }: { label: string; value: string }) {
  return (
    <VStack gap="space-2">
      <Label size="small">{label}</Label>
      <BodyShort size="small">{value}</BodyShort>
    </VStack>
  );
}

function InfoKort({ tittel, children }: { tittel: string; children: React.ReactNode }) {
  return (
    <Box background="soft" padding="space-16" borderRadius="8">
      <VStack gap="space-16">
        <Heading level="2" size="small">
          {tittel}
        </Heading>
        {children}
      </VStack>
    </Box>
  );
}

function VilkårItem({ vilkår }: { vilkår: Vilkår }) {
  const oppfylt = vilkår.status === "oppfylt";
  return (
    <Accordion.Item defaultOpen>
      <Accordion.Header>
        <HStack gap="space-8" align="center">
          {oppfylt ? (
            <CheckmarkCircleFillIcon
              aria-hidden
              style={{ color: "var(--ax-color-success-icon)", flexShrink: 0 }}
              fontSize="1.25rem"
            />
          ) : (
            <ClockDashedIcon
              aria-hidden
              style={{ color: "var(--ax-color-warning-icon)", flexShrink: 0 }}
              fontSize="1.25rem"
            />
          )}
          {vilkår.tittel}
        </HStack>
      </Accordion.Header>
      <Accordion.Content>
        <VStack gap="space-8">
          <BodyShort size="small">{vilkår.beskrivelse}</BodyShort>
          <HStack gap="space-8">
            {oppfylt && (
              <>
                <Tag variant="success" size="xsmall">
                  Oppfylt
                </Tag>
                {vilkår.automatisk && (
                  <Tag variant="neutral" size="xsmall">
                    Automatisk
                  </Tag>
                )}
              </>
            )}
            {!oppfylt && (
              <Button variant="primary" size="xsmall">
                Vurdere manuelt
              </Button>
            )}
          </HStack>
        </VStack>
      </Accordion.Content>
    </Accordion.Item>
  );
}

export default function SakPage() {
  const { sakId } = useParams<{ sakId: string }>();
  const { sak, error, isLoading } = useSak(sakId ?? "");

  if (isLoading) return <Loader size="large" title="Laster sak" />;
  if (error || !sak) return <Tag variant="error">Kunne ikke hente saken.</Tag>;

  const { deltaker, arbeidsgiver, ekspert, situasjon, ekspertbistand, vilkår } = sak;

  return (
    <>
      {/* Navn-linje: grå bakgrunn */}
      <Box background="soft" paddingBlock="space-12" paddingInline="space-32">
        <HStack gap="space-8" align="center">
          <BodyShort weight="semibold">
            {deltaker.navn} ({deltaker.alder} år)
          </BodyShort>
          <CopyButton copyText={deltaker.navn} size="xsmall" />
          <BodyShort>/</BodyShort>
          <BodyShort>{deltaker.fnr}</BodyShort>
          <CopyButton copyText={deltaker.fnr.replace(/\s/g, "")} size="xsmall" />
        </HStack>
      </Box>

      {/* Breadcrumb-linje: hvit bakgrunn */}
      <Box
        background="default"
        paddingBlock="space-8"
        paddingInline="space-32"
        borderWidth="0 0 1 0"
        borderColor="neutral-subtle"
      >
        <Link as={NavLink} to={OVERSIKT_PATH}>
          <HStack gap="space-4" align="center">
            <ArrowLeftIcon aria-hidden />
            Tilbake til liste av saker
          </HStack>
        </Link>
      </Box>

      {/* 3-kolonne layout med dragbare skillelinjer */}
      <Page.Block gutters as="main">
        <Box paddingBlock="space-24">
          <Group orientation="horizontal" style={{ minHeight: "calc(100vh - 200px)" }}>
            {/* Venstre kolonne */}
            <Panel defaultSize={22} minSize={15}>
              <VStack gap="space-16">
                <InfoKort tittel="Arbeidsgiver">
                  <HGrid columns="repeat(auto-fit, minmax(120px, 1fr))" gap="space-12">
                    <DataRad label="Navn" value={arbeidsgiver.navn} />
                    <DataRad label="Kontaktperson" value={arbeidsgiver.kontaktperson} />
                    <DataRad label="Org.nr" value={arbeidsgiver.orgNr} />
                    <DataRad label="E-post" value={arbeidsgiver.epost} />
                    <DataRad
                      label="Beliggenhetssadresse"
                      value={arbeidsgiver.beliggenhetssadresse}
                    />
                    <DataRad label="Telefon" value={arbeidsgiver.telefon} />
                  </HGrid>
                </InfoKort>

                <InfoKort tittel="Deltakere">
                  <VStack gap="space-8">
                    <DataRad label="Navn" value={deltaker.navn} />
                    <HStack gap="space-8" wrap>
                      <Link href={`${MODIA_URL}/sykefravær`} target="modia">
                        Sykefraværshistorikk
                      </Link>
                      <Link href={`${MODIA_URL}/person`} target="modia">
                        Personoversikt - Modia
                      </Link>
                      <Link href={`${MODIA_URL}/aktivitetsplan`} target="modia">
                        Aktivitesplan - Modia
                      </Link>
                      <Link href={GOSYS_URL} target="gosys">
                        Gosys - personmappe
                      </Link>
                    </HStack>
                    <DataRad label="Fødselnummer" value={deltaker.fnr} />
                    <VStack gap="space-2">
                      <Label size="small">Arbeidsforhold</Label>
                      <Link href="#" target="_blank">
                        <HStack gap="space-4" align="center">
                          <CheckmarkCircleFillIcon
                            aria-hidden
                            style={{ color: "var(--ax-color-success-icon)" }}
                          />
                          Se Aa-registret
                        </HStack>
                      </Link>
                    </VStack>
                  </VStack>
                </InfoKort>

                <InfoKort tittel="Ekspert">
                  <HGrid columns="repeat(auto-fit, minmax(120px, 1fr))" gap="space-12">
                    <DataRad label="Navn" value={ekspert.navn} />
                    <DataRad label="Tilknyttet virksomhet" value={ekspert.tilknyttetVirksomhet} />
                    <DataRad label="Kompetanse/authorisasjon" value={ekspert.kompetanse} />
                    <DataRad label="Org.nr" value={ekspert.orgNr} />
                  </HGrid>
                </InfoKort>
              </VStack>
            </Panel>

            <Separator
              style={{
                width: "16px",
                background: "transparent",
                cursor: "col-resize",
                position: "relative",
                flexShrink: 0,
              }}
            >
              <div
                style={{
                  position: "absolute",
                  top: 0,
                  bottom: 0,
                  left: "50%",
                  transform: "translateX(-50%)",
                  width: "1px",
                  background: "var(--ax-border-divider)",
                }}
              />
              <div
                style={{
                  position: "absolute",
                  top: "50%",
                  left: "50%",
                  transform: "translate(-50%, -50%)",
                  width: "4px",
                  height: "32px",
                  borderRadius: "2px",
                  background: "var(--ax-border-strong)",
                  opacity: 0.5,
                }}
              />
            </Separator>

            {/* Midtre kolonne */}
            <Panel defaultSize={52} minSize={30}>
              <Box background="soft" padding="space-24" borderRadius="8" style={{ height: "100%" }}>
                <VStack gap="space-24">
                  <VStack gap="space-16">
                    <Heading level="2" size="medium">
                      Situasjonen
                    </Heading>
                    <VStack gap="space-8">
                      <Label>Beskriv den ansattes arbeidssituasjon</Label>
                      <BodyLong size="small">{situasjon.arbeidssituasjon}</BodyLong>
                    </VStack>
                    <VStack gap="space-8">
                      <Label>
                        Beskriv ansatt sykefravær, og hvilken oppfølging og tilrettelegging dere
                        allerede har tilbudt/prøvd ut?
                      </Label>
                      <BodyLong size="small">{situasjon.sykefravær}</BodyLong>
                    </VStack>
                  </VStack>

                  <hr
                    style={{
                      margin: 0,
                      border: "none",
                      borderTop: "1px solid var(--ax-border-divider)",
                    }}
                  />

                  <VStack gap="space-16">
                    <Heading level="2" size="medium">
                      Ekspertbistand
                    </Heading>
                    <VStack gap="space-8">
                      <Label>Hva skal eksperten hjelpe dere med?</Label>
                      <BodyLong size="small">{ekspertbistand.hvaHjelpeMed}</BodyLong>
                    </VStack>
                    <VStack gap="space-4">
                      <Label>Hvor mange timer skal eksperten hjelpe dere?</Label>
                      <BodyShort size="small">{ekspertbistand.antallTimer} timer</BodyShort>
                    </VStack>
                    <VStack gap="space-4">
                      <Label>Søknadssum</Label>
                      <BodyShort size="small">
                        {ekspertbistand.søknadssum.toLocaleString("nb-NO")} kr
                      </BodyShort>
                    </VStack>
                    <VStack gap="space-4">
                      <Label>Startdato</Label>
                      <BodyShort size="small">{formatDate(ekspertbistand.startdato)}</BodyShort>
                    </VStack>
                    <VStack gap="space-4">
                      <Label>Sendt inn til Nav</Label>
                      <BodyShort size="small">
                        {formatDate(ekspertbistand.sendtInnTilNav)}
                      </BodyShort>
                    </VStack>
                  </VStack>
                </VStack>
              </Box>
            </Panel>

            <Separator
              style={{
                width: "16px",
                background: "transparent",
                cursor: "col-resize",
                position: "relative",
                flexShrink: 0,
              }}
            >
              <div
                style={{
                  position: "absolute",
                  top: 0,
                  bottom: 0,
                  left: "50%",
                  transform: "translateX(-50%)",
                  width: "1px",
                  background: "var(--ax-border-divider)",
                }}
              />
              <div
                style={{
                  position: "absolute",
                  top: "50%",
                  left: "50%",
                  transform: "translate(-50%, -50%)",
                  width: "4px",
                  height: "32px",
                  borderRadius: "2px",
                  background: "var(--ax-border-strong)",
                  opacity: 0.5,
                }}
              />
            </Separator>

            {/* Høyre kolonne */}
            <Panel defaultSize={26} minSize={18}>
              <Box background="soft" padding="space-16" borderRadius="8" style={{ height: "100%" }}>
                <VStack gap="space-16">
                  <Heading level="2" size="small">
                    Vilkårsvurdering
                  </Heading>
                  <Accordion size="small">
                    {vilkår.map((v) => (
                      <VilkårItem key={v.id} vilkår={v} />
                    ))}
                  </Accordion>
                </VStack>
              </Box>
            </Panel>
          </Group>
        </Box>
      </Page.Block>
    </>
  );
}
