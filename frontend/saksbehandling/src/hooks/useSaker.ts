import useSWR from "swr";
import { SAKSBEHANDLING_SAKER_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type SoknadStatus = "innsendt" | "godkjent" | "avlyst";

export type Saksstatus =
  "OPPRETTET" | "UNDER_BEHANDLING" | "TIL_BESLUTNING" | "INNVILGET" | "AVSLATT" | "AVSLUTTET";

export type KildeTilBehandling = "EKSPERTBISTAND" | "ARENA";

/** Null på saken betyr at den ikke har noen oppgave. */
export type Oppgavetype = "SOKNAD" | "SOKNAD_BESLUTTER";

export type Tildelingsrolle = "saksbehandler" | "beslutter";

/** Speiler `Oppgavetype.rolle` i backend. */
export const rolleForOppgave = (oppgavetype: Oppgavetype | null): Tildelingsrolle | null => {
  switch (oppgavetype) {
    case "SOKNAD":
      return "saksbehandler";
    case "SOKNAD_BESLUTTER":
      return "beslutter";
    default:
      return null;
  }
};

export type SakInfo = {
  sakId: string;
  status: Saksstatus;
  kildeTilBehandling: KildeTilBehandling;
  behandlendeEnhet: string | null;
  saksbehandlerIdent: string | null;
  saksbehandlerNavn: string | null;
  kanTildeleMeg: boolean;
  beslutterIdent: string | null;
  beslutterNavn: string | null;
  kanTildeleMegSomBeslutter: boolean;
  oppgavetype: Oppgavetype | null;
  arenaSakId: string | null;
};

export type SakListeElement = SakInfo & {
  harOppgaveForMeg: boolean;
  /** Fra siste foreløpige vedtak. Null uten vedtak og ved avslag. */
  tiltaksperiodeFra: string | null;
  tiltaksperiodeTil: string | null;
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
