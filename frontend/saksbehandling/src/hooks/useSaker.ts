import useSWR from "swr";
import { SAKSBEHANDLING_SAKER_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type SoknadStatus = "innsendt" | "godkjent" | "avlyst";

export type Saksstatus =
  "OPPRETTET" | "UNDER_BEHANDLING" | "TIL_BESLUTNING" | "INNVILGET" | "AVSLATT" | "AVSLUTTET";

export type KildeTilBehandling = "EKSPERTBISTAND" | "ARENA";

export type SakInfo = {
  sakId: string;
  status: Saksstatus;
  kildeTilBehandling: KildeTilBehandling;
  behandlendeEnhet: string | null;
  saksbehandlerIdent: string | null;
  beslutterIdent: string | null;
  arenaSakId: string | null;
};

export type SakListeElement = SakInfo & {
  soknad: {
    soknadId: string;
    status: SoknadStatus;
    innsendtTidspunkt: string;
    virksomhet: {
      virksomhetsnummer: string;
      virksomhetsnavn: string;
    };
    ansattNavn: string;
    startdato: string;
  };
};

export type SakerResponse = {
  saker: SakListeElement[];
};

export function useSaker() {
  const { data, error, isLoading } = useSWR<SakerResponse, HttpError>(SAKSBEHANDLING_SAKER_URL, {
    revalidateOnFocus: false,
  });

  return {
    saker: data?.saker ?? [],
    error,
    isLoading,
  };
}
