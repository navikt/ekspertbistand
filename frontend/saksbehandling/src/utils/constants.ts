import { envSwitch } from "./env";

export const OVERSIKT_PATH = "/oversikt";
export const SAK_PATH = "/oversikt/:sakId";
export const SAKSBEHANDLING_OVERSIKT_URL = "/api/saksbehandling/v1/oversikt";
export const SAKSBEHANDLING_SAK_URL = (sakId: string) => `/api/saksbehandling/v1/saker/${sakId}`;
export const SAKSBEHANDLING_KONTONUMMER_URL = (orgnr: string) =>
  `/api/saksbehandling/v1/virksomhet/${orgnr}/kontonummer`;
export const SESSION_URL = "/oauth2/session";
export const LOGOUT_URL = "/oauth2/logout";
export const GOSYS_URL = "https://gosys.intern.nav.no/gosys/";
export const MODIA_URL =
  envSwitch({
    prod: () => "https://modiapersonoversikt.intern.nav.no",
    dev: () => "https://modiapersonoversikt.intern.dev.nav.no",
    local: () => "https://modiapersonoversikt.intern.dev.nav.no",
    other: () => "https://modiapersonoversikt.intern.dev.nav.no",
  }) ?? "https://modiapersonoversikt.intern.dev.nav.no";

const isLocalHost = () => {
  if (typeof window === "undefined") return false;
  return ["localhost", "127.0.0.1", "0.0.0.0", "::1"].includes(window.location.hostname);
};

export const isMockEnabled = () => {
  if (isLocalHost()) return true;
  if (import.meta.env.DEV) return true;
  const flag = import.meta.env.VITE_ENABLE_MOCKS?.toLowerCase();
  return flag === "true";
};

export const LOGIN_URL = `/oauth2/login?redirect=${OVERSIKT_PATH}`;
