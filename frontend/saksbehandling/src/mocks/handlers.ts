import { http, bypass, HttpResponse } from "msw";
import type { ForelopigVedtakInput } from "../hooks/useForelopigVedtak";
import type { ForelopigVedtakOppsett } from "../hooks/useForelopigVedtakOppsett";
import type { Mottaker } from "../hooks/useVedtaksbrev";
import type { ForelopigUtfall, ForelopigVedtak, SakDetaljer } from "../hooks/useSak";
import type {
  Oppgavetype,
  SakInfo,
  SakListeElement,
  Saksstatus,
  SoknadStatus,
} from "../hooks/useSaker";
import { VILKAR_TEKSTER, type VilkarId, type VilkarsvurderingDTO } from "../hooks/useVilkår";
import type { SaksloggInnslag, SaksloggResponse } from "../hooks/useSakslogg";
import { mockInnloggetAnsatt } from "../mock/ansatt";
import { tolkSoknadssum } from "../utils/belop";
import {
  SAKSBEHANDLING_FORELOPIG_VEDTAK_BREV_URL,
  SAKSBEHANDLING_FORELOPIG_VEDTAK_FORHANDSVISNING_URL,
  SAKSBEHANDLING_FORELOPIG_VEDTAK_OPPSETT_URL,
  SAKSBEHANDLING_REFUSJON_URL,
  SAKSBEHANDLING_REFUSJON_VEDLEGG_URL,
  SAKSBEHANDLING_SAKER_URL,
  SAKSBEHANDLING_SLUTTRAPPORT_FIL_URL,
  SAKSBEHANDLING_SLUTTRAPPORT_URL,
  SESSION_URL,
} from "../utils/constants";
import { gjeldendeMaksSats } from "../utils/maksSats";

const innslag = (
  id: string,
  tidspunkt: string,
  utfortAvRolle: SaksloggInnslag["utfortAvRolle"],
  utfortAvNavn: string,
  notat: string
): SaksloggInnslag => ({
  id,
  tidspunkt,
  utfortAvRolle,
  utfortAvIdent: utfortAvRolle === "SYSTEM" ? null : "Z999999",
  utfortAvNavn,
  notat,
});

const mockSakslogg: SaksloggInnslag[] = [
  innslag(
    "9",
    "2026-06-17T07:01:00Z",
    "SAKSBEHANDLER",
    "Mina Minnloo",
    "Refusjonskrav sendt til utbetaling"
  ),
  innslag(
    "8",
    "2026-06-17T06:34:00Z",
    "SAKSBEHANDLER",
    "Mina Minnloo",
    "Sluttrapport er registrert som mottatt"
  ),
  innslag(
    "7",
    "2026-06-14T08:41:00Z",
    "SYSTEM",
    "System",
    "Refusjonskrav mottatt fra arbeidsgiver"
  ),
  innslag("6", "2026-06-14T08:32:00Z", "SYSTEM", "System", "Sluttrapport mottatt fra arbeidsgiver"),
  innslag(
    "5",
    "2026-04-06T12:15:00Z",
    "BESLUTTER",
    "Bea Beslutter",
    "Vedtak fattet: Søknad innvilget (22 000 kr)"
  ),
  innslag("4", "2026-04-06T07:45:00Z", "BESLUTTER", "Bea Beslutter", "Sak tildelt"),
  innslag(
    "3",
    "2026-04-05T07:32:00Z",
    "SAKSBEHANDLER",
    "Sara Saksbehandler",
    "Vurdering: Innvilge"
  ),
  innslag("2", "2026-04-02T06:00:00Z", "SAKSBEHANDLER", "Sara Saksbehandler", "Sak tildelt"),
  innslag(
    "1",
    "2026-03-30T10:00:00Z",
    "SYSTEM",
    "System",
    "Søknad mottatt fra arbeidsgiver (Bygg og Anlegg AS)"
  ),
];

const kollega = { ident: "H654321", navn: "Hedda Hansen" };
const mockBeslutter = { ident: "B123456", navn: "Bea Beslutter" };

const mockRoller = { saksbehandler: true, beslutter: true };

type MockSak = Pick<SakInfo, "sakId" | "status" | "saksbehandlerIdent" | "saksbehandlerNavn"> &
  Partial<SakInfo> & {
    soknad: {
      soknadId: string;
      virksomhetsnavn: string;
      ansattNavn: string;
      innsendtTidspunkt: string;
      startdato: string;
      status?: SoknadStatus;
      estimertKostnad?: string;
    };
  };

