import useSWR from "swr";
import { SAKSBEHANDLING_SAKER_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type SoknadStatus = "innsendt" | "godkjent" | "avslatt" | "avlyst";

export type Saksstatus =
  "OPPRETTET" | "UNDER_BEHANDLING" | "TIL_BESLUTNING" | "INNVILGET" | "AVSLATT" | "AVSLUTTET";

export type KildeTilBehandling = "EKSPERTBISTAND" | "ARENA";

/** Null på saken betyr at den ikke har noen oppgave. */
export type Oppgavetype = "SOKNAD" | "SOKNAD_RETUR" | "SOKNAD_BESLUTTER";

export type Tildelingsrolle = "saksbehandler" | "beslutter";

/** Speiler `Oppgavetype.rolle` i backend. */
export const rolleForOppgave = (oppgavetype: Oppgavetype | null): Tildelingsrolle | null => {
  switch (oppgavetype) {
    case "SOKNAD":
    case "SOKNAD_RETUR":
      return "saksbehandler";
    case "SOKNAD_BESLUTTER":
      return "beslutter";
    default:
      return null;
  }
};

export type Adressebeskyttelse = "FORTROLIG" | "STRENGT_FORTROLIG";

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
  /** Null uten adressebeskyttelse og uten tilgang til deltakeren. Lagret da saken ble opprettet. */
  adressebeskyttelse: Adressebeskyttelse | null;
  /** Egen ansatt i Nav da saken ble opprettet. False uten tilgang til deltakeren. */
  skjermet: boolean;
};

/** Arena-saker behandles ferdig i Arena. API-et godtar tildeling, men GUI-et tilbyr det ikke. */
export const kanTildeleMegSomSaksbehandler = (
  sak: Pick<SakInfo, "kildeTilBehandling" | "kanTildeleMeg">
) => sak.kildeTilBehandling !== "ARENA" && sak.kanTildeleMeg;

export type DeltakerTilgang = {
  avvist: boolean;
  /** `AVVIST_SKJERMING`, `AVVIST_FORTROLIG_ADRESSE`, `AVVIST_STRENGT_FORTROLIG_ADRESSE` eller `IKKE_TILGANG`. */
  kode: string;
};

export type SakListeElement = SakInfo & {
  harOppgaveForMeg: boolean;
  /** Null når Tilgangsmaskinen innvilger, eller når sjekken feilet. */
  deltakerTilgang: DeltakerTilgang | null;
  /** Virksomhetens kommune. Null for kode 6, Arena-saker og rader uten tilgang. */
  kommunenummer: string | null;
  /** Null når Norg ikke kjenner kommunen eller ikke svarer. */
  kommunenavn: string | null;
  /** Fra siste foreløpige vedtak. Null uten vedtak og ved avslag. */
  tiltaksperiodeFra: string | null;
  tiltaksperiodeTil: string | null;
  soknad: {
    soknadId: string;
    status: SoknadStatus;
    innsendtTidspunkt: string;
    /** Null når den innloggede ikke har tilgang til deltakeren. */
    virksomhet: {
      virksomhetsnummer: string;
      virksomhetsnavn: string;
    } | null;
    ansattNavn: string | null;
    startdato: string;
  };
};

export type SakerResponse = {
  saker: SakListeElement[];
  tilgangssjekkFeilet: boolean;
};

export function useSaker() {
  const { data, error, isLoading } = useSWR<SakerResponse, HttpError>(SAKSBEHANDLING_SAKER_URL, {
    revalidateOnFocus: false,
  });

  return {
    saker: data?.saker ?? [],
    tilgangssjekkFeilet: data?.tilgangssjekkFeilet ?? false,
    error,
    isLoading,
  };
}
