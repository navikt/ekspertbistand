import { Skeleton } from "@navikt/ds-react";
import { Document, Page } from "./pdfjs";
import { A4_FORHOLD, type PdfFil } from "./types";

type Props = {
  fil: PdfFil;
  bredde: number;
  onFeil: () => void;
};

export default function PdfMiniatyr({ fil, bredde, onFeil }: Props) {
  const laster = <Skeleton variant="rectangle" width={bredde} height={bredde * A4_FORHOLD} />;

  return (
    <Document
      file={fil.innhold ?? fil.url}
      suspense={false}
      loading={laster}
      error={null}
      onLoadError={onFeil}
      onSourceError={onFeil}
    >
      <Page
        pageNumber={1}
        width={bredde}
        loading={laster}
        error={null}
        renderTextLayer={false}
        renderAnnotationLayer={false}
        onLoadError={onFeil}
        onRenderError={onFeil}
      />
    </Document>
  );
}
