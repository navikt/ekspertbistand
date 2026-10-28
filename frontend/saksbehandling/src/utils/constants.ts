import { envSwitch } from "./env";

export const OVERSIKT_PATH = "/oversikt";
export const SAK_PATH = "/oversikt/:sakId";
export const SAKSBEHANDLING_SAKER_URL = "/api/saksbehandling/v1/saker";
export const SAKSBEHANDLING_KOMMUNEFILTER_URL = "/api/saksbehandling/v1/meg/kommunefilter";
export const SAKSBEHANDLING_SAK_URL = (sakId: string) => `/api/saksbehandling/v1/saker/${sakId}`;
export const SAKSBEHANDLING_VILKARSVURDERING_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/vilkarsvurdering`;
export const SAKSBEHANDLING_TILDELING_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/tildeling`;
export const SAKSBEHANDLING_BESLUTTER_TILDELING_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/tildeling/beslutter`;
export const SAKSBEHANDLING_FORELOPIG_VEDTAK_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/forelopig-vedtak`;
export const SAKSBEHANDLING_FORELOPIG_VEDTAK_FORHANDSVISNING_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/forelopig-vedtak/forhandsvisning`;
export const SAKSBEHANDLING_FORELOPIG_VEDTAK_BREV_URL = (sakId: string, mottaker: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/forelopig-vedtak/brev/${mottaker}`;
export const SAKSBEHANDLING_VEDTAK_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/vedtak`;
export const SAKSBEHANDLING_RETUR_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/retur`;
export const SAKSBEHANDLING_REFUSJON_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/refusjon`;
export const SAKSBEHANDLING_REFUSJON_VEDLEGG_URL = (sakId: string, vedleggId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/refusjon/vedlegg/${vedleggId}`;
export const SAKSBEHANDLING_SLUTTRAPPORT_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/sluttrapport`;
export const SAKSBEHANDLING_SLUTTRAPPORT_FIL_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/sluttrapport/fil`;
export const SAKSBEHANDLING_FORELOPIG_VEDTAK_OPPSETT_URL =
  "/api/saksbehandling/v1/forelopig-vedtak/oppsett";
export const SAKSBEHANDLING_SAKSLOGG_URL = (sakId: string) =>
  `/api/saksbehandling/v1/saker/${sakId}/logg`;
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
  const flag = import.meta.env.VITE_ENABLE_MOCKS?.toLowerCase();
  if (flag === "false") return false;
  if (isLocalHost()) return true;
  if (import.meta.env.DEV) return true;
  return flag === "true";
};

/**
 * Brukervelgeren vises lokalt mot LocalApplication, og i ekspertbistand-saksbehandling-mock,
 * som bygges med VITE_LOKAL_BRUKERBYTTE=true og går mot ekspertbistand-backend-mock.
 */
export const isLokalBrukerbytteMulig = () =>
  !isMockEnabled() &&
  (isLocalHost() || import.meta.env.VITE_LOKAL_BRUKERBYTTE?.toLowerCase() === "true");
export const LOKALE_BRUKERE_URL = "/lokal/brukere";
export const LOKAL_BRUKER_COOKIE = "lokal-bruker";

export const LOGIN_URL = `/oauth2/login?redirect=${OVERSIKT_PATH}`;