const mockSaker: MockSak[] = [
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002001",
    status: "OPPRETTET",
    saksbehandlerIdent: null,
    saksbehandlerNavn: null,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001001",
      virksomhetsnavn: "Lomma kommune Måsen omsorgsbolig",
      ansattNavn: "Mona Moonlight",
      innsendtTidspunkt: "2026-10-30T09:12:00Z",
      startdato: "2026-11-22",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002002",
    status: "UNDER_BEHANDLING",
    saksbehandlerIdent: mockInnloggetAnsatt.id,
    saksbehandlerNavn: mockInnloggetAnsatt.navn,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001002",
      virksomhetsnavn: "Hallandsbro hotell AS",
      ansattNavn: "Mari Currire",
      innsendtTidspunkt: "2026-10-28T13:40:00Z",
      startdato: "2026-11-15",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002003",
    status: "TIL_BESLUTNING",
    saksbehandlerIdent: mockInnloggetAnsatt.id,
    saksbehandlerNavn: mockInnloggetAnsatt.navn,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001003",
      virksomhetsnavn: "Mega Sales Dyypvik",
      ansattNavn: "Erik Leverholdt",
      innsendtTidspunkt: "2026-10-27T08:05:00Z",
      startdato: "2026-11-10",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002004",
    status: "UNDER_BEHANDLING",
    saksbehandlerIdent: kollega.ident,
    saksbehandlerNavn: kollega.navn,
    behandlendeEnhet: "0301",
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001004",
      virksomhetsnavn: "Bygg og Fix AS",
      ansattNavn: "Jon Larson",
      innsendtTidspunkt: "2026-10-25T11:30:00Z",
      startdato: "2026-11-01",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002005",
    status: "OPPRETTET",
    saksbehandlerIdent: null,
    saksbehandlerNavn: null,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001005",
      virksomhetsnavn: "Solbakken barnehage",
      ansattNavn: "Viktor Wilhelmsson",
      innsendtTidspunkt: "2026-10-24T07:55:00Z",
      startdato: "2026-11-05",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002006",
    status: "INNVILGET",
    saksbehandlerIdent: "O111222",
    saksbehandlerNavn: "Ola Oppdiktet",
    beslutterIdent: mockBeslutter.ident,
    beslutterNavn: mockBeslutter.navn,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001006",
      virksomhetsnavn: "Flisfiksern AS",
      ansattNavn: "Dorotea Danielssen",
      innsendtTidspunkt: "2026-09-12T10:00:00Z",
      startdato: "2026-10-01",
      status: "godkjent",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002007",
    status: "AVSLATT",
    saksbehandlerIdent: kollega.ident,
    saksbehandlerNavn: kollega.navn,
    beslutterIdent: mockBeslutter.ident,
    beslutterNavn: mockBeslutter.navn,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001007",
      virksomhetsnavn: "Ortopedisk avdeling",
      ansattNavn: "Hanna Sødervik",
      innsendtTidspunkt: "2026-09-08T14:20:00Z",
      startdato: "2026-09-20",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002008",
    status: "AVSLUTTET",
    kildeTilBehandling: "ARENA",
    saksbehandlerIdent: null,
    saksbehandlerNavn: null,
    arenaSakId: "2026123456",
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001008",
      virksomhetsnavn: "Solbro swømlag",
      ansattNavn: "Mons Malmberg",
      innsendtTidspunkt: "2026-08-30T09:00:00Z",
      startdato: "2026-09-15",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002009",
    status: "UNDER_BEHANDLING",
    saksbehandlerIdent: kollega.ident,
    saksbehandlerNavn: kollega.navn,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001009",
      virksomhetsnavn: "Fjordkanten regnskap AS",
      ansattNavn: "Petra Pålsrud",
      innsendtTidspunkt: "2026-10-20T08:15:00Z",
      startdato: "2026-11-03",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002010",
    status: "UNDER_BEHANDLING",
    saksbehandlerIdent: mockInnloggetAnsatt.id,
    saksbehandlerNavn: mockInnloggetAnsatt.navn,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001010",
      virksomhetsnavn: "Nordlys transport AS",
      ansattNavn: "Kari Kalkun",
      innsendtTidspunkt: "2026-10-29T10:45:00Z",
      startdato: "2026-11-22",
      estimertKostnad: "30000",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002011",
    status: "OPPRETTET",
    kildeTilBehandling: "ARENA",
    saksbehandlerIdent: null,
    saksbehandlerNavn: null,
    arenaSakId: "2026654321",
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001011",
      virksomhetsnavn: "Elvebredden snekkerverksted",
      ansattNavn: "Trond Trestamme",
      innsendtTidspunkt: "2026-10-18T12:00:00Z",
      startdato: "2026-11-08",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002012",
    status: "TIL_BESLUTNING",
    saksbehandlerIdent: kollega.ident,
    saksbehandlerNavn: kollega.navn,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001012",
      virksomhetsnavn: "Partner & Billingstad AS",
      ansattNavn: "Anna Lena Hjalmarsson",
      innsendtTidspunkt: "2026-10-16T09:20:00Z",
      startdato: "2026-11-12",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002013",
    status: "TIL_BESLUTNING",
    saksbehandlerIdent: kollega.ident,
    saksbehandlerNavn: kollega.navn,
    beslutterIdent: mockBeslutter.ident,
    beslutterNavn: mockBeslutter.navn,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001013",
      virksomhetsnavn: "Hønbro flytthjelp",
      ansattNavn: "Sofie Segerhård",
      innsendtTidspunkt: "2026-10-14T13:05:00Z",
      startdato: "2026-11-18",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002014",
    status: "TIL_BESLUTNING",
    saksbehandlerIdent: kollega.ident,
    saksbehandlerNavn: kollega.navn,
    beslutterIdent: mockInnloggetAnsatt.id,
    beslutterNavn: mockInnloggetAnsatt.navn,
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001014",
      virksomhetsnavn: "Lillestrøm lydstudio",
      ansattNavn: "Mats Mikrofon",
      innsendtTidspunkt: "2026-10-13T10:15:00Z",
      startdato: "2026-11-20",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002015",
    status: "UNDER_BEHANDLING",
    kildeTilBehandling: "ARENA",
    saksbehandlerIdent: kollega.ident,
    saksbehandlerNavn: kollega.navn,
    arenaSakId: "2026777888",
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001015",
      virksomhetsnavn: "Strandvik bakeri",
      ansattNavn: "Bente Brødskive",
      innsendtTidspunkt: "2026-10-11T08:30:00Z",
      startdato: "2026-11-10",
    },
  },
  {
    sakId: "9c2f7a10-0000-4000-8000-000000002016",
    status: "UNDER_BEHANDLING",
    kildeTilBehandling: "ARENA",
    saksbehandlerIdent: mockInnloggetAnsatt.id,
    saksbehandlerNavn: mockInnloggetAnsatt.navn,
    arenaSakId: "2026999000",
    soknad: {
      soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001016",
      virksomhetsnavn: "Havbris kafé",
      ansattNavn: "Ola Oppskrift",
      innsendtTidspunkt: "2026-10-09T11:45:00Z",
      startdato: "2026-11-05",
    },
  },
];

