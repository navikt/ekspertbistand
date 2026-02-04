import { envSwitch } from "./env";

export const APP_BASE_PATH = import.meta.env.VITE_APP_BASE_PATH || "/";

const withBasePath = (path: `/${string}`) =>
  APP_BASE_PATH === "/" ? path : `${APP_BASE_PATH}${path}`;

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

export const EKSPERTBISTAND_API_PATH = withBasePath("/ekspertbistand-backend/api/soknad/v1");
export const EKSPERTBISTAND_TILSKUDDSBREV_HTML_PATH = withBasePath(
  "/ekspertbistand-backend/api/tilsagndata/v1"
);
export const EKSPERTBISTAND_ORGANISASJONER_PATH = withBasePath(
  "/ekspertbistand-backend/api/organisasjoner/v1"
);
export const EKSPERTBISTAND_EREG_ADRESSE_PATH = withBasePath("/ekspertbistand-backend/api/ereg");

export const SOKNADER_PATH = "/soknader";

export const LOGIN_URL = envSwitch({
  prod: () => `${withBasePath("/oauth2/login")}?redirect=${withBasePath(SOKNADER_PATH)}`,
  dev: () => `${withBasePath("/oauth2/login")}?redirect=${withBasePath(SOKNADER_PATH)}`,
  local: () => withBasePath(SOKNADER_PATH),
});

export const SESSION_URL = withBasePath("/oauth2/session");

export const TILGANGSSTYRING_URL =
  "https://www.nav.no/arbeidsgiver/min-side-arbeidsgiver/tilgangsstyring";
