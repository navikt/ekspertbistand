import { detectEnv, envSwitch } from "./env";

export const APP_BASE_PATH = import.meta.env.VITE_APP_BASE_PATH || "/";

const withBasePath = (path: `/${string}`) =>
  APP_BASE_PATH === "/" ? path : `${APP_BASE_PATH}${path}`;

export const isMockEnabled = () => {
  if (detectEnv() === "prod") return false;
  if (import.meta.env.DEV) return true;
  const flag = import.meta.env.VITE_ENABLE_MOCKS?.toLowerCase();
  return flag === "true";
};

export const TELEMETRY_COLLECTOR_URL = envSwitch({
  prod: () => "https://telemetry.nav.no/collect",
  dev: () => "https://telemetry.ekstern.dev.nav.no/collect",
  local: () => undefined,
  other: () => undefined,
});

export const MIN_SIDE_URL = envSwitch({
  prod: () => "https://arbeidsgiver.nav.no/min-side-arbeidsgiver/",
  dev: () =>
    isMockEnabled()
      ? "https://arbeidsgiver-dev-like.ansatt.dev.nav.no/min-side-arbeidsgiver"
      : "https://arbeidsgiver.intern.dev.nav.no/min-side-arbeidsgiver/",
  local: () => "https://arbeidsgiver.intern.dev.nav.no/min-side-arbeidsgiver/",
});

export const EKSPERTBISTAND_URL = envSwitch({
  prod: () => "https://arbeidsgiver.nav.no/ekspertbistand",
  dev: () => "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand",
  local: () => "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand",
});

export const EKSPERTBISTAND_INFO_URL = "https://www.nav.no/arbeidsgiver/ekspertbistand";

export const EKSPERTBISTAND_API_PATH = withBasePath("/ekspertbistand-backend/api/skjema/v1");
export const EKSPERTBISTAND_TILSKUDDSBREV_HTML_PATH = withBasePath(
  "/ekspertbistand-backend/api/tilsagndata/v1"
);
export const EKSPERTBISTAND_ORGANISASJONER_PATH = withBasePath(
  "/ekspertbistand-backend/api/organisasjoner/v1"
);
export const EKSPERTBISTAND_EREG_ADRESSE_PATH = withBasePath("/ekspertbistand-backend/api/ereg");

export const SOKNADER_PATH = "/soknader";
export const REFUSJON_URL = "https://www.nav.no/fyllut/nav761390?sub=paper";
export const HENT_FORSTESIDE_URL =
  "https://www.nav.no/fyllut-ettersending/lospost?tema=OPP&sub=paper";

export const LOGIN_URL = envSwitch({
  prod: () => `${withBasePath("/oauth2/login")}?redirect=${withBasePath(SOKNADER_PATH)}`,
  dev: () =>
    isMockEnabled()
      ? withBasePath(SOKNADER_PATH)
      : `${withBasePath("/oauth2/login")}?redirect=${withBasePath(SOKNADER_PATH)}`,
  local: () => withBasePath(SOKNADER_PATH),
});

export const SESSION_URL = withBasePath("/oauth2/session");

export const TILGANGSSTYRING_URL =
  "https://www.nav.no/arbeidsgiver/min-side-arbeidsgiver/tilgangsstyring";