const MOCK_MAKS_SATSER = [{ gyldigFra: "2026-01-01", belop: 24000 }];

// Ordrett som `forelopig_vedtak.json` i backend.
const mockOppsett: ForelopigVedtakOppsett = {
  standardtekster: {
    INNVILGE_REDUSERT: {
      arbeidsgiver:
        "Dere søkte om {soknadssum} kr, Nav har innvilget {innvilgetBelop} kr i tilskudd til ekspertbistand.",
    },
  },
  maksSatser: MOCK_MAKS_SATSER,
};

const forelopigVedtakStore = new Map<string, ForelopigVedtak>([
  [
    "9c2f7a10-0000-4000-8000-000000002003",
    {
      versjon: 1,
      utfall: "INNVILGE_REDUSERT",
      innvilgetBelop: 17000,
      tiltaksperiodeFra: "2026-11-10",
      tiltaksperiodeTil: "2026-12-31",
      begrunnelseDeltaker:
        "Dere søkte om 22 000 kr, Nav har innvilget 17 000 kr i tilskudd til ekspertbistand.",
      begrunnelseArbeidsgiver:
        "Dere søkte om 22 000 kr, Nav har innvilget 17 000 kr i tilskudd til ekspertbistand.",
      innstiltAvIdent: mockInnloggetAnsatt.id,
      sendtTidspunkt: "2026-10-31T09:30:00Z",
    },
  ],
  [
    "9c2f7a10-0000-4000-8000-000000002012",
    {
      versjon: 1,
      utfall: "INNVILGE",
      innvilgetBelop: 22000,
      tiltaksperiodeFra: "2026-11-12",
      tiltaksperiodeTil: "2026-12-15",
      begrunnelseDeltaker: "Arbeidsgiveren din har fått tilskudd til ekspertbistand.",
      begrunnelseArbeidsgiver: null,
      innstiltAvIdent: kollega.ident,
      sendtTidspunkt: "2026-10-30T12:00:00Z",
    },
  ],
  [
    "9c2f7a10-0000-4000-8000-000000002013",
    {
      versjon: 1,
      utfall: "AVSLAA",
      innvilgetBelop: null,
      tiltaksperiodeFra: null,
      tiltaksperiodeTil: null,
      begrunnelseDeltaker: "Søknaden oppfyller ikke vilkårene for tilskudd.",
      begrunnelseArbeidsgiver: "Søknaden oppfyller ikke vilkårene for tilskudd.",
      innstiltAvIdent: kollega.ident,
      sendtTidspunkt: "2026-10-29T08:00:00Z",
    },
  ],
  [
    "9c2f7a10-0000-4000-8000-000000002014",
    {
      versjon: 1,
      utfall: "INNVILGE",
      innvilgetBelop: 18000,
      tiltaksperiodeFra: "2026-11-20",
      tiltaksperiodeTil: "2026-12-20",
      begrunnelseDeltaker: "Arbeidsgiveren din har fått tilskudd til ekspertbistand.",
      begrunnelseArbeidsgiver: null,
      innstiltAvIdent: kollega.ident,
      sendtTidspunkt: "2026-10-28T14:00:00Z",
    },
  ],
]);

