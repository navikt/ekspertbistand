import { Alert, Button, HStack, Heading, TextField, Textarea, VStack } from "@navikt/ds-react";
import type { ForelopigVedtakInput } from "../../hooks/useForelopigVedtak";
import type { SakDetaljer } from "../../hooks/useSak";
import { tolkSoknadssum } from "../../utils/belop";
import Vedtaksbrev from "./Vedtaksbrev";
import VurderingKort from "./VurderingKort";
import {
  begrunnelseLabel,
  formatKostnadssted,
  formatPeriode,
  vurderingRader,
} from "./forelopigVedtak";

type Props = {
  sak: SakDetaljer;
  vedtak: ForelopigVedtakInput;
  kostnadsstedNavn: string | null;
} & (
  | { modus: "sendt"; tittel?: string; fattet: boolean }
  | {
      modus: "forhandsvisning";
      onForrige: () => void;
      onSend: () => void;
      isSaving: boolean;
      error: Error | null;
    }
);

export default function ForelopigVedtakOppsummering(props: Props) {
  const { sak, vedtak, kostnadsstedNavn } = props;
  const tilskudd =
    vedtak.innvilgetBelop ?? tolkSoknadssum(sak.soknad.behovForBistand.estimertKostnad);

  return (
    <VStack gap="space-24">
      {props.modus === "sendt" && (
        <Heading level="2" size="small">
          {props.tittel ?? "Foreløpig vedtak sendt til beslutter"}
        </Heading>
      )}

      <VurderingKort
        utfall={vedtak.utfall}
        tittelNivå={props.modus === "sendt" ? "h3" : "h2"}
        rader={vurderingRader(
          vedtak.utfall,
          sak,
          tilskudd,
          formatPeriode(vedtak.tiltaksperiodeFra, vedtak.tiltaksperiodeTil)
        )}
      />

      <Textarea
        label={begrunnelseLabel(vedtak.utfall, "deltaker")}
        description="Teksten blir synlig i vedtaksbrevet til deltaker."
        value={vedtak.begrunnelseDeltaker}
        readOnly
        minRows={2}
      />
      {vedtak.kostnadssted !== null && (
        <TextField
          label="Kostnadssted"
          value={formatKostnadssted(vedtak.kostnadssted, kostnadsstedNavn)}
          readOnly
        />
      )}
      {vedtak.begrunnelseArbeidsgiver !== null && (
        <Textarea
          label={begrunnelseLabel(vedtak.utfall, "arbeidsgiver")}
          description="Teksten blir synlig i vedtaksbrevet til arbeidsgiver."
          value={vedtak.begrunnelseArbeidsgiver}
          readOnly
          minRows={2}
        />
      )}

      {props.modus === "sendt" ? (
        <Vedtaksbrev
          modus="sendt"
          sakId={sak.sakId}
          versjon={sak.forelopigVedtak?.versjon ?? 0}
          fattet={props.fattet}
        />
      ) : (
        <Vedtaksbrev modus="forhandsvisning" sakId={sak.sakId} vedtak={vedtak} />
      )}

      {props.modus === "forhandsvisning" && (
        <>
          {props.error && (
            <Alert variant="error" size="small">
              {props.error.message}
            </Alert>
          )}
          <HStack gap="space-16">
            <Button
              type="button"
              variant="secondary"
              onClick={props.onForrige}
              disabled={props.isSaving}
            >
              Forrige
            </Button>
            <Button type="button" onClick={props.onSend} loading={props.isSaving}>
              Send til beslutter
            </Button>
          </HStack>
        </>
      )}
    </VStack>
  );
}
