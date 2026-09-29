import useSWR from "swr";
import { SAKSBEHANDLING_SOKNAD_URL } from "../utils/constants";
import { HttpError } from "../utils/http";
import type { SakInfo, SoknadStatus } from "./useSoknader";

export type SoknadDetaljer = {
  soknadId: string;
  soknadStatus: SoknadStatus;
  innsendtTidspunkt: string;
  virksomhet: {
    virksomhetsnummer: string;
    virksomhetsnavn: string;
    kontaktperson: {
      navn: string;
      epost: string;
      telefonnummer: string;
    };
    beliggenhetsadresse?: string | null;
  };
  ansatt: {
    fnr: string;
    navn: string;
  };
  ekspert: {
    navn: string;
    virksomhet: string;
    virksomhetNavn?: string | null;
    virksomhetOrgnr?: string | null;
    kompetanse: string;
    godkjentUtdanningEllerAutorisasjon: string[];
    relevantKompetanse: string[];
  };
  behovForBistand: {
    begrunnelse: string;
    behov: string;
    estimertKostnad: string;
    timer: string;
    tilrettelegging: string;
    startdato: string;
  };
  nav: {
    kontaktperson: string;
  };
  sak: SakInfo | null;
};

export function useSoknad(soknadId: string) {
  const { data, error, isLoading } = useSWR<SoknadDetaljer, HttpError>(
    soknadId ? SAKSBEHANDLING_SOKNAD_URL(soknadId) : null,
    {
      revalidateOnFocus: false,
    }
  );

  return { soknad: data, error, isLoading };
}
