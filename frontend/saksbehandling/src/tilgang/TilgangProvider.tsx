import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import useSWR from "swr";
import type { AnsattEnhet } from "../mock/ansatt";
import type { InnloggetAnsatt } from "../mock/ansatt";
import { mockInnloggetAnsatt } from "../mock/ansatt";
import { fetchJson } from "../utils/api";
import { HttpError } from "../utils/http";
import { TilgangContext } from "./TilgangContext";

const valgtEnhetKey = "saksbehandling.valgtEnhetsnummer";
const ansattUrl = "/api/ansatte/meg";

function finnGjeldendeEnhet(
  enheter: ReadonlyArray<AnsattEnhet>,
  fallback: AnsattEnhet,
  valgtEnhetsnummer: string | null
): AnsattEnhet {
  return enheter.find((enhet) => enhet.nummer === valgtEnhetsnummer) ?? fallback;
}

export function TilgangProvider({ children }: { children: ReactNode }) {
  const { data, error, isLoading } = useSWR<InnloggetAnsatt, HttpError>(ansattUrl, {
    revalidateOnFocus: false,
  });
  const innloggetAnsattFraApi = data ?? mockInnloggetAnsatt;
  const [gjeldendeEnhet, setGjeldendeEnhet] = useState<AnsattEnhet>(
    innloggetAnsattFraApi.gjeldendeEnhet
  );

  useEffect(() => {
    setGjeldendeEnhet(
      finnGjeldendeEnhet(
        innloggetAnsattFraApi.enheter,
        innloggetAnsattFraApi.gjeldendeEnhet,
        window.localStorage.getItem(valgtEnhetKey)
      )
    );
  }, [innloggetAnsattFraApi]);

  const value = useMemo(
    () => ({
      innloggetAnsatt: {
        ...innloggetAnsattFraApi,
        gjeldendeEnhet,
      },
      isLoading,
      isUnauthorized: error?.status === 403,
      setValgtEnhet: async (enhetsnummer: string) => {
        await fetchJson("/api/ansatte/enhet", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ valgtEnhetsnummer: enhetsnummer }),
        });
        const nesteEnhet = finnGjeldendeEnhet(
          innloggetAnsattFraApi.enheter,
          innloggetAnsattFraApi.gjeldendeEnhet,
          enhetsnummer
        );
        window.localStorage.setItem(valgtEnhetKey, nesteEnhet.nummer);
        setGjeldendeEnhet(nesteEnhet);
      },
    }),
    [error?.status, gjeldendeEnhet, innloggetAnsattFraApi, isLoading]
  );

  return <TilgangContext.Provider value={value}>{children}</TilgangContext.Provider>;
}
