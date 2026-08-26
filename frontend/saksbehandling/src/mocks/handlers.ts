import { http, HttpResponse } from "msw";
import { mockInnloggetAnsatt } from "../mock/ansatt";
import { SAKSBEHANDLING_OVERSIKT_URL, SESSION_URL } from "../utils/constants";

const oversikt = [
  {
    id: "sak-1001",
    virksomhet: "Lomma kommune Måsen omsorgsbolig",
    deltaker: "Mona Moonlight",
    status: "Til behandling",
    saksbehandler: null,
    oppgavetype: "Søknad",
    tiltaksperiodeFra: "2020-02-02",
    tiltaksperiodeTil: "2020-02-02",
    opprettetDato: "2020-02-02",
  },
  {
    id: "sak-1002",
    virksomhet: "Hallandsbro hotell AS",
    deltaker: "Mari Currire",
    status: "Til behandling",
    saksbehandler: "Hanne Jensen",
    oppgavetype: "Refusjon",
    tiltaksperiodeFra: "2020-02-02",
    tiltaksperiodeTil: "2020-02-02",
    opprettetDato: "2020-02-02",
  },
  {
    id: "sak-1003",
    virksomhet: "Mega Sales Dyypvik",
    deltaker: "Erik Leverholdt",
    status: "Avventer svar",
    saksbehandler: "Hanne Jensen",
    oppgavetype: "Søknad - beslutter",
    tiltaksperiodeFra: "2020-02-02",
    tiltaksperiodeTil: "2020-02-02",
    opprettetDato: "2020-02-02",
  },
  {
    id: "sak-1004",
    virksomhet: "Bygg og Fix AS",
    deltaker: "Jon Larson",
    status: "Til behandling",
    saksbehandler: "Hans Hansen",
    oppgavetype: "Refusjon",
    tiltaksperiodeFra: "2020-02-02",
    tiltaksperiodeTil: "2020-02-02",
    opprettetDato: "2020-02-02",
  },
  {
    id: "sak-1005",
    virksomhet: "Solbakken barnehage",
    deltaker: "Viktor Wilhelmsson",
    status: "Til behandling",
    saksbehandler: null,
    oppgavetype: "Søknad",
    tiltaksperiodeFra: "2020-02-02",
    tiltaksperiodeTil: "2020-02-02",
    opprettetDato: "2020-02-02",
  },
  {
    id: "sak-1006",
    virksomhet: "Flisfiksern AS",
    deltaker: "Dorotea Danielssen",
    status: "Ferdigstilt",
    saksbehandler: null,
    oppgavetype: "Refusjon",
    tiltaksperiodeFra: "2020-02-02",
    tiltaksperiodeTil: "2020-02-02",
    opprettetDato: "2020-02-02",
  },
  {
    id: "sak-1007",
    virksomhet: "Ortopedisk avdeling",
    deltaker: "Hanna Sødervik",
    status: "Avventer svar",
    saksbehandler: "Henrik Ripsen",
    oppgavetype: "Søknad",
    tiltaksperiodeFra: "2020-02-02",
    tiltaksperiodeTil: "2020-02-02",
    opprettetDato: "2020-02-02",
  },
  {
    id: "sak-1008",
    virksomhet: "Solbro swømlag",
    deltaker: "Mons Malmberg",
    status: "Ferdigstilt",
    saksbehandler: "Ola Olavsen",
    oppgavetype: "Søknad",
    tiltaksperiodeFra: "2020-02-02",
    tiltaksperiodeTil: "2020-02-02",
    opprettetDato: "2020-02-02",
  },
] as const;

export const handlers = [
  http.get(SESSION_URL, () =>
    HttpResponse.json({
      session: {
        ends_in_seconds: 3600,
      },
    })
  ),
  http.get("/api/saksbehandling/v1/meg", () => HttpResponse.json(mockInnloggetAnsatt)),
  http.get(SAKSBEHANDLING_OVERSIKT_URL, () =>
    HttpResponse.json({
      saker: oversikt,
    })
  ),
  http.get("/api/saksbehandling/v1/saker/:sakId", ({ params }) => {
    const { sakId } = params;
    return HttpResponse.json({
      id: sakId,
      deltaker: {
        navn: "Moon Moonlight",
        alder: 37,
        fnr: "120184 34566",
      },
      arbeidsgiver: {
        navn: "Bygg og Anlegg AS",
        orgNr: "876 543 210",
        beliggenhetssadresse: "Drammensveien 123\n120 33 Drammen",
        kontaktperson: "Merte Ferrari",
        epost: "merete@byggogfiks.as",
        telefon: "94 34 21 12",
      },
      ekspert: {
        navn: "Eivind Ekspertseen",
        tilknyttetVirksomhet: "Eksperter AS",
        kompetanse: "Arbeidsterpeuft",
        orgNr: "409 231 445",
      },
      situasjon: {
        arbeidssituasjon:
          "Den ansatte har jobbet i virksomheten som salgsmedarbeidere (både dag- og kveldstid) de siste 3 årene i en 80% stilling. Oppgavene består i å …. Den ansatte har jobbet i virksomheten som salgsmedarbeidere (både dag- og kveldstid) de siste 3 årene i en 80% stilling.",
        sykefravær:
          "Her skal det stå informasjon om hva som er prøvd og hvordan det har gått, og har hatt hyppige sykefravær de siste året på opptil en uke. Fleksitid har vi prøvd, det men det var vanskelig med skjemaet og de øvrige ansatte.",
      },
      ekspertbistand: {
        hvaHjelpeMed:
          "Arbeidsevnevurdering og massa mer her får man skrive litt mer og forklare slikt at det er tydlig hva noen forventer seg.",
        antallTimer: 8,
        søknadssum: 22000,
        startdato: "2026-11-22",
        sendtInnTilNav: "2026-10-30",
      },
      vilkår: [
        {
          id: "arbeidsforhold",
          tittel: "Arbeidsforhold",
          beskrivelse: "Deltaker må ha et arbeidsforhold hos arbeidsgiver i Aa-reg",
          status: "oppfylt",
          automatisk: true,
        },
        {
          id: "deltaker-enig",
          tittel: "Deltaker er enig",
          beskrivelse:
            "Arbeidsgiver har oppgitt at deltaker gitt samtykke til at søknaden sendtes.",
          status: "oppfylt",
          automatisk: true,
        },
        {
          id: "provd-tilrettelegging",
          tittel: "Prøvd tilrettelegging",
          beskrivelse: "Arbeidsgiver har beskrevet hvilke tiltak de prøvd eller vurdert.",
          status: "manuell",
        },
        {
          id: "sykefravarshistorikk",
          tittel: "Sykefraværshistorikk",
          beskrivelse: "Må ha legemeldt sykefravær som er hyppig eller gjentakerende.",
          status: "manuell",
        },
        {
          id: "ekspert-kompetanse",
          tittel: "Ekspertens kompetanse og uavhengighet",
          beskrivelse:
            "Må ha offentlig godkjent utdanning og relevant arbeidsrelatert kompentanse.",
          status: "manuell",
        },
      ],
    });
  }),
];
