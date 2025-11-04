import { envSwitch } from "./env";

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
  prod: () => "/oauth2/login",
  dev: () => "/oauth2/login",
  local: () => "/soknader",
});

export const TILGANGSSTYRING_URL =
  "https://www.nav.no/arbeidsgiver/min-side-arbeidsgiver/tilgangsstyring";
