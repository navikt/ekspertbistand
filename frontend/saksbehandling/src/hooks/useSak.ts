import useSWR from "swr";
import { SAKSBEHANDLING_SAK_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type Vilkårstatus = "oppfylt" | "manuell";

export type Vilkår = {
  id: string;
  tittel: string;
  beskrivelse: string;
  status: Vilkårstatus;
  automatisk?: boolean;
};

export type SakDetaljer = {
  id: string;
  deltaker: {
    navn: string;
    alder: number;
    fnr: string;
  };
  arbeidsgiver: {
    navn: string;
    orgNr: string;
    beliggenhetssadresse: string;
    kontaktperson: string;
    epost: string;
    telefon: string;
  };
  ekspert: {
    navn: string;
    tilknyttetVirksomhet: string;
    kompetanse: string;
    orgNr: string;
  };
  situasjon: {
    arbeidssituasjon: string;
    sykefravær: string;
  };
  ekspertbistand: {
    hvaHjelpeMed: string;
    antallTimer: number;
    søknadssum: number;
    startdato: string;
    sendtInnTilNav: string;
  };
  vilkår: Vilkår[];
};

export function useSak(sakId: string) {
  const { data, error, isLoading } = useSWR<SakDetaljer, HttpError>(SAKSBEHANDLING_SAK_URL(sakId), {
    revalidateOnFocus: false,
  });

  return { sak: data, error, isLoading };
}
