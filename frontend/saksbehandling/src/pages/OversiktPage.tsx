import {
  Alert,
  BodyShort,
  Box,
  Button,
  Heading,
  HStack,
  Loader,
  Page,
  Table,
  Tabs,
  Tag,
  VStack,
} from "@navikt/ds-react";
import { useState } from "react";
import { useNavigate } from "react-router";
import {
  type Oppgavetype,
  type Saksstatus,
  type SakListeElement,
  type Tildelingsrolle,
  kanTildeleMegSomSaksbehandler,
  useSaker,
} from "../hooks/useSaker";
import { brukKommunefilter, useKommunefilter } from "../hooks/useKommunefilter";
import { useTildeling } from "../hooks/useTildeling";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import classes from "../components/AppLayout.module.css";
import { AdressebeskyttelseTag } from "../components/AdressebeskyttelseTag";
import { EgenAnsattTag } from "../components/EgenAnsattTag";
import { Kommunefilter } from "../components/Kommunefilter";
import { OVERSIKT_PATH } from "../utils/constants";

function formatDate(date: string) {
  return new Intl.DateTimeFormat("nb-NO", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(date));
}

const saksstatusTekst: Record<Saksstatus, string> = {
  OPPRETTET: "Opprettet",
  UNDER_BEHANDLING: "Under behandling",
  TIL_BESLUTNING: "Til beslutning",
  INNVILGET: "Innvilget",
  AVSLATT: "Avslått",
  AVSLUTTET: "Avsluttet",
};

const oppgavetypeTekst: Record<Oppgavetype, string> = {
  SOKNAD: "Søknad",
  SOKNAD_RETUR: "Søknad - retur",
  SOKNAD_BESLUTTER: "Søknad - beslutter",
  REFUSJON: "Refusjon",
  SLUTTRAPPORT: "Sluttrapport",
};

const oppgavemerke: Record<
  Oppgavetype,
  { tekst: string; farge: "warning" | "meta-purple" | "meta-lime" }
> = {
  SOKNAD: { tekst: "SØ", farge: "warning" },
  SOKNAD_RETUR: { tekst: "SØ", farge: "warning" },
  SOKNAD_BESLUTTER: { tekst: "SØ", farge: "warning" },
  REFUSJON: { tekst: "R", farge: "meta-purple" },
  SLUTTRAPPORT: { tekst: "SR", farge: "meta-lime" },
};

function OppgavetypeCelle({ sak }: { sak: SakListeElement }) {
  if (!sak.oppgavetype) return saksstatusTekst[sak.status];
  const merke = oppgavemerke[sak.oppgavetype];
  return (
    <HStack gap="space-8" align="center" wrap={false}>
      <Tag size="xsmall" variant="outline" data-color={merke.farge} aria-hidden>
        {merke.tekst}
      </Tag>
      {oppgavetypeTekst[sak.oppgavetype]}
    </HStack>
  );
}

const avvisningstag: Record<string, { tekst: string; farge: "info" | "warning" | "danger" }> = {
  AVVIST_SKJERMING: { tekst: "Egen ansatt", farge: "info" },
  AVVIST_FORTROLIG_ADRESSE: { tekst: "Fortrolig adresse", farge: "warning" },
  AVVIST_STRENGT_FORTROLIG_ADRESSE: { tekst: "Strengt fortrolig adresse", farge: "danger" },
};

function DeltakerCelle({ sak }: { sak: SakListeElement }) {
  if (sak.deltakerTilgang?.avvist) {
    const tag = avvisningstag[sak.deltakerTilgang.kode];
    return (
      <Tag size="xsmall" variant="outline" data-color={tag?.farge ?? "neutral"}>
        {tag?.tekst ?? "Ingen tilgang"}
      </Tag>
    );
  }
  if (!sak.skjermet && !sak.adressebeskyttelse) return sak.soknad.ansattNavn ?? "";
  return (
    <HStack gap="space-8" align="center" wrap={false}>
      {sak.soknad.ansattNavn}
      <AdressebeskyttelseTag adressebeskyttelse={sak.adressebeskyttelse} />
      <EgenAnsattTag skjermet={sak.skjermet} />
    </HStack>
  );
}