// Speiler tildelingHindring i backend: bare saker som venter på saksbehandler.
const kanTildeles = (sak: Pick<SakInfo, "status" | "saksbehandlerIdent">) =>
  oppgavetype(sak.status) === "SOKNAD" && sak.saksbehandlerIdent !== mockInnloggetAnsatt.id;

const mockRefusjonSakId = "9c2f7a10-0000-4000-8000-000000002010";
const mockRefusjonsvedleggId = "b7fa1e19-6adc-4444-b592-1567343686f6";
const mockPdfBytes = (() => {
  const objekter = [
    "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
    "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
    "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << >> >>\nendobj\n",
  ];
  let pdf = "%PDF-1.4\n";
  const offsetter = [0];
  for (const objekt of objekter) {
    offsetter.push(pdf.length);
    pdf += objekt;
  }
  const xrefStart = pdf.length;
  pdf += `xref\n0 ${offsetter.length}\n0000000000 65535 f \n`;
  pdf += offsetter
    .slice(1)
    .map((offset) => `${String(offset).padStart(10, "0")} 00000 n \n`)
    .join("");
  pdf += `trailer\n<< /Size ${offsetter.length} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`;
  return new TextEncoder().encode(pdf);
})();
const mockRefusjon = {
  belopKroner: 18400,
  utgifter: "Kostnader for rådgivning og tilrettelegging på arbeidsplassen.",
  opprettet: "2026-10-30T13:25:00Z",
  vedlegg: [
    {
      id: mockRefusjonsvedleggId,
      filnavn: "kvittering-arbeidsterapeut.pdf",
      storrelse: mockPdfBytes.length,
    },
  ],
};
const mockSluttrapport = {
  filnavn: "sluttrapport-ekspertbistand.pdf",
  opprettet: "2026-10-30T12:30:00Z",
  storrelse: mockPdfBytes.length,
};

// Speiler oppgavetype i backend.
const oppgavetype = (status: Saksstatus): Oppgavetype | null => {
  switch (status) {
    case "OPPRETTET":
    case "UNDER_BEHANDLING":
      return "SOKNAD";
    case "TIL_BESLUTNING":
      return "SOKNAD_BESLUTTER";
    default:
      return null;
  }
};

const harOppgaveForMeg = (type: Oppgavetype | null) =>
  (mockRoller.saksbehandler && type === "SOKNAD") ||
  (mockRoller.beslutter && type === "SOKNAD_BESLUTTER");

type BeslutterTildelingHindring =
  "MANGLER_ROLLE" | "UGYLDIG_STATUS" | "INHABIL" | "HAR_SAKEN_ALLEREDE";

// Speiler beslutterTildelingHindring i backend.
const beslutterTildelingHindring = (
  sak: Pick<SakInfo, "sakId" | "status" | "beslutterIdent">
): BeslutterTildelingHindring | null => {
  const innstiltAvIdent = forelopigVedtakStore.get(sak.sakId)?.innstiltAvIdent ?? null;
  if (!mockRoller.beslutter) return "MANGLER_ROLLE";
  if (sak.status !== "TIL_BESLUTNING") return "UGYLDIG_STATUS";
  if (innstiltAvIdent === null || innstiltAvIdent === mockInnloggetAnsatt.id) return "INHABIL";
  if (sak.beslutterIdent === mockInnloggetAnsatt.id) return "HAR_SAKEN_ALLEREDE";
  return null;
};

const tilListeElement = ({ soknad, ...mockSak }: MockSak, index: number): SakListeElement => {
  const sak = {
    kildeTilBehandling: "EKSPERTBISTAND" as const,
    behandlendeEnhet: "4710",
    beslutterIdent: null,
    beslutterNavn: null,
    arenaSakId: null,
    ...mockSak,
  };
  const vedtak = forelopigVedtakStore.get(sak.sakId);
  const type = oppgavetype(sak.status);
  return {
    ...sak,
    kanTildeleMeg: sak.saksbehandlerIdent === null && kanTildeles(sak),
    oppgavetype: type,
    harOppgaveForMeg: harOppgaveForMeg(type),
    kanTildeleMegSomBeslutter:
      beslutterTildelingHindring(sak) === null &&
      (sak.beslutterIdent === null || sak.beslutterIdent === vedtak?.innstiltAvIdent),
    tiltaksperiodeFra: vedtak?.tiltaksperiodeFra ?? null,
    tiltaksperiodeTil: vedtak?.tiltaksperiodeTil ?? null,
    soknad: {
      soknadId: soknad.soknadId,
      status: soknad.status ?? "innsendt",
      innsendtTidspunkt: soknad.innsendtTidspunkt,
      virksomhet: {
        virksomhetsnummer: String(876543210 + index),
        virksomhetsnavn: soknad.virksomhetsnavn,
      },
      ansattNavn: soknad.ansattNavn,
      startdato: soknad.startdato,
    },
  };
};

const alleSaker = (): SakListeElement[] => mockSaker.map(tilListeElement);

const mineEnheter = new Set(mockInnloggetAnsatt.enheter.map((e) => e.nummer));

