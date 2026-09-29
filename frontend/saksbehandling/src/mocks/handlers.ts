import { http, HttpResponse } from "msw";
import type { SoknadDetaljer } from "../hooks/useSoknad";
import type { SakInfo, SoknadListeElement } from "../hooks/useSoknader";
import type { Vilkår, Vilkårstatus } from "../hooks/useVilkår";
import { mockInnloggetAnsatt } from "../mock/ansatt";
import { SAKSBEHANDLING_SOKNADER_URL, SESSION_URL } from "../utils/constants";

type MockSoknad = {
  soknadId: string;
  virksomhetsnavn: string;
  ansattNavn: string;
  innsendtTidspunkt: string;
  startdato: string;
  soknadStatus?: SoknadListeElement["soknadStatus"];
  sak: (Pick<SakInfo, "sakId" | "status" | "saksbehandlerIdent"> & Partial<SakInfo>) | null;
};

const mockSoknader: MockSoknad[] = [
  {
    soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001001",
    virksomhetsnavn: "Lomma kommune Måsen omsorgsbolig",
    ansattNavn: "Mona Moonlight",
    innsendtTidspunkt: "2026-10-30T09:12:00Z",
    startdato: "2026-11-22",
    sak: null,
  },
  {
    soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001002",
    virksomhetsnavn: "Hallandsbro hotell AS",
    ansattNavn: "Mari Currire",
    innsendtTidspunkt: "2026-10-28T13:40:00Z",
    startdato: "2026-11-15",
    sak: {
      sakId: "9c2f7a10-0000-4000-8000-000000002002",
      status: "UNDER_BEHANDLING",
      saksbehandlerIdent: mockInnloggetAnsatt.id,
    },
  },
  {
    soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001003",
    virksomhetsnavn: "Mega Sales Dyypvik",
    ansattNavn: "Erik Leverholdt",
    innsendtTidspunkt: "2026-10-27T08:05:00Z",
    startdato: "2026-11-10",
    sak: {
      sakId: "9c2f7a10-0000-4000-8000-000000002003",
      status: "TIL_BESLUTNING",
      saksbehandlerIdent: mockInnloggetAnsatt.id,
      beslutterIdent: "B123456",
    },
  },
  {
    soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001004",
    virksomhetsnavn: "Bygg og Fix AS",
    ansattNavn: "Jon Larson",
    innsendtTidspunkt: "2026-10-25T11:30:00Z",
    startdato: "2026-11-01",
    sak: {
      sakId: "9c2f7a10-0000-4000-8000-000000002004",
      status: "UNDER_BEHANDLING",
      saksbehandlerIdent: "H654321",
    },
  },
  {
    soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001005",
    virksomhetsnavn: "Solbakken barnehage",
    ansattNavn: "Viktor Wilhelmsson",
    innsendtTidspunkt: "2026-10-24T07:55:00Z",
    startdato: "2026-11-05",
    sak: {
      sakId: "9c2f7a10-0000-4000-8000-000000002005",
      status: "OPPRETTET",
      saksbehandlerIdent: null,
    },
  },
  {
    soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001006",
    virksomhetsnavn: "Flisfiksern AS",
    ansattNavn: "Dorotea Danielssen",
    innsendtTidspunkt: "2026-09-12T10:00:00Z",
    startdato: "2026-10-01",
    soknadStatus: "godkjent",
    sak: {
      sakId: "9c2f7a10-0000-4000-8000-000000002006",
      status: "INNVILGET",
      saksbehandlerIdent: "O111222",
      beslutterIdent: "B123456",
    },
  },
  {
    soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001007",
    virksomhetsnavn: "Ortopedisk avdeling",
    ansattNavn: "Hanna Sødervik",
    innsendtTidspunkt: "2026-09-08T14:20:00Z",
    startdato: "2026-09-20",
    sak: {
      sakId: "9c2f7a10-0000-4000-8000-000000002007",
      status: "AVSLATT",
      saksbehandlerIdent: "H654321",
      beslutterIdent: "B123456",
    },
  },
  {
    soknadId: "5b1e0d3c-1f4a-4c1e-9a51-000000001008",
    virksomhetsnavn: "Solbro swømlag",
    ansattNavn: "Mons Malmberg",
    innsendtTidspunkt: "2026-08-30T09:00:00Z",
    startdato: "2026-09-15",
    sak: {
      sakId: "9c2f7a10-0000-4000-8000-000000002008",
      status: "AVSLUTTET",
      kildeTilBehandling: "ARENA",
      saksbehandlerIdent: null,
      arenaSakId: "2026123456",
    },
  },
];

const tilSakInfo = (sak: NonNullable<MockSoknad["sak"]>): SakInfo => ({
  kildeTilBehandling: "EKSPERTBISTAND",
  behandlendeEnhet: "4710",
  beslutterIdent: null,
  arenaSakId: null,
  ...sak,
});

