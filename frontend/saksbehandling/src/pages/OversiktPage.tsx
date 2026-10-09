import { MenuElipsisHorizontalCircleIcon } from "@navikt/aksel-icons";
import {
  ActionMenu,
  Alert,
  BodyShort,
  Box,
  Button,
  Heading,
  Loader,
  Page,
  Table,
  Tabs,
  Tag,
  VStack,
} from "@navikt/ds-react";
import { useState } from "react";
import { useNavigate } from "react-router";
import { type Saksstatus, type SakListeElement, useSaker } from "../hooks/useSaker";
import { useTildeling } from "../hooks/useTildeling";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import classes from "../components/AppLayout.module.css";
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

const ferdigeSaksstatuser: Saksstatus[] = ["INNVILGET", "AVSLATT", "AVSLUTTET"];

function erAvsluttet(sak: SakListeElement) {
  return ferdigeSaksstatuser.includes(sak.status);
}

function TildelMegKnapp({ sakId, onError }: { sakId: string; onError: (e: Error) => void }) {
  const { tildelMeg, isSaving } = useTildeling(sakId, { onError });

  return (
    <Button
      variant="secondary"
      size="xsmall"
      loading={isSaving}
      onClick={(e) => {
        e.stopPropagation();
        void tildelMeg();
      }}
    >
      Tildel meg
    </Button>
  );
}

export default function OversiktPage() {
  const { saker, error, isLoading } = useSaker();
  const [tildelingsfeil, setTildelingsfeil] = useState<Error | null>(null);
  const innloggetAnsatt = useInnloggetAnsatt();
  const navigate = useNavigate();

  const faner = [
    {
      value: "til-godkjenning",
      label: "Til godkjenning",
      filter: () => saker.filter((s) => !s.saksbehandlerIdent && !erAvsluttet(s)),
    },
    {
      value: "mine-saker",
      label: "Mine saker",
      filter: () =>
        saker.filter((s) => !!innloggetAnsatt && s.saksbehandlerIdent === innloggetAnsatt.id),
    },
    {
      value: "pagaende",
      label: "Pågående",
      filter: () => saker.filter((s) => !erAvsluttet(s)),
    },
    {
      value: "avsluttet",
      label: "Avsluttet",
      filter: () => saker.filter(erAvsluttet),
    },
    { value: "alle", label: "Alle", filter: () => saker },
  ];

  if (isLoading) {
    return <Loader size="large" title="Laster oversikt" />;
  }

  if (error) {
    return <Tag variant="error">Kunne ikke hente saksoversikten.</Tag>;
  }

  if (innloggetAnsatt && innloggetAnsatt.enheter.length === 0) {
    return (
      <Page.Block as="main">
        <Box padding="space-24">
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
      <Box paddingInline="space-24">
        <VStack gap="space-16">
          {tildelingsfeil && (
            <Alert variant="error" size="small" closeButton onClose={() => setTildelingsfeil(null)}>
              {tildelingsfeil.message}
            </Alert>
          )}
          <Tabs defaultValue="alle">
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
                        <Table.ColumnHeader>Status</Table.ColumnHeader>
                        <Table.ColumnHeader>Arbeidsgiver</Table.ColumnHeader>
                        <Table.ColumnHeader>Deltaker</Table.ColumnHeader>
                        <Table.ColumnHeader sortable>Startdato</Table.ColumnHeader>
                        <Table.ColumnHeader sortable>Søknad mottatt</Table.ColumnHeader>
                        <Table.HeaderCell />
                      </Table.Row>
                    </Table.Header>
                    <Table.Body>
                      {filter().map((sak) => (
                        <Table.Row
                          key={sak.sakId}
                          style={{ cursor: "pointer" }}
                          onClick={() => navigate(`${OVERSIKT_PATH}/${sak.sakId}`)}
                        >
                          <Table.DataCell>
                            {sak.kanTildeleMeg ? (
                              <TildelMegKnapp sakId={sak.sakId} onError={setTildelingsfeil} />
                            ) : (
                              (sak.saksbehandlerNavn ?? "–")
                            )}
                          </Table.DataCell>
                          <Table.DataCell>{saksstatusTekst[sak.status]}</Table.DataCell>
                          <Table.DataCell>{sak.soknad.virksomhet.virksomhetsnavn}</Table.DataCell>
                          <Table.DataCell>{sak.soknad.ansattNavn}</Table.DataCell>
                          <Table.DataCell>{formatDate(sak.soknad.startdato)}</Table.DataCell>
                          <Table.DataCell>
                            {formatDate(sak.soknad.innsendtTidspunkt)}
                          </Table.DataCell>
                          <Table.DataCell>
                            <ActionMenu>
                              <ActionMenu.Trigger>
                                <Button
                                  variant="tertiary-neutral"
                                  size="xsmall"
                                  icon={<MenuElipsisHorizontalCircleIcon aria-hidden />}
                                  aria-label="Handlinger"
                                />
                              </ActionMenu.Trigger>
                              <ActionMenu.Content>
                                <ActionMenu.Item>Åpne sak</ActionMenu.Item>
                                <ActionMenu.Item>Tildel saksbehandler</ActionMenu.Item>
                              </ActionMenu.Content>
                            </ActionMenu>
                          </Table.DataCell>
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