// Speiler backend: saker uten enhet, eller på enhet saksbehandler ikke har tilgang til, er skjult.
const harTilgangTilEnhet = (sak: SakListeElement) =>
  !!sak.behandlendeEnhet && mineEnheter.has(sak.behandlendeEnhet);

const lagVilkårsvurdering = (): VilkarsvurderingDTO[] =>
  (Object.keys(VILKAR_TEKSTER) as VilkarId[]).map((vilkar) => ({ vilkar, godkjent: null }));

const estimertKostnad = (sakId: string) =>
  mockSaker.find((s) => s.sakId === sakId)?.soknad.estimertKostnad ?? "22000";

const lagSakDetaljer = ({ soknad: element, ...sak }: SakListeElement): SakDetaljer => ({
  ...sak,
  kanTildeleMeg: kanTildeles(sak),
  kanTildeleMegSomBeslutter: beslutterTildelingHindring(sak) === null,
  forelopigVedtak: forelopigVedtakStore.get(sak.sakId) ?? null,
  soknad: {
    soknadId: element.soknadId,
    status: element.status,
    innsendtTidspunkt: element.innsendtTidspunkt,
    virksomhet: {
      ...element.virksomhet,
      kontaktperson: {
        navn: "Merete Ferrari",
        epost: "merete@byggogfiks.as",
        telefonnummer: "94342112",
      },
      beliggenhetsadresse: "Drammensveien 123\n3033 Drammen",
    },
    ansatt: {
      fnr: "12018434566",
      navn: element.ansattNavn,
    },
    ekspert: {
      navn: "Eivind Ekspertsen",
      virksomhet: "Eksperter AS",
      virksomhetNavn: "Eksperter AS",
      virksomhetOrgnr: "409231445",
      kompetanse: "Arbeidsterapeut",
      godkjentUtdanningEllerAutorisasjon: ["Arbeidsterapeut"],
      relevantKompetanse: ["Ergonomi"],
    },
    behovForBistand: {
      begrunnelse:
        "Den ansatte har jobbet i virksomheten som salgsmedarbeider (både dag- og kveldstid) de siste 3 årene i en 80 % stilling.",
      tilrettelegging:
        "Den ansatte har hatt hyppig sykefravær det siste året på opptil en uke. Vi har prøvd fleksitid, men det var vanskelig å få til med vaktplanen og de øvrige ansatte.",
      behov:
        "Arbeidsevnevurdering og råd om hvordan arbeidsplassen kan tilrettelegges slik at den ansatte kan stå i jobb.",
      timer: "8",
      estimertKostnad: estimertKostnad(sak.sakId),
      startdato: element.startdato,
    },
    nav: {
      kontaktperson: "Nils Navesen",
    },
  },
});

const vilkårStore = new Map<string, VilkarsvurderingDTO[]>([
  [
    "9c2f7a10-0000-4000-8000-000000002010",
    lagVilkårsvurdering().map((v) => ({
      ...v,
      godkjent: v.vilkar !== "DELTAKER_HAR_SYKEFRAVAERSHISTORIKK",
      vurdertAvIdent: mockInnloggetAnsatt.id,
      vurdertTidspunkt: "2026-10-30T12:00:00Z",
    })),
  ],
]);

function hentVilkårsvurdering(sakId: string): VilkarsvurderingDTO[] {
  const eksisterende = vilkårStore.get(sakId);
  if (eksisterende) return eksisterende;

  const vurderinger = lagVilkårsvurdering();
  vilkårStore.set(sakId, vurderinger);
  return vurderinger;
}

const erMottaker = (verdi: string | null): verdi is Mottaker =>
  verdi === "deltaker" || verdi === "arbeidsgiver";

// Speiler backend sin `valider()` for `<` og `>`: første tekstfelt med brudd gir 400 med feltstien.
function ugyldigInput(verdi: unknown, sti = ""): Response | null {
  if (typeof verdi === "string") {
    if (!/[<>]/.test(verdi)) return null;
    return HttpResponse.json(
      {
        kode: "UGYLDIG_INPUT",
        message: `Ugyldig verdi i felt '${sti}': inneholder < eller >`,
        felt: sti,
      },
      { status: 400 }
    );
  }
  if (Array.isArray(verdi)) {
    for (const [index, element] of verdi.entries()) {
      const feil = ugyldigInput(element, `${sti}[${index}]`);
      if (feil) return feil;
    }
  } else if (verdi && typeof verdi === "object") {
    for (const [nøkkel, barn] of Object.entries(verdi)) {
      const feil = ugyldigInput(barn, sti ? `${sti}.${nøkkel}` : nøkkel);
      if (feil) return feil;
    }
  }
  return null;
}

async function mockVedtaksbrev() {
  const pdf = await fetch(bypass("/mock/eksempel.pdf")).then((r) => r.arrayBuffer());
  return new HttpResponse(pdf, {
    headers: { "Content-Type": "application/pdf", "Cache-Control": "no-store" },
  });
}