const tilListeElement = (soknad: MockSoknad, index: number): SoknadListeElement => ({
  soknadId: soknad.soknadId,
  soknadStatus: soknad.soknadStatus ?? "innsendt",
  innsendtTidspunkt: soknad.innsendtTidspunkt,
  virksomhet: {
    virksomhetsnummer: String(876543210 + index),
    virksomhetsnavn: soknad.virksomhetsnavn,
  },
  ansattNavn: soknad.ansattNavn,
  startdato: soknad.startdato,
  sak: soknad.sak ? tilSakInfo(soknad.sak) : null,
});

const soknadListe = (): SoknadListeElement[] => mockSoknader.map(tilListeElement);

const lagVilkår = (): Vilkår[] => [
  {
    id: "arbeidsforhold",
    tittel: "Arbeidsforhold",
    beskrivelse: "Deltaker må ha et arbeidsforhold hos arbeidsgiver i Aa-reg",
    vurdering: {
      automatisk: true,
      status: "oppfylt",
    },
  },
  {
    id: "deltaker-enig",
    tittel: "Deltaker er enig",
    beskrivelse: "Arbeidsgiver har oppgitt at deltaker gitt samtykke til at søknaden sendtes.",
    vurdering: {
      automatisk: true,
      status: "oppfylt",
    },
  },
  {
    id: "provd-tilrettelegging",
    tittel: "Prøvd tilrettelegging",
    beskrivelse: "Arbeidsgiver har beskrevet hvilke tiltak de prøvd eller vurdert.",
    vurdering: {
      automatisk: false,
      status: "ikke_vurdert",
    },
  },
  {
    id: "sykefravarshistorikk",
    tittel: "Sykefraværshistorikk",
    beskrivelse: "Må ha legemeldt sykefravær som er hyppig eller gjentakerende.",
    vurdering: {
      automatisk: false,
      status: "ikke_vurdert",
    },
  },
  {
    id: "ekspert-kompetanse",
    tittel: "Ekspertens kompetanse og uavhengighet",
    beskrivelse: "Må ha offentlig godkjent utdanning og relevant arbeidsrelatert kompentanse.",
    vurdering: {
      automatisk: false,
      status: "ikke_vurdert",
    },
  },
];

const lagSoknadDetaljer = (element: SoknadListeElement): SoknadDetaljer => ({
  soknadId: element.soknadId,
  soknadStatus: element.soknadStatus,
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
    estimertKostnad: "22000",
    startdato: element.startdato,
  },
  nav: {
    kontaktperson: "Nils Navesen",
  },
  sak: element.sak,
});

const vilkårStore = new Map<string, Vilkår[]>();

function hentVilkår(sakId: string): Vilkår[] {
  const eksisterende = vilkårStore.get(sakId);
  if (eksisterende) return eksisterende;

  const vilkår = lagVilkår();
  vilkårStore.set(sakId, vilkår);
  return vilkår;
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
  http.get(SAKSBEHANDLING_SOKNADER_URL, () => HttpResponse.json({ soknader: soknadListe() })),
  http.get("/api/saksbehandling/v1/soknader/:soknadId", ({ params }) => {
    const element = soknadListe().find((s) => s.soknadId === params.soknadId);
    if (!element) {
      return HttpResponse.json({ message: "Fant ikke søknaden." }, { status: 404 });
    }
    return HttpResponse.json(lagSoknadDetaljer(element));
  }),
  http.get("/api/saksbehandling/v1/saker/:sakId/vilkar", ({ params }) =>
    HttpResponse.json(hentVilkår(String(params.sakId)))
  ),
  http.put<
    { sakId: string; vilkarId: string },
    { status: Vilkårstatus; kommentar?: string },
    Vilkår | { message: string }
  >("/api/saksbehandling/v1/saker/:sakId/vilkar/:vilkarId", async ({ params, request }) => {
    const { sakId, vilkarId } = params;
    const body = await request.json();

    if (body.status !== "oppfylt" && body.status !== "ikke_oppfylt") {
      return HttpResponse.json({ message: "Ugyldig status på vilkårsvurdering." }, { status: 400 });
    }

    const alleVilkår = hentVilkår(String(sakId));
    const vilkår = alleVilkår.find((v) => v.id === vilkarId);

    if (!vilkår) {
      return HttpResponse.json({ message: "Fant ikke vilkåret." }, { status: 404 });
    }

    const kommentar = body.kommentar?.trim();
    const oppdatert: Vilkår = {
      ...vilkår,
      vurdering: {
        ...(kommentar ? { kommentar } : {}),
        status: body.status,
        automatisk: false,
        vurdertAv: mockInnloggetAnsatt.navn,
        vurdertTidspunkt: new Date().toISOString(),
      },
    };

    vilkårStore.set(
      String(sakId),
      alleVilkår.map((v) => (v.id === vilkarId ? oppdatert : v))
    );

    return HttpResponse.json(oppdatert);
  }),
];
