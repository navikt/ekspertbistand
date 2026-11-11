import { DownloadIcon, FilePdfIcon } from "@navikt/aksel-icons";
import { Button, Modal, Skeleton } from "@navikt/ds-react";
import { Suspense, lazy, useCallback, useState } from "react";
import { A4_FORHOLD, type PdfFil } from "./types";

const PdfSider = lazy(() => import("./PdfSider"));

type Props = {
  fil: PdfFil;
  open: boolean;
  onClose: () => void;
};

export function PdfModal({ fil, open, onClose }: Props) {
  const [bredde, setBredde] = useState(0);

  const målBredde = useCallback((element: HTMLDivElement | null) => {
    if (!element) return;
    const observer = new ResizeObserver(([entry]) =>
      setBredde(Math.floor(entry.contentRect.width))
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return (
    <Modal
      open={open}
      onClose={onClose}
      header={{ heading: fil.filnavn, icon: <FilePdfIcon aria-hidden /> }}
      width={900}
      closeOnBackdropClick
    >
      <Modal.Body>
        <div ref={målBredde}>
          {open && bredde > 0 && (
            <Suspense
              fallback={
                <Skeleton variant="rectangle" width={bredde} height={bredde * A4_FORHOLD} />
              }
            >
              <PdfSider fil={fil} bredde={bredde} />
            </Suspense>
          )}
        </div>
      </Modal.Body>
      <Modal.Footer>
        <Button as="a" href={fil.url} download={fil.filnavn} icon={<DownloadIcon aria-hidden />}>
          Last ned
        </Button>
        <Button type="button" variant="secondary" onClick={onClose}>
          Lukk
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
