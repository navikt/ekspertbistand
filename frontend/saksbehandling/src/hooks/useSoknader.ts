import useSWR from "swr";
import { SAKSBEHANDLING_SOKNADER_URL } from "../utils/constants";
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

export type SoknadListeElement = {
  soknadId: string;
  soknadStatus: SoknadStatus;
  innsendtTidspunkt: string;
  virksomhet: {
    virksomhetsnummer: string;
    virksomhetsnavn: string;
  };
  ansattNavn: string;
  startdato: string;
  sak: SakInfo | null;
};

export type SoknaderResponse = {
  soknader: SoknadListeElement[];
};

export function useSoknader() {
  const { data, error, isLoading } = useSWR<SoknaderResponse, HttpError>(
    SAKSBEHANDLING_SOKNADER_URL,
    {
      revalidateOnFocus: false,
    }
  );

  return {
    soknader: data?.soknader ?? [],
    error,
    isLoading,
  };
}
