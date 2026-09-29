import { MenuElipsisHorizontalCircleIcon } from "@navikt/aksel-icons";
import { ActionMenu, Box, Button, Loader, Page, Table, Tabs, Tag, VStack } from "@navikt/ds-react";
import { useNavigate } from "react-router";
import { type Saksstatus, type SoknadListeElement, useSoknader } from "../hooks/useSoknader";
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

function statusTekst(soknad: SoknadListeElement) {
  if (soknad.sak) return saksstatusTekst[soknad.sak.status];
  return soknad.soknadStatus === "avlyst" ? "Avlyst" : "Mottatt";
}

function erAvsluttet(soknad: SoknadListeElement) {
  if (soknad.sak) return ferdigeSaksstatuser.includes(soknad.sak.status);
  return soknad.soknadStatus === "avlyst";
}

export default function OversiktPage() {
  const { soknader, error, isLoading } = useSoknader();
  const innloggetAnsatt = useInnloggetAnsatt();
  const navigate = useNavigate();

  const faner = [
    {
      value: "til-godkjenning",
      label: "Til godkjenning",
      filter: () => soknader.filter((s) => !s.sak?.saksbehandlerIdent && !erAvsluttet(s)),
    },
    {
      value: "mine-saker",
      label: "Mine saker",
      filter: () =>
        soknader.filter(
          (s) => !!innloggetAnsatt && s.sak?.saksbehandlerIdent === innloggetAnsatt.id
        ),
    },
    {
      value: "pagaende",
      label: "Pågående",
      filter: () => soknader.filter((s) => !erAvsluttet(s)),
    },
    {
      value: "avsluttet",
      label: "Avsluttet",
      filter: () => soknader.filter(erAvsluttet),
    },
    { value: "alle", label: "Alle", filter: () => soknader },
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
                        <Table.ColumnHeader>Status</Table.ColumnHeader>
                        <Table.ColumnHeader>Arbeidsgiver</Table.ColumnHeader>
                        <Table.ColumnHeader>Deltaker</Table.ColumnHeader>
                        <Table.ColumnHeader sortable>Startdato</Table.ColumnHeader>
                        <Table.ColumnHeader sortable>Søknad mottatt</Table.ColumnHeader>
                        <Table.HeaderCell />
                      </Table.Row>
                    </Table.Header>
                    <Table.Body>
                      {filter().map((soknad) => (
                        <Table.Row
                          key={soknad.soknadId}
                          style={{ cursor: "pointer" }}
                          onClick={() => navigate(`${OVERSIKT_PATH}/${soknad.soknadId}`)}
                        >
                          <Table.DataCell>
                            {soknad.sak?.saksbehandlerIdent ? (
                              soknad.sak.saksbehandlerIdent
                            ) : (
                              <Button variant="secondary" size="xsmall">
                                Tildel meg
                              </Button>
                            )}
                          </Table.DataCell>
                          <Table.DataCell>{statusTekst(soknad)}</Table.DataCell>
                          <Table.DataCell>{soknad.virksomhet.virksomhetsnavn}</Table.DataCell>
                          <Table.DataCell>{soknad.ansattNavn}</Table.DataCell>
                          <Table.DataCell>{formatDate(soknad.startdato)}</Table.DataCell>
                          <Table.DataCell>{formatDate(soknad.innsendtTidspunkt)}</Table.DataCell>
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
                                <ActionMenu.Item>Åpne søknad</ActionMenu.Item>
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