// Speiler backend: 404 når saken ikke finnes, 403 når saksbehandler mangler tilgang til enheten.
function finnSakMedTilgang(sakId: string): SakListeElement | Response {
  const sak = alleSaker().find((s) => s.sakId === sakId);
  if (!sak) {
    return HttpResponse.json({ message: "fant ikke sak" }, { status: 404 });
  }
  if (!harTilgangTilEnhet(sak)) {
    return HttpResponse.json(
      {
        kode: "IKKE_TILGANG_ENHET",
        begrunnelse: "Du har ikke tilgang til enheten som behandler saken",
      },
      { status: 403 }
    );
  }
  return sak;
}

export const handlers = [
  http.get(SESSION_URL, () =>
    HttpResponse.json({
      session: {
        ends_in_seconds: 3600,
      },
    })
  ),
  http.get("/api/saksbehandling/v1/meg", () => HttpResponse.json(mockInnloggetAnsatt)),
  http.get(SAKSBEHANDLING_SAKER_URL, () =>
    HttpResponse.json({ saker: alleSaker().filter(harTilgangTilEnhet) })
  ),
  http.get("/api/saksbehandling/v1/saker/:sakId", ({ params }) => {
    const sak = alleSaker().find((s) => s.sakId === params.sakId);
    if (!sak) {
      return HttpResponse.json({ message: "Fant ikke saken." }, { status: 404 });
    }
    if (!harTilgangTilEnhet(sak)) {
      return HttpResponse.json(
        {
          kode: "IKKE_TILGANG_ENHET",
          begrunnelse: "Du har ikke tilgang til enheten som behandler saken",
        },
        { status: 403 }
      );
    }
    return HttpResponse.json(lagSakDetaljer(sak));
  }),
  http.get(SAKSBEHANDLING_REFUSJON_URL(":sakId"), ({ params }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;
    if (sak.sakId !== mockRefusjonSakId || sak.kildeTilBehandling === "ARENA") {
      return new HttpResponse(null, { status: 204 });
    }
    return HttpResponse.json(mockRefusjon);
  }),
  http.get(SAKSBEHANDLING_REFUSJON_VEDLEGG_URL(":sakId", ":vedleggId"), ({ params }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;
    if (sak.sakId !== mockRefusjonSakId || String(params.vedleggId) !== mockRefusjonsvedleggId) {
      return HttpResponse.json({ message: "Fant ikke vedlegget." }, { status: 404 });
    }
    return new HttpResponse(mockPdfBytes, {
      headers: {
        "Content-Type": "application/pdf",
        "Content-Disposition": 'attachment; filename="kvittering-arbeidsterapeut.pdf"',
      },
    });
  }),
  http.get(SAKSBEHANDLING_SLUTTRAPPORT_URL(":sakId"), ({ params }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;
    if (sak.sakId !== mockRefusjonSakId || sak.kildeTilBehandling === "ARENA") {
      return new HttpResponse(null, { status: 204 });
    }
    return HttpResponse.json(mockSluttrapport);
  }),
  http.get(SAKSBEHANDLING_SLUTTRAPPORT_FIL_URL(":sakId"), ({ params }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;
    if (sak.sakId !== mockRefusjonSakId || sak.kildeTilBehandling === "ARENA") {
      return HttpResponse.json({ message: "Fant ikke sluttrapporten." }, { status: 404 });
    }
    return new HttpResponse(mockPdfBytes, {
      headers: {
        "Content-Type": "application/pdf",
        "Content-Disposition": 'inline; filename="sluttrapport-ekspertbistand.pdf"',
      },
    });
  }),
  http.get("/api/saksbehandling/v1/saker/:sakId/vilkarsvurdering", ({ params }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;
    // Arena-saker har ingen vilkår (spec 2c).
    if (sak.kildeTilBehandling === "ARENA") return HttpResponse.json([]);
    return HttpResponse.json(hentVilkårsvurdering(sak.sakId));
  }),
  http.get("/api/saksbehandling/v1/saker/:sakId/logg", () =>
    HttpResponse.json<SaksloggResponse>({ innslag: mockSakslogg })
  ),
  http.patch<
    { sakId: string },
    { vilkar?: string; godkjent?: boolean | null; notat?: string | null }
  >("/api/saksbehandling/v1/saker/:sakId/vilkarsvurdering", async ({ params, request }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;

    if (sak.saksbehandlerIdent !== mockInnloggetAnsatt.id) {
      return HttpResponse.json(
        { kode: "IKKE_TILDELT_SAK", begrunnelse: "Du er ikke tildelt saken" },
        { status: 403 }
      );
    }

    const body = await request.json().catch(() => null);
    const vilkar = body?.vilkar;
    const godkjent = body?.godkjent;
    if (typeof vilkar !== "string" || !(godkjent === null || typeof godkjent === "boolean")) {
      return HttpResponse.json({ message: "ugyldig vilkårsvurdering" }, { status: 400 });
    }
    const feil = ugyldigInput(body);
    if (feil) return feil;
    if (sak.kildeTilBehandling === "ARENA") {
      return HttpResponse.json({ message: "fant ikke vilkår på saken" }, { status: 404 });
    }
    if (sak.status !== "UNDER_BEHANDLING") {
      return HttpResponse.json({ message: "Saken er ikke under behandling" }, { status: 409 });
    }

    const oppdatert: VilkarsvurderingDTO = {
      vilkar: vilkar as VilkarId,
      godkjent,
      notat: body?.notat?.trim() || null,
      vurdertAvIdent: mockInnloggetAnsatt.id,
      vurdertTidspunkt: new Date().toISOString(),
    };

    vilkårStore.set(
      sak.sakId,
      hentVilkårsvurdering(sak.sakId).map((v) => (v.vilkar === oppdatert.vilkar ? oppdatert : v))
    );

    return new HttpResponse(null, { status: 202 });
  }),
  http.get(SAKSBEHANDLING_FORELOPIG_VEDTAK_OPPSETT_URL, () =>
    HttpResponse.json<ForelopigVedtakOppsett>(mockOppsett)
  ),
  http.post(
    SAKSBEHANDLING_FORELOPIG_VEDTAK_FORHANDSVISNING_URL(":sakId"),
    async ({ params, request }) => {
      const sak = finnSakMedTilgang(String(params.sakId));
      if (sak instanceof Response) return sak;
      if (!erMottaker(new URL(request.url).searchParams.get("mottaker"))) {
        return HttpResponse.json({ message: "Ukjent mottaker" }, { status: 400 });
      }
      if (sak.kildeTilBehandling === "ARENA") {
        return HttpResponse.json(
          { kode: "SAK_UGYLDIG_KILDE", message: "Saken behandles i Arena" },
          { status: 409 }
        );
      }
      if (sak.saksbehandlerIdent !== mockInnloggetAnsatt.id) {
        return HttpResponse.json(
          { kode: "IKKE_TILDELT_SAK", begrunnelse: "Du er ikke tildelt saken" },
          { status: 403 }
        );
      }
      const feil = ugyldigInput(await request.json().catch(() => null));
      if (feil) return feil;
      return mockVedtaksbrev();
    }
  ),
  http.get(SAKSBEHANDLING_FORELOPIG_VEDTAK_BREV_URL(":sakId", ":mottaker"), ({ params }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;
    if (!erMottaker(String(params.mottaker))) {
      return HttpResponse.json({ message: "Ukjent mottaker" }, { status: 400 });
    }
    return mockVedtaksbrev();
  }),
  http.post<{ sakId: string }, Partial<ForelopigVedtakInput>>(
    "/api/saksbehandling/v1/saker/:sakId/forelopig-vedtak",
    async ({ params, request }) => {
      const sak = finnSakMedTilgang(String(params.sakId));
      if (sak instanceof Response) return sak;
      if (sak.kildeTilBehandling === "ARENA") {
        return HttpResponse.json(
          { kode: "SAK_UGYLDIG_KILDE", message: "Saken behandles i Arena" },
          { status: 409 }
        );
      }
      if (sak.saksbehandlerIdent !== mockInnloggetAnsatt.id) {
        return HttpResponse.json(
          { kode: "IKKE_TILDELT_SAK", begrunnelse: "Du er ikke tildelt saken" },
          { status: 403 }
        );
      }

      const body = await request.json().catch(() => null);
      const ugyldig = () => HttpResponse.json({ message: "ugyldig input" }, { status: 400 });
      const feil = ugyldigInput(body);
      if (feil) return feil;
      const utfall = body?.utfall;
      if (
        !body ||
        !utfall ||
        !(["INNVILGE", "INNVILGE_REDUSERT", "AVSLAA"] as ForelopigUtfall[]).includes(utfall) ||
        !body.begrunnelseDeltaker?.trim()
      ) {
        return ugyldig();
      }
      const fra = body.tiltaksperiodeFra ?? null;
      const til = body.tiltaksperiodeTil ?? null;
      const sats = fra ? gjeldendeMaksSats(MOCK_MAKS_SATSER, fra) : undefined;
      if (utfall === "AVSLAA") {
        if (fra || til || body.innvilgetBelop != null || !body.begrunnelseArbeidsgiver?.trim()) {
          return ugyldig();
        }
      } else {
        if (!fra || !til || fra > til || fra.slice(0, 4) !== til.slice(0, 4) || !sats) {
          return ugyldig();
        }
        const belop = body.innvilgetBelop;
        if (utfall === "INNVILGE" && (belop != null || body.begrunnelseArbeidsgiver != null)) {
          return ugyldig();
        }
        if (
          utfall === "INNVILGE_REDUSERT" &&
          (typeof belop !== "number" ||
            belop <= 0 ||
            belop > sats.belop ||
            !body.begrunnelseArbeidsgiver?.trim())
        ) {
          return ugyldig();
        }
      }

      if (sak.status !== "UNDER_BEHANDLING") {
        return HttpResponse.json(
          { kode: "SAK_UGYLDIG_STATUS", message: "Saken er ikke under behandling" },
          { status: 409 }
        );
      }
      if (hentVilkårsvurdering(sak.sakId).some((v) => v.godkjent == null)) {
        return HttpResponse.json(
          { kode: "VILKAR_IKKE_VURDERT", message: "Alle vilkår må være vurdert" },
          { status: 409 }
        );
      }
      const soknadssum = tolkSoknadssum(estimertKostnad(sak.sakId));
      if (utfall !== "AVSLAA" && soknadssum === null) {
        return HttpResponse.json(
          { kode: "UGYLDIG_SOKNADSSUM", message: "Søknadssummen er ikke et gyldig beløp" },
          { status: 409 }
        );
      }
      if (utfall === "INNVILGE" && sats && soknadssum !== null && soknadssum > sats.belop) {
        return HttpResponse.json(
          { kode: "BELOP_OVER_SATS", message: "Søknadssummen er høyere enn maks sats" },
          { status: 409 }
        );
      }
      if (
        utfall === "INNVILGE_REDUSERT" &&
        soknadssum !== null &&
        (body.innvilgetBelop ?? 0) >= soknadssum
      ) {
        return ugyldig();
      }

      const mockSak = mockSaker.find((s) => s.sakId === sak.sakId)!;
      mockSak.status = "TIL_BESLUTNING";
      forelopigVedtakStore.set(sak.sakId, {
        versjon: (forelopigVedtakStore.get(sak.sakId)?.versjon ?? 0) + 1,
        utfall,
        innvilgetBelop: utfall === "INNVILGE" ? soknadssum : (body.innvilgetBelop ?? null),
        tiltaksperiodeFra: fra,
        tiltaksperiodeTil: til,
        begrunnelseDeltaker: body.begrunnelseDeltaker.trim(),
        begrunnelseArbeidsgiver: body.begrunnelseArbeidsgiver?.trim() ?? null,
        innstiltAvIdent: mockInnloggetAnsatt.id,
        sendtTidspunkt: new Date().toISOString(),
      });
      return new HttpResponse(null, { status: 202 });
    }
  ),
  http.post("/api/saksbehandling/v1/saker/:sakId/tildeling", ({ params }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;
    if (oppgavetype(sak.status) !== "SOKNAD") {
      return HttpResponse.json(
        {
          kode: "SAK_UGYLDIG_STATUS",
          message: `Saken kan ikke tildeles med status ${sak.status}`,
        },
        { status: 409 }
      );
    }

    const mockSak = mockSaker.find((s) => s.sakId === sak.sakId)!;
    mockSak.saksbehandlerIdent = mockInnloggetAnsatt.id;
    mockSak.saksbehandlerNavn = mockInnloggetAnsatt.navn;
    if (mockSak.status === "OPPRETTET") mockSak.status = "UNDER_BEHANDLING";
    return new HttpResponse(null, { status: 202 });
  }),
  http.post("/api/saksbehandling/v1/saker/:sakId/tildeling/beslutter", ({ params }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;
    switch (beslutterTildelingHindring(sak)) {
      case "MANGLER_ROLLE":
        return HttpResponse.json(
          { kode: "MANGLER_ROLLE", begrunnelse: "krever rolle beslutter" },
          { status: 403 }
        );
      case "UGYLDIG_STATUS":
        return HttpResponse.json(
          {
            kode: "SAK_UGYLDIG_STATUS",
            message: `Saken kan ikke tildeles beslutter med status ${sak.status}`,
          },
          { status: 409 }
        );
      case "INHABIL":
        return HttpResponse.json(
          {
            kode: "BESLUTTER_INHABIL",
            begrunnelse: "Du sendte det foreløpige vedtaket og kan ikke være beslutter på saken",
          },
          { status: 403 }
        );
      case "HAR_SAKEN_ALLEREDE":
        return new HttpResponse(null, { status: 202 });
    }

    const mockSak = mockSaker.find((s) => s.sakId === sak.sakId)!;
    mockSak.beslutterIdent = mockInnloggetAnsatt.id;
    mockSak.beslutterNavn = mockInnloggetAnsatt.navn;
    return new HttpResponse(null, { status: 202 });
  }),
  http.delete("/api/saksbehandling/v1/saker/:sakId/tildeling", ({ params }) => {
    const sak = finnSakMedTilgang(String(params.sakId));
    if (sak instanceof Response) return sak;
    if (sak.saksbehandlerIdent !== mockInnloggetAnsatt.id) {
      return HttpResponse.json(
        { kode: "IKKE_TILDELT_SAK", begrunnelse: "Du er ikke tildelt saken" },
        { status: 403 }
      );
    }

    const mockSak = mockSaker.find((s) => s.sakId === sak.sakId)!;
    mockSak.saksbehandlerIdent = null;
    mockSak.saksbehandlerNavn = null;
    return new HttpResponse(null, { status: 202 });
  }),
];
