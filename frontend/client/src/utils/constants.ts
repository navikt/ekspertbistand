import { envSwitch } from "./env";

// Collector URL for forwarding NAIS telemetry events
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

export const SKJEMA_API_PATH = "/api/skjema/v1";

export const APPLICATIONS_PATH = "/soknader";

export const LOGIN_URL = envSwitch({
  prod: () => "/oauth2/login?redirect=/ekspertbistand/soknader",
  dev: () => "/oauth2/login?redirect=/ekspertbistand/soknader",
  local: () => "/soknader",
});

export const TILGANGSSTYRING_URL =
  "https://www.nav.no/arbeidsgiver/min-side-arbeidsgiver/tilgangsstyring";
