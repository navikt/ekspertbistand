import { CheckmarkIcon, FileTextIcon, FileXMarkIcon } from "@navikt/aksel-icons";
import { BodyLong, BodyShort, ExpansionCard, HStack, Tag } from "@navikt/ds-react";
import type { SluttrapportStatus } from "../hooks/useSluttrapportStatus";
import { formatSubmittedDate } from "./summaryFormatters";
import styles from "./SluttrapportSendtInnKort.module.css";

export function SluttrapportSendtInnKort({ sluttrapport }: { sluttrapport: SluttrapportStatus }) {
  const innsendtDato = formatSubmittedDate(sluttrapport.opprettet);
  const avvist = sluttrapport.status === "NY_INNSENDING_KREVES";
  const tittel = avvist ? "Sluttrapport avvist" : "Sluttrapport sendt inn";
  const dato = avvist ? formatSubmittedDate(sluttrapport.vurdertTidspunkt) : innsendtDato;
  const Ikon = avvist ? FileXMarkIcon : FileTextIcon;

  return (
    <ExpansionCard size="small" aria-label={tittel} defaultOpen={avvist}>
      <ExpansionCard.Header>
        <HStack wrap={false} gap="space-16" align="center">
          <Ikon aria-hidden fontSize="2rem" />
          <div>
            <ExpansionCard.Title as="h2" size="small">
              {tittel}
            </ExpansionCard.Title>
            <ExpansionCard.Description>{dato}</ExpansionCard.Description>
          </div>
          {sluttrapport.status === "BEKREFTET" && (
            <Tag
              size="small"
              variant="moderate"
              data-color="success"
              icon={<CheckmarkIcon aria-hidden />}
            >
              Godkjent av Nav
            </Tag>
          )}
        </HStack>
      </ExpansionCard.Header>
      <ExpansionCard.Content>
        <dl className={styles.opplysninger}>
          {avvist && (
            <div className={styles.opplysning}>
              <BodyShort as="dt" weight="semibold">
                Sluttrapport avvist
              </BodyShort>
              <BodyLong as="dd" className={styles.verdi}>
                {sluttrapport.begrunnelse}
              </BodyLong>
            </div>
          )}
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
