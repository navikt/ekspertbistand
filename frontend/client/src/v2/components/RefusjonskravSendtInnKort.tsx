import { FilePdfIcon, FileTextIcon, FileXMarkIcon } from "@navikt/aksel-icons";
import { BodyShort, ExpansionCard, HStack, Link } from "@navikt/ds-react";
import type { RefusjonStatus } from "../hooks/useRefusjonStatus";
import { EKSPERTBISTAND_REFUSJON_VEDLEGG_PATH } from "../utils/constants";
import { formatBytes } from "../utils/format";
import { formatCurrency, formatSubmittedDate } from "./summaryFormatters";
import styles from "./RefusjonskravSendtInnKort.module.css";

export function RefusjonskravSendtInnKort({
  soknadId,
  refusjon,
}: {
  soknadId: string;
  refusjon: RefusjonStatus;
}) {
  const innsendtDato = formatSubmittedDate(refusjon.opprettet);
  const avvist = refusjon.status === "NY_INNSENDING_KREVES";
  const tittel = avvist ? "Refusjonskrav avvist" : "Refusjonskrav sendt inn";
  const Ikon = avvist ? FileXMarkIcon : FileTextIcon;
  const vurdertDato = refusjon.vurdertTidspunkt
    ? formatSubmittedDate(refusjon.vurdertTidspunkt)
    : innsendtDato;

  return (
    <ExpansionCard
      key={`${refusjon.opprettet}:${refusjon.status}`}
      size="small"
      aria-label={tittel}
      defaultOpen={avvist}
    >
      <ExpansionCard.Header>
        <HStack wrap={false} gap="space-16" align="center">
          <Ikon aria-hidden fontSize="2rem" />
          <div>
            <ExpansionCard.Title as="h2" size="small">
              {tittel}
            </ExpansionCard.Title>
            <ExpansionCard.Description>
              {avvist ? vurdertDato : innsendtDato}
            </ExpansionCard.Description>
          </div>
        </HStack>
      </ExpansionCard.Header>
      <ExpansionCard.Content>
        <dl className={styles.opplysninger}>
          {avvist && refusjon.begrunnelse && (
            <div className={styles.opplysning}>
              <BodyShort as="dt" weight="semibold">
                Beskjed fra Nav
              </BodyShort>
              <BodyShort as="dd" className={styles.verdi} style={{ whiteSpace: "pre-line" }}>
                {refusjon.begrunnelse}
              </BodyShort>
            </div>
          )}
          <div className={styles.opplysning}>
            <BodyShort as="dt" weight="semibold">
              Beløp for refusjonskravet
            </BodyShort>
            <BodyShort as="dd" className={styles.verdi}>
              {formatCurrency(refusjon.belopKroner)}
            </BodyShort>
          </div>
          <div className={styles.opplysning}>
            <BodyShort as="dt" weight="semibold">
              Vedlegg
            </BodyShort>
            <dd className={styles.verdi}>
              {refusjon.vedlegg.length === 0 ? (
                <BodyShort>–</BodyShort>
              ) : (
                <ul className={styles.vedlegg}>
                  {refusjon.vedlegg.map((vedlegg) => (
                    <li key={vedlegg.id}>
                      <Link
                        href={EKSPERTBISTAND_REFUSJON_VEDLEGG_PATH(soknadId, vedlegg.id)}
                        download={vedlegg.filnavn}
                        inlineText
                      >
                        <FilePdfIcon aria-hidden /> {vedlegg.filnavn}
                      </Link>
                      <BodyShort size="small" textColor="subtle">
                        {formatBytes(vedlegg.storrelse)}
                      </BodyShort>
                    </li>
                  ))}
                </ul>
              )}
            </dd>
          </div>
          <div className={styles.opplysning}>
            <BodyShort as="dt" weight="semibold">
              Sendt inn til Nav
            </BodyShort>
            <BodyShort as="dd" className={styles.verdi}>
              {innsendtDato}
            </BodyShort>
          </div>
        </dl>
      </ExpansionCard.Content>
    </ExpansionCard>
  );
}
