import { ArrowLeftIcon, CheckmarkCircleFillIcon } from "@navikt/aksel-icons";
import { BodyLong, BodyShort, Box, Heading, Link, Table, VStack } from "@navikt/ds-react";
import { NavLink } from "react-router";
import type { ForelopigVedtak, SakDetaljer } from "../../hooks/useSak";
import { formatKroner, tolkSoknadssum } from "../../utils/belop";
import { OVERSIKT_PATH } from "../../utils/constants";
import { type Rad, UTFALL_TEKST, formatPeriode } from "./forelopigVedtak";

type Props = {
  sak: SakDetaljer;
  vedtak: ForelopigVedtak;
};

function rader(sak: SakDetaljer, vedtak: ForelopigVedtak): Rad[] {
  const { estimertKostnad } = sak.soknad.behovForBistand;
  const soknadssum = tolkSoknadssum(estimertKostnad);
  const kroner = (belop: number | null) => (belop !== null ? formatKroner(belop) : estimertKostnad);
  const vurdering = { label: "Vurdering", verdi: UTFALL_TEKST[vedtak.utfall] };
  const parter = [
    { label: "Arbeidsgiver", verdi: sak.soknad.virksomhet.virksomhetsnavn },
    { label: "Deltakere", verdi: sak.soknad.ansatt.navn },
  ];
  const periode = formatPeriode(vedtak.tiltaksperiodeFra, vedtak.tiltaksperiodeTil);
  const tiltaksperiode = periode ? [{ label: "Tiltaksperiode", verdi: periode }] : [];
  const tilskudd = { label: "Tilskudd", verdi: kroner(vedtak.innvilgetBelop ?? soknadssum) };

  switch (vedtak.utfall) {
    case "INNVILGE":
      return [vurdering, tilskudd, ...parter, ...tiltaksperiode];
    case "INNVILGE_REDUSERT":
      return [
        vurdering,
        { label: "Søknadssum", verdi: kroner(soknadssum) },
        tilskudd,
        ...parter,
        ...tiltaksperiode,
      ];
    case "AVSLAA":
      return [vurdering, ...parter];
  }
}

export default function ForelopigVedtakKvittering({ sak, vedtak }: Props) {
  return (
    <Box paddingBlock="space-40" paddingInline="space-24">
      <VStack gap="space-32" align="center" marginInline="auto" maxWidth="32rem">
        <CheckmarkCircleFillIcon
          aria-hidden
          fontSize="4rem"
          style={{ color: "var(--ax-text-success-decoration)" }}
        />
        <VStack gap="space-16" align="center">
          <Heading level="1" size="large" align="center">
            Foreløpig vedtak sendt til beslutter
          </Heading>
          <BodyLong align="center">
            Saken er oversendt beslutter for godkjenning. Arbeidsgiver og deltaker vil bli varslet
            etter endelig godkjenning.
          </BodyLong>
        </VStack>
        <Box background="neutral-soft" paddingInline="space-16" borderRadius="4" width="100%">
          <Table size="small">
            <Table.Body>
              {rader(sak, vedtak).map(({ label, verdi }) => (
                <Table.Row key={label}>
                  <Table.HeaderCell scope="row">
                    <BodyShort as="span" size="small" textColor="subtle">
                      {label}
                    </BodyShort>
                  </Table.HeaderCell>
                  <Table.DataCell>{verdi}</Table.DataCell>
                </Table.Row>
              ))}
            </Table.Body>
          </Table>
        </Box>
        <Link as={NavLink} to={OVERSIKT_PATH} underline={false}>
          <ArrowLeftIcon aria-hidden />
          <BodyShort as="span" weight="semibold">
            Tilbake til liste av saker
          </BodyShort>
        </Link>
      </VStack>
    </Box>
  );
}