function KommuneCelle({ sak, onVelg }: { sak: SakListeElement; onVelg: (nummer: string) => void }) {
  if (!sak.kommunenummer) return null;
  const navn = sak.kommunenavn ?? sak.kommunenummer;
  const nummer = sak.kommunenummer;
  return (
    <Button
      variant="tertiary"
      size="xsmall"
      aria-label={`Filtrer på ${navn}`}
      onClick={(e) => {
        e.stopPropagation();
        onVelg(nummer);
      }}
    >
      {navn}
    </Button>
  );
}

function tiltaksperiode(sak: SakListeElement) {
  if (sak.tiltaksperiodeFra && sak.tiltaksperiodeTil) {
    return `${formatDate(sak.tiltaksperiodeFra)} – ${formatDate(sak.tiltaksperiodeTil)}`;
  }
  return `${formatDate(sak.soknad.startdato)} –`;
}

const ferdigeSaksstatuser: Saksstatus[] = ["AVSLATT", "AVSLUTTET"];

function erAvsluttet(sak: SakListeElement) {
  return ferdigeSaksstatuser.includes(sak.status);
}

const sakLenke = (sak: Pick<SakListeElement, "sakId" | "oppgavetype">) => {
  const sti = `${OVERSIKT_PATH}/${sak.sakId}`;
  if (sak.oppgavetype === "REFUSJON") return `${sti}?fane=refusjon`;
  if (sak.oppgavetype === "SLUTTRAPPORT") return `${sti}?fane=sluttrapport`;
  return sti;
};

function TildelMegKnapp({
  sakId,
  lenke,
  rolle,
  onError,
}: {
  sakId: string;
  lenke: string;
  rolle: Tildelingsrolle;
  onError: (e: Error) => void;
}) {
  const { tildelMeg, isSaving } = useTildeling(sakId, { rolle, onError });
  const navigate = useNavigate();

  return (
    <Button
      variant="secondary"
      size="xsmall"
      loading={isSaving}
      onClick={async (e) => {
        e.stopPropagation();
        if (await tildelMeg()) navigate(lenke);
      }}
    >
      Tildel meg
    </Button>
  );
}

