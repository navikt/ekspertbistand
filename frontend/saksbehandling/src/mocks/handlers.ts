import { http, HttpResponse } from "msw";
import { mockInnloggetAnsatt } from "../mock/ansatt";
import { SAKSBEHANDLING_OVERSIKT_URL, SESSION_URL } from "../utils/constants";

let valgtEnhetsnummer = mockInnloggetAnsatt.gjeldendeEnhet.nummer;

const oversikt = [
  {
    id: "sak-1001",
    virksomhet: "Eksempel Bedrift AS",
    deltaker: "Ola Nordmann",
    status: "Til behandling",
    saksbehandler: "Silje Saksbehandler",
    opprettetDato: "2026-04-18",
    tilsagnNummer: "2026-101-1",
  },
  {
    id: "sak-1002",
    virksomhet: "Demo Solutions AS",
    deltaker: "Eva Hansen",
    status: "Avventer svar",
    saksbehandler: "Silje Saksbehandler",
    opprettetDato: "2026-04-15",
  },
  {
    id: "sak-1003",
    virksomhet: "Testfirma Norge AS",
    deltaker: "Per Pedersen",
    status: "Ferdigstilt",
    saksbehandler: "Vurderer Vilkårsen",
    opprettetDato: "2026-04-09",
    tilsagnNummer: "2026-087-2",
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
  http.get("/api/ansatte/meg", () => {
    const gjeldendeEnhet =
      mockInnloggetAnsatt.enheter.find((enhet) => enhet.nummer === valgtEnhetsnummer) ??
      mockInnloggetAnsatt.gjeldendeEnhet;

    return HttpResponse.json({
      ...mockInnloggetAnsatt,
      gjeldendeEnhet,
    });
  }),
  http.post("/api/ansatte/enhet", async ({ request }) => {
    const body = (await request.json()) as { valgtEnhetsnummer?: string };
    if (body.valgtEnhetsnummer) {
      valgtEnhetsnummer = body.valgtEnhetsnummer;
    }
    return new HttpResponse(null, { status: 204 });
  }),
  http.get(SAKSBEHANDLING_OVERSIKT_URL, () =>
    HttpResponse.json({
      saker: oversikt,
    })
  ),
];
