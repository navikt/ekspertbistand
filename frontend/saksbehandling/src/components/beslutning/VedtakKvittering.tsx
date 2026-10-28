import type { ForelopigVedtak, SakDetaljer } from "../../hooks/useSak";
import { Kvittering } from "../forelopigVedtak/ForelopigVedtakKvittering";

type Props = {
  sak: SakDetaljer;
  vedtak: ForelopigVedtak;
};

export default function VedtakKvittering({ sak, vedtak }: Props) {
  return (
    <Kvittering
      sak={sak}
      vedtak={vedtak}
      tittel="Vedtak fattet"
      tekst="Arbeidsgiver og deltaker vil bli varslet om vedtaket."
    />
  );
}
