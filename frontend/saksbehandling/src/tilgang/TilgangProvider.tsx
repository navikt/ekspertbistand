import type { ReactNode } from "react";
import { useMemo } from "react";
import useSWR from "swr";
import type { InnloggetAnsatt } from "../mock/ansatt";
import { mockInnloggetAnsatt } from "../mock/ansatt";
import { HttpError } from "../utils/http";
import { isMockEnabled } from "../utils/constants";
import { TilgangContext } from "./TilgangContext";

const ansattUrl = "/api/saksbehandling/v1/meg";

export function TilgangProvider({ children }: { children: ReactNode }) {
  const { data, error, isLoading } = useSWR<InnloggetAnsatt, HttpError>(ansattUrl, {
    revalidateOnFocus: false,
  });

  const value = useMemo(
    () => ({
      innloggetAnsatt: data ?? (isMockEnabled() ? mockInnloggetAnsatt : undefined),
      isLoading,
      isUnauthorized: error?.status === 403,
    }),
    [data, error?.status, isLoading]
  );

  return <TilgangContext.Provider value={value}>{children}</TilgangContext.Provider>;
}
