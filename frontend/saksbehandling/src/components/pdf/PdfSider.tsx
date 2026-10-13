import { BodyShort, Link, Skeleton, VStack } from "@navikt/ds-react";
import { useState } from "react";
import "react-pdf/dist/Page/TextLayer.css";
import styles from "./Pdf.module.css";
import { Document, Page } from "./pdfjs";
import { A4_FORHOLD, type PdfFil } from "./types";

type Props = {
  fil: PdfFil;
  bredde: number;
};

export default function PdfSider({ fil, bredde }: Props) {
  const [antallSider, setAntallSider] = useState(0);
  const [feil, setFeil] = useState(false);
  const laster = <Skeleton variant="rectangle" width={bredde} height={bredde * A4_FORHOLD} />;

  if (feil) {
    return (
      <VStack gap="space-8">
        <BodyShort textColor="subtle">Kunne ikke vise forhåndsvisning.</BodyShort>
        <Link href={fil.url} download={fil.filnavn}>
          Last ned {fil.filnavn}
        </Link>
      </VStack>
    );
  }

  return (
    <Document
      file={fil.url}
      suspense={false}
      loading={laster}
      error={null}
      onLoadSuccess={({ numPages }) => setAntallSider(numPages)}
      onLoadError={() => setFeil(true)}
      onSourceError={() => setFeil(true)}
      className={styles.sider}
    >
      {Array.from({ length: antallSider }, (_, i) => (
        <Page
          key={i + 1}
          pageNumber={i + 1}
          width={bredde}
          loading={laster}
          renderAnnotationLayer={false}
          className={styles.side}
        />
      ))}
    </Document>
  );
}
