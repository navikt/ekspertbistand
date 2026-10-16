import { Alert, HStack, Skeleton } from "@navikt/ds-react";
import type { ForelopigVedtakInput } from "../../hooks/useForelopigVedtak";
import {
  type Mottaker,
  useLagredeVedtaksbrev,
  useVedtaksbrevForhandsvisning,
} from "../../hooks/useVedtaksbrev";
import { PdfForhandsvisning, type PdfFil } from "../pdf";
import { A4_FORHOLD } from "../pdf/types";
import { HttpError } from "../../utils/http";

const BREDDE = 360;
const MOTTAKERE: Mottaker[] = ["deltaker", "arbeidsgiver"];

type Props =
  | { modus: "sendt"; sakId: string; versjon: number }
  | { modus: "forhandsvisning"; sakId: string; vedtak: ForelopigVedtakInput };

export default function Vedtaksbrev(props: Props) {
  return props.modus === "sendt" ? (
    <LagredeBrev sakId={props.sakId} versjon={props.versjon} />
  ) : (
    <ForhandsvisteBrev sakId={props.sakId} vedtak={props.vedtak} />
  );
}

function LagredeBrev({ sakId, versjon }: { sakId: string; versjon: number }) {
  return <Brev {...useLagredeVedtaksbrev(sakId, versjon)} />;
}

function ForhandsvisteBrev({ sakId, vedtak }: { sakId: string; vedtak: ForelopigVedtakInput }) {
  return <Brev {...useVedtaksbrevForhandsvisning(sakId, vedtak)} />;
}

type BrevProps = {
  deltaker?: PdfFil;
  arbeidsgiver?: PdfFil;
  isLoading: boolean;
  error?: Error;
};

function Brev({ deltaker, arbeidsgiver, isLoading, error }: BrevProps) {
  if (error) {
    return (
      <Alert variant="warning" size="small">
        {error instanceof HttpError && error.status === 400
          ? `Kunne ikke lage forhåndsvisning av brevene: ${error.message}`
          : "Kunne ikke lage forhåndsvisning av brevene."}
      </Alert>
    );
  }

  const brev = [deltaker, arbeidsgiver];

  return (
    <HStack as="ul" gap="space-24" wrap style={{ listStyle: "none", margin: 0, padding: 0 }}>
      {brev.map((fil, i) => (
        <li key={MOTTAKERE[i]}>
          {isLoading || !fil ? (
            <Skeleton variant="rectangle" width={BREDDE} height={BREDDE * A4_FORHOLD} />
          ) : (
            <PdfForhandsvisning bredde={BREDDE} fil={fil} />
          )}
        </li>
      ))}
    </HStack>
  );
}
