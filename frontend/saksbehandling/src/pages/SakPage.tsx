import { ArrowLeftIcon, CheckmarkCircleIcon, ChevronRightIcon } from "@navikt/aksel-icons";
import {
  Accordion,
  BodyLong,
  BodyShort,
  Box,
  CopyButton,
  HGrid,
  HStack,
  Heading,
  Label,
  Link,
  Loader,
  Tabs,
  Tag,
  VStack,
} from "@navikt/ds-react";
import { useState } from "react";
import { Group, Panel } from "react-resizable-panels";
import { NavLink, useParams } from "react-router";
import { DataRad, InfoKort } from "../components/InfoKort";
import KolonneSeparator from "../components/KolonneSeparator";
import VilkårItem from "../components/VilkårItem";
import { useSak } from "../hooks/useSak";
import { useVilkårsvurdering } from "../hooks/useVilkårsvurdering";
import { GOSYS_URL, MODIA_URL, OVERSIKT_PATH } from "../utils/constants";

function formatDate(iso: string) {
  return new Intl.DateTimeFormat("nb-NO", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(iso));
}

function Spørsmål({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <VStack gap="space-4">
      <Label as="h3">{label}</Label>
      {children}
    </VStack>
  );
}

type Fane = "vilkarsvurdering" | "forelopig-vedtak";

export default function SakPage() {
  const { sakId } = useParams<{ sakId: string }>();
  const { sak, error, isLoading } = useSak(sakId ?? "");
  const { lagreVurdering, isSaving, error: lagreError } = useVilkårsvurdering(sakId ?? "");
  const [fane, setFane] = useState<Fane>("vilkarsvurdering");

  if (isLoading) return <Loader size="large" title="Laster sak" />;
  if (error || !sak) return <Tag variant="error">Kunne ikke hente saken.</Tag>;

  const { deltaker, arbeidsgiver, ekspert, situasjon, ekspertbistand, vilkår } = sak;

  return (
    <>
      <Box background="soft" paddingBlock="space-8" paddingInline="space-24">
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

      <Box
        background="default"
        paddingBlock="space-12"
        paddingInline="space-24"
        borderWidth="0 0 1 0"
        borderColor="neutral-subtle"
      >
        <Link as={NavLink} to={OVERSIKT_PATH} underline={false}>
          <ArrowLeftIcon aria-hidden />
          <BodyShort as="span" weight="semibold">
            Tilbake til liste av saker
          </BodyShort>
        </Link>
      </Box>

      <main>
        <Group orientation="horizontal" style={{ minHeight: "calc(100vh - 160px)" }}>
          {/* Venstre kolonne */}
          <Panel defaultSize={25} minSize={15}>
            <Box padding="space-16" paddingBlock="space-32">
              <VStack gap="space-32">
                <InfoKort tittel="Arbeidsgiver">
                  <HGrid columns="repeat(auto-fit, minmax(160px, 1fr))" gap="space-8 space-16">
                    <DataRad label="Navn" value={arbeidsgiver.navn} />
                    <DataRad label="Kontaktperson" value={arbeidsgiver.kontaktperson} />
                    <DataRad label="Org.nr" value={arbeidsgiver.orgNr} />
                    <DataRad label="E-post" value={arbeidsgiver.epost} />
                    <DataRad
                      label="Beliggenhetsadresse"
                      value={arbeidsgiver.beliggenhetssadresse}
                    />
                    <DataRad label="Telefon" value={arbeidsgiver.telefon} />
                  </HGrid>
                </InfoKort>

                <InfoKort tittel="Deltakere">
                  <HGrid columns="repeat(auto-fit, minmax(160px, 1fr))" gap="space-8 space-16">
                    <VStack gap="space-8">
                      <DataRad label="Navn" value={deltaker.navn} />
                      <DataRad label="Fødselsnummer" value={deltaker.fnr} />
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
                    <DataRad label="Godkjent utdanning/autorisasjon" value={ekspert.kompetanse} />
                    <DataRad label="Tilknyttet virksomhet" value={ekspert.tilknyttetVirksomhet} />
                    <DataRad label="Org.nr." value={ekspert.orgNr} />
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
                    <BodyLong>{situasjon.arbeidssituasjon}</BodyLong>
                  </Spørsmål>
                  <Spørsmål label="Beskriv ansatt sykefravær, og hvilken oppfølging og tilrettelegging dere allerede har tilbudt/prøvd ut?">
                    <BodyLong>{situasjon.sykefravær}</BodyLong>
                  </Spørsmål>
                </VStack>

                <VStack as="section" gap="space-16">
                  <Heading level="2" size="xsmall">
                    Ekspertbistand
                  </Heading>
                  <Spørsmål label="Hva skal eksperten hjelpe dere med?">
                    <BodyLong>{ekspertbistand.hvaHjelpeMed}</BodyLong>
                  </Spørsmål>
                  <Spørsmål label="Hvor mange timer skal eksperten hjelpe dere?">
                    <BodyShort>{ekspertbistand.antallTimer} timer</BodyShort>
                  </Spørsmål>
                  <Spørsmål label="Søknadssum">
                    <BodyShort>{ekspertbistand.søknadssum.toLocaleString("nb-NO")} kr</BodyShort>
                  </Spørsmål>
                  <Spørsmål label="Startdato">
                    <BodyShort>{formatDate(ekspertbistand.startdato)}</BodyShort>
                  </Spørsmål>
                  <Spørsmål label="Sendt inn til Nav">
                    <BodyShort>{formatDate(ekspertbistand.sendtInnTilNav)}</BodyShort>
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
                <VStack gap="space-16" paddingBlock="space-8 space-32" paddingInline="space-8 space-16">
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
