import { Alert, BodyLong, BodyShort, Heading, Loader, Table, Tag, VStack } from "@navikt/ds-react";
import { useOversikt } from "../hooks/useOversikt";
import { useTilgangContext } from "../tilgang/useTilgang";

function formatDate(date: string) {
  return new Intl.DateTimeFormat("nb-NO", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(date));
}

function statusVariant(status: "Til behandling" | "Avventer svar" | "Ferdigstilt") {
  if (status === "Til behandling") return "info";
  if (status === "Avventer svar") return "warning";
  return "success";
}

export default function OversiktPage() {
  const { saker, error, isLoading } = useOversikt();
  const { innloggetAnsatt } = useTilgangContext();

  if (isLoading) {
    return (
      <VStack gap="space-4">
        <Heading level="1" size="large">
          Saksoversikt
        </Heading>
        <Loader size="large" title="Laster oversikt" />
      </VStack>
    );
  }

  if (error) {
    return (
      <VStack gap="space-4">
        <Heading level="1" size="large">
          Saksoversikt
        </Heading>
        <Alert variant="error">Kunne ikke hente saksoversikten.</Alert>
      </VStack>
    );
  }

  if (saker.length === 0) {
    return (
      <VStack gap="space-4">
        <Heading level="1" size="large">
          Saksoversikt
        </Heading>
        <BodyLong>Fant ingen saker for valgt enhet.</BodyLong>
      </VStack>
    );
  }

  return (
    <VStack gap="space-4">
      <Heading level="1" size="large">
        Saksoversikt
      </Heading>
      <BodyShort size="small">
        Innlogget: {innloggetAnsatt?.navn} ({innloggetAnsatt?.id}) — Enhet:{" "}
        {innloggetAnsatt?.gjeldendeEnhet.nummer} {innloggetAnsatt?.gjeldendeEnhet.navn}
      </BodyShort>
      <Table>
        <Table.Header>
          <Table.Row>
            <Table.HeaderCell scope="col">Sak</Table.HeaderCell>
            <Table.HeaderCell scope="col">Virksomhet</Table.HeaderCell>
            <Table.HeaderCell scope="col">Deltaker</Table.HeaderCell>
            <Table.HeaderCell scope="col">Status</Table.HeaderCell>
            <Table.HeaderCell scope="col">Saksbehandler</Table.HeaderCell>
            <Table.HeaderCell scope="col">Opprettet</Table.HeaderCell>
            <Table.HeaderCell scope="col">Tilsagn</Table.HeaderCell>
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {saker.map((sak) => (
            <Table.Row key={sak.id}>
              <Table.DataCell>{sak.id}</Table.DataCell>
              <Table.DataCell>{sak.virksomhet}</Table.DataCell>
              <Table.DataCell>{sak.deltaker}</Table.DataCell>
              <Table.DataCell>
                <Tag variant={statusVariant(sak.status)} size="small">
                  {sak.status}
                </Tag>
              </Table.DataCell>
              <Table.DataCell>{sak.saksbehandler}</Table.DataCell>
              <Table.DataCell>{formatDate(sak.opprettetDato)}</Table.DataCell>
              <Table.DataCell>{sak.tilsagnNummer ?? "-"}</Table.DataCell>
            </Table.Row>
          ))}
        </Table.Body>
      </Table>
    </VStack>
  );
}
