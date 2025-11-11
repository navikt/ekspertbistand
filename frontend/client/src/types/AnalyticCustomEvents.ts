import type { AnalyticsEvent } from "@navikt/nav-dekoratoren-moduler";

type SkjemaInnsendingAvbrutt = AnalyticsEvent<
  "skjema innsending avbrutt",
  {
    skjemanavn: string;
  }
>;

type InnsendteSkjemaer = AnalyticsEvent<
  "innsendte skjemaer",
  {
    antallSkjemaer: number;
  }
>;

type VirksomhetValgt = AnalyticsEvent<
  "virksomhet valgt",
  {
    virksomhetinfo: unknown;
  }
>;

export type CustomEvents = SkjemaInnsendingAvbrutt | InnsendteSkjemaer | VirksomhetValgt;
