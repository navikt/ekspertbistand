import { FilePdfIcon, FileTextIcon } from "@navikt/aksel-icons";
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

  return (
    <ExpansionCard size="small" aria-label="Refusjonskrav sendt inn">
      <ExpansionCard.Header>
        <HStack wrap={false} gap="space-16" align="center">
          <FileTextIcon aria-hidden fontSize="2rem" />
          <div>
            <ExpansionCard.Title as="h2" size="small">
              Refusjonskrav sendt inn
            </ExpansionCard.Title>
            <ExpansionCard.Description>{innsendtDato}</ExpansionCard.Description>
          </div>
        </HStack>
      </ExpansionCard.Header>
      <ExpansionCard.Content>
        <dl className={styles.opplysninger}>
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
