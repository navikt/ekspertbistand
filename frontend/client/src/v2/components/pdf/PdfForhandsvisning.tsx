import { FilePdfIcon } from "@navikt/aksel-icons";
import { BodyShort, HStack, Link, Skeleton, VStack } from "@navikt/ds-react";
import { Suspense, lazy, useCallback, useState } from "react";
import { formatBytes } from "../../utils/formatBytes";
import styles from "./Pdf.module.css";
import { PdfModal } from "./PdfModal";
import { A4_FORHOLD, type PdfFil } from "./types";

const PdfMiniatyr = lazy(() => import("./PdfMiniatyr"));

type Props = {
  fil: PdfFil;
  /** Bredde på miniatyren i px */
  bredde?: number;
};

export function PdfForhandsvisning({ fil, bredde = 240 }: Props) {
  const [synlig, setSynlig] = useState(false);
  const [feil, setFeil] = useState(false);
  const [åpen, setÅpen] = useState(false);
  const høyde = bredde * A4_FORHOLD;
  const laster = <Skeleton variant="rectangle" width={bredde} height={høyde} />;

  const observerSynlighet = useCallback((element: HTMLElement | null) => {
    if (!element) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setSynlig(true);
          observer.disconnect();
        }
      },
      { rootMargin: "200px" }
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const håndterFeil = useCallback(() => setFeil(true), []);

  return (
    <VStack gap="space-8" style={{ width: bredde }}>
      {feil ? (
        <BodyShort textColor="subtle">Kunne ikke vise forhåndsvisning.</BodyShort>
      ) : (
        <button
          ref={observerSynlighet}
          type="button"
          aria-label={`Forhåndsvis ${fil.filnavn}`}
          aria-haspopup="dialog"
          className={styles.miniatyrKnapp}
          style={{ width: bredde, minHeight: høyde }}
          onClick={() => setÅpen(true)}
        >
          {synlig ? (
            <Suspense fallback={laster}>
              <PdfMiniatyr fil={fil} bredde={bredde} onFeil={håndterFeil} />
            </Suspense>
          ) : (
            laster
          )}
        </button>
      )}
      <VStack gap="space-2">
        <HStack gap="space-4" wrap={false} align="start">
          <FilePdfIcon aria-hidden fontSize="1.5rem" style={{ flexShrink: 0 }} />
          <Link href={fil.url} download={fil.filnavn} className={styles.filnavn}>
            {fil.filnavn}
          </Link>
        </HStack>
        {fil.storrelse !== undefined && (
          <BodyShort size="small" textColor="subtle">
            {formatBytes(fil.storrelse)}
          </BodyShort>
        )}
      </VStack>
      {!feil && <PdfModal fil={fil} open={åpen} onClose={() => setÅpen(false)} />}
    </VStack>
  );
}
