import { FileTextIcon } from "@navikt/aksel-icons";
import { BodyShort, ExpansionCard, HStack } from "@navikt/ds-react";
import type { SluttrapportStatus } from "../hooks/useSluttrapportStatus";
import { formatSubmittedDate } from "./summaryFormatters";
import styles from "./SluttrapportSendtInnKort.module.css";

export function SluttrapportSendtInnKort({ sluttrapport }: { sluttrapport: SluttrapportStatus }) {
  const innsendtDato = formatSubmittedDate(sluttrapport.opprettet);

  return (
    <ExpansionCard size="small" aria-label="Sluttrapport sendt inn">
      <ExpansionCard.Header>
        <HStack wrap={false} gap="space-16" align="center">
          <FileTextIcon aria-hidden fontSize="2rem" />
          <div>
            <ExpansionCard.Title as="h2" size="small">
              Sluttrapport sendt inn
            </ExpansionCard.Title>
            <ExpansionCard.Description>{innsendtDato}</ExpansionCard.Description>
          </div>
        </HStack>
      </ExpansionCard.Header>
      <ExpansionCard.Content>
        <dl className={styles.opplysninger}>
          <div className={styles.opplysning}>
            <BodyShort as="dt" weight="semibold">
              Vedlegg
            </BodyShort>
            <BodyShort as="dd" className={styles.verdi}>
              {sluttrapport.filnavn}
            </BodyShort>
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
