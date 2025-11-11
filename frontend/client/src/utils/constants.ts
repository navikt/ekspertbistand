import { envSwitch } from "./env";

export const TELEMETRY_COLLECTOR_URL = envSwitch({
  prod: () => "https://telemetry.nav.no/collect",
  dev: () => "https://telemetry.ekstern.dev.nav.no/collect",
  local: () => undefined,
  other: () => undefined,
});

export const MIN_SIDE_URL = envSwitch({
  prod: () => "https://arbeidsgiver.nav.no/min-side-arbeidsgiver/",
  dev: () => "https://arbeidsgiver.intern.dev.nav.no/min-side-arbeidsgiver/",
  local: () => "https://arbeidsgiver.intern.dev.nav.no/min-side-arbeidsgiver/",
});

export const EKSPERTBISTAND_URL = envSwitch({
  prod: () => "https://arbeidsgiver.nav.no/ekspertbistand",
  dev: () => "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand",
  local: () => "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand",
});

const apiBaseUrl = (import.meta.env.BASE_URL ?? "/").replace(/\/$/, "");
const withBasePath = (path: string) => {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return `${apiBaseUrl}${normalized}`;
};

export const EKSPERTBISTAND_API_PATH = `${apiBaseUrl}/ekspertbistand-backend/api/skjema/v1`;
export const EKSPERTBISTAND_ORGANISASJONER_PATH = `${apiBaseUrl}/ekspertbistand-backend/api/organisasjoner/v1`;

export const APPLICATIONS_PATH = "/soknader";

export const LOGIN_URL = envSwitch({
  prod: () => `${withBasePath("/oauth2/login")}?redirect=${withBasePath(APPLICATIONS_PATH)}`,
  dev: () => `${withBasePath("/oauth2/login")}?redirect=${withBasePath(APPLICATIONS_PATH)}`,
  local: () => withBasePath(APPLICATIONS_PATH),
});

export const SESSION_URL = withBasePath("/oauth2/session");

export const TILGANGSSTYRING_URL =
  "https://www.nav.no/arbeidsgiver/min-side-arbeidsgiver/tilgangsstyring";