export default function OversiktPage() {
  const { saker: alleSaker, tilgangssjekkFeilet, error, isLoading } = useSaker();
  const [tildelingsfeil, setTildelingsfeil] = useState<Error | null>(null);
  const innloggetAnsatt = useInnloggetAnsatt();
  const navigate = useNavigate();
  const enheter = innloggetAnsatt?.enheter ?? [];
  const kommunefilter = useKommunefilter(enheter);
  const {
    synlige: saker,
    skjulte,
    kommuneValg,
  } = brukKommunefilter(
    alleSaker,
    kommunefilter.filter,
    kommunefilter.lagredeKommuner,
    tilgangssjekkFeilet
  );
  const velgKommune = (nummer: string) => {
    const { filter } = kommunefilter;
    if (!filter.kommuner.includes(nummer)) {
      kommunefilter.lagre({ ...filter, kommuner: [...filter.kommuner, nummer] });
    }
  };

  const faner = [
    {
      value: "oppgaver-som-venter",
      label: "Oppgaver som venter",
      filter: () => saker.filter((s) => s.harOppgaveForMeg),
    },
    {
      value: "mine-saker",
      label: "Mine saker",
      filter: () =>
        saker.filter(
          (s) =>
            !!innloggetAnsatt &&
            (s.saksbehandlerIdent === innloggetAnsatt.id || s.beslutterIdent === innloggetAnsatt.id)
        ),
    },
    {
      value: "gjennomfores",
      label: "Gjennomføres",
      filter: () => saker.filter((s) => s.status === "INNVILGET"),
    },
    {
      value: "avsluttet",
      label: "Avsluttet",
      filter: () => saker.filter(erAvsluttet),
    },
    { value: "alle", label: "Alle", filter: () => saker },
  ];

  if (isLoading || kommunefilter.isLoading) {
    return <Loader size="large" title="Laster oversikt" />;
  }

  if (error) {
    return <Tag variant="error">Kunne ikke hente saksoversikten.</Tag>;
  }

  if (innloggetAnsatt && innloggetAnsatt.enheter.length === 0) {
    return (
      <Page.Block as="main">
        <Box padding="space-24">
          <Heading level="1" size="medium" spacing>
            Saker
          </Heading>
          <Alert variant="warning">
            <Heading spacing size="small" level="2">
              Du er ikke knyttet til noen enheter
            </Heading>
            <BodyShort>
              Derfor ser du ingen saker. Ta kontakt med lederen din eller den som styrer tilgang hos
              dere, slik at du får tilgang til enheten du jobber i.
            </BodyShort>
          </Alert>
        </Box>
      </Page.Block>
    );
  }

  return (
    <Page.Block as="main">
      <Box paddingInline="space-24" paddingBlock="space-16 space-0">
        <VStack gap="space-16">
          <Heading level="1" size="medium">
            Saker
          </Heading>
          {tilgangssjekkFeilet && (
            <Alert variant="warning" size="small">
              Vi kunne ikke sjekke tilgangen din. Deltaker og arbeidsgiver vises ikke. Prøv igjen om
              litt.
            </Alert>
          )}
          {kommunefilter.lagringFeilet && (
            <Alert variant="warning" size="small">
              Vi kunne ikke lagre filteret ditt.
            </Alert>
          )}
          {tildelingsfeil && (
            <Alert variant="error" size="small" closeButton onClose={() => setTildelingsfeil(null)}>
              {tildelingsfeil.message}
            </Alert>
          )}
          <Kommunefilter
            enheter={enheter}
            filter={kommunefilter.filter}
            kommuneValg={kommuneValg}
            skjulte={skjulte}
            onEndre={kommunefilter.lagre}
          />
          <Tabs defaultValue="oppgaver-som-venter">
            <Tabs.List>
              {faner.map(({ value, label, filter }) => (
                <Tabs.Tab key={value} value={value} label={`${label} (${filter().length})`} />
              ))}
            </Tabs.List>
            {faner.map(({ value, filter }) => (
              <Tabs.Panel key={value} value={value}>
                <div className={classes.tableWrapper}>
                  <Table zebraStripes size="small">
                    <Table.Header>
                      <Table.Row>
                        <Table.ColumnHeader sortable>Saksbehandler</Table.ColumnHeader>
                        <Table.ColumnHeader>Beslutter</Table.ColumnHeader>
                        <Table.ColumnHeader>Oppgavetype</Table.ColumnHeader>
                        <Table.ColumnHeader>Arbeidsgiver</Table.ColumnHeader>
                        <Table.ColumnHeader>Deltaker</Table.ColumnHeader>
                        <Table.ColumnHeader>Kommune</Table.ColumnHeader>
                        <Table.ColumnHeader sortable>Tiltaksperiode</Table.ColumnHeader>
                        <Table.ColumnHeader sortable>Mottatt</Table.ColumnHeader>
                      </Table.Row>
                    </Table.Header>
                    <Table.Body>
                      {filter().map((sak) => (
                        <Table.Row
                          key={`${sak.sakId}-${sak.oppgavetype ?? "status"}`}
                          style={{ cursor: "pointer" }}
                          onClick={() => navigate(sakLenke(sak))}
                        >
                          <Table.DataCell>
                            {kanTildeleMegSomSaksbehandler(sak) ? (
                              <TildelMegKnapp
                                sakId={sak.sakId}
                                lenke={sakLenke(sak)}
                                rolle="saksbehandler"
                                onError={setTildelingsfeil}
                              />
                            ) : (
                              (sak.saksbehandlerNavn ?? "–")
                            )}
                          </Table.DataCell>
                          <Table.DataCell>
                            {sak.kanTildeleMegSomBeslutter ? (
                              <TildelMegKnapp
                                sakId={sak.sakId}
                                lenke={sakLenke(sak)}
                                rolle="beslutter"
                                onError={setTildelingsfeil}
                              />
                            ) : (
                              (sak.beslutterNavn ?? "–")
                            )}
                          </Table.DataCell>
                          <Table.DataCell>
                            <OppgavetypeCelle sak={sak} />
                          </Table.DataCell>
                          <Table.DataCell>{sak.soknad.virksomhet?.virksomhetsnavn}</Table.DataCell>
                          <Table.DataCell>
                            <DeltakerCelle sak={sak} />
                          </Table.DataCell>
                          <Table.DataCell>
                            <KommuneCelle sak={sak} onVelg={velgKommune} />
                          </Table.DataCell>
                          <Table.DataCell style={{ whiteSpace: "nowrap" }}>
                            {tiltaksperiode(sak)}
                          </Table.DataCell>
                          <Table.DataCell>{formatDate(sak.mottatt)}</Table.DataCell>
                        </Table.Row>
                      ))}
                    </Table.Body>
                  </Table>
                </div>
              </Tabs.Panel>
            ))}
          </Tabs>
        </VStack>
      </Box>
    </Page.Block>
  );
}
