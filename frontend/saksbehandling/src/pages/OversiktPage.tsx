import { MenuElipsisHorizontalCircleIcon } from "@navikt/aksel-icons";
import {
  ActionMenu,
  Box,
  Button,
  HStack,
  Loader,
  Page,
  Table,
  Tabs,
  Tag,
  VStack,
} from "@navikt/ds-react";
import { useNavigate } from "react-router";
import { useOversikt } from "../hooks/useOversikt";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import classes from "../components/AppLayout.module.css";

function formatDate(date: string) {
  return new Intl.DateTimeFormat("nb-NO", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(date));
}

function OppgavetypeKode({ type }: { type: string }) {
  const kode = type.startsWith("Søknad") ? "Sø" : "R";
  return (
    <Tag variant="neutral" size="small">
      {kode}
    </Tag>
  );
}

export default function OversiktPage() {
  const { saker, error, isLoading } = useOversikt();
  const innloggetAnsatt = useInnloggetAnsatt();
  const navigate = useNavigate();

  const faner = [
    {
      value: "til-godkjenning",
      label: "Til godkjenning",
      filter: () => saker.filter((s) => s.saksbehandler === null),
    },
    {
      value: "mine-saker",
      label: "Mine saker",
      filter: () => saker.filter((s) => s.saksbehandler === innloggetAnsatt?.navn),
    },
    {
      value: "pagaende",
      label: "Pågående",
      filter: () => saker.filter((s) => s.status !== "Ferdigstilt"),
    },
    {
      value: "avsluttet",
      label: "Avsluttet",
      filter: () => saker.filter((s) => s.status === "Ferdigstilt"),
    },
    { value: "alle", label: "Alle", filter: () => saker },
  ];

  if (isLoading) {
    return <Loader size="large" title="Laster oversikt" />;
  }

  if (error) {
    return <Tag variant="error">Kunne ikke hente saksoversikten.</Tag>;
  }

  return (
    <Page.Block as="main">
      <Box paddingInline="space-24">
        <VStack gap="space-0">
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
                        <Table.ColumnHeader>Oppgavetype</Table.ColumnHeader>
                        <Table.ColumnHeader>Arbeidsgiver</Table.ColumnHeader>
                        <Table.ColumnHeader>Deltakere</Table.ColumnHeader>
                        <Table.ColumnHeader sortable>Tiltaksperiode</Table.ColumnHeader>
                        <Table.ColumnHeader sortable>Søknad mottatt</Table.ColumnHeader>
                        <Table.HeaderCell />
                      </Table.Row>
                    </Table.Header>
                    <Table.Body>
                      {filter().map((sak) => (
                        <Table.Row
                          key={sak.id}
                          style={{ cursor: "pointer" }}
                          onClick={() => navigate(`/oversikt/${sak.id}`)}
                        >
                          <Table.DataCell>
                            {sak.saksbehandler ? (
                              sak.saksbehandler
                            ) : (
                              <Button variant="secondary" size="xsmall">
                                Tildel meg
                              </Button>
                            )}
                          </Table.DataCell>
                          <Table.DataCell>
                            <HStack gap="space-8" align="center">
                              <OppgavetypeKode type={sak.oppgavetype} />
                              {sak.oppgavetype}
                            </HStack>
                          </Table.DataCell>
                          <Table.DataCell>{sak.virksomhet}</Table.DataCell>
                          <Table.DataCell>{sak.deltaker}</Table.DataCell>
                          <Table.DataCell>
                            <HStack gap="space-8">
                              <span>{formatDate(sak.tiltaksperiodeFra)}</span>
                              <span>{formatDate(sak.tiltaksperiodeTil)}</span>
                            </HStack>
                          </Table.DataCell>
                          <Table.DataCell>{formatDate(sak.opprettetDato)}</Table.DataCell>
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
