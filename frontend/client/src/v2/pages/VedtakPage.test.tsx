import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MOCK_VEDTAK_SOKNAD_ID } from "../../mocks/handlers";
import { server } from "../../test/server";
import { renderSkjema } from "../../test/skjemaV2";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";

const vedtakssiden = (id: string) => `/v2/skjema/${id}/vedtak`;

class IngenObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

beforeEach(() => {
  vi.stubGlobal("IntersectionObserver", IngenObserver);
  vi.stubGlobal("ResizeObserver", IngenObserver);
  URL.createObjectURL = vi.fn(() => "blob:vedtaksbrev");
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const nokkeldata = (label: string) =>
  screen.queryAllByRole("term").find((term) => term.textContent === label)?.nextElementSibling ??
  null;

const ventPaVedtak = (status: string) =>
  screen.findByRole("heading", { level: 2, name: status }, { timeout: 5000 });

describe("VedtakPage", () => {
  it("viser innvilget beløp, knappene og fristen, men ikke søkt beløp", async () => {
    const id = MOCK_VEDTAK_SOKNAD_ID.INNVILGET;
    renderSkjema(vedtakssiden(id));

    await ventPaVedtak("Søknad godkjent");
    expect(
      screen.getByRole("heading", { name: "50 000 kr satt av til ekspertbistand" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Send inn sluttrapport fra eksperten" })
    ).toHaveAttribute("href", `/v2/skjema/${id}/sluttrapport`);
    expect(screen.getByRole("button", { name: "Søk refusjon" })).toHaveAttribute(
      "href",
      `/v2/skjema/${id}/refusjon`
    );
    expect(screen.getByText("Frist 31. juli 2027")).toBeInTheDocument();
    expect(nokkeldata("Tiltaksperiode")).toHaveTextContent("1. november 2026 – 31. januar 2027");
    expect(nokkeldata("Ekspert")).toHaveTextContent("Eva Ekspert");
    expect(nokkeldata("Søkt beløp")).toBeNull();
  });

  it("viser søkt beløp ved redusert beløp", async () => {
    renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET_REDUSERT));

    await ventPaVedtak("Søknad godkjent med redusert beløp");
    expect(
      screen.getByRole("heading", { name: "40 000 kr satt av til ekspertbistand" })
    ).toBeInTheDocument();
    expect(nokkeldata("Søkt beløp")).toHaveTextContent("50 000 kr");
  });

  it("viser verken beløp, vilkår, knapper eller frist ved avslag", async () => {
    renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.AVSLATT));

    await ventPaVedtak("Søknad avslått");
    expect(screen.queryByText(/satt av til ekspertbistand/)).not.toBeInTheDocument();
    expect(screen.queryByText("Vilkår og informasjon om utbetaling")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /sluttrapport/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Søk refusjon" })).not.toBeInTheDocument();
    expect(screen.queryByText(/^Frist/)).not.toBeInTheDocument();
    expect(nokkeldata("Tiltaksperiode")).toBeNull();
    expect(nokkeldata("Søkt beløp")).toHaveTextContent("50 000 kr");
    expect(screen.getByRole("region", { name: "Vedtak om avslag på søknad" })).toBeInTheDocument();
  });

  it("henter brevet først når vedtakskortet åpnes, og lenker til det med filnavnet", async () => {
    const brevKall: string[] = [];
    const lytter = ({ request }: { request: Request }) => {
      if (request.url.endsWith("/vedtak/brev-pdf")) brevKall.push(request.url);
    };
    server.events.on("request:start", lytter);
    try {
      renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

      await ventPaVedtak("Søknad godkjent");
      expect(brevKall).toHaveLength(0);

      const kort = screen.getByRole("region", { name: "Vedtak om godkjent søknad" });
      await userEvent.click(within(kort).getByRole("button", { expanded: false }));

      const lenke = await within(kort).findByRole("link", {
        name: "Vedtaksbrev ekspertbistand.pdf",
      });
      expect(lenke).toHaveAttribute("href", "blob:vedtaksbrev");
      expect(lenke).toHaveAttribute("download", "Vedtaksbrev ekspertbistand.pdf");
      expect(brevKall).toHaveLength(1);
    } finally {
      server.events.removeListener("request:start", lytter);
    }
  });

  it("viser en feilmelding i kortet når brevet ikke kan hentes", async () => {
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/vedtak/brev-pdf`, () =>
        HttpResponse.json({ message: "Gotenberg er nede" }, { status: 500 })
      )
    );
    renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

    await ventPaVedtak("Søknad godkjent");
    const kort = screen.getByRole("region", { name: "Vedtak om godkjent søknad" });
    await userEvent.click(within(kort).getByRole("button", { expanded: false }));

    expect(
      await within(kort).findByText("Kunne ikke hente vedtaksbrevet akkurat nå. Prøv igjen senere.")
    ).toBeInTheDocument();
  });

  it("går til kvitteringen når søknaden ikke har vedtak", async () => {
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/vedtak`, () =>
        HttpResponse.json({ message: "vedtak ikke funnet" }, { status: 404 })
      )
    );
    renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

    expect(
      await screen.findByRole("heading", { name: "Søknaden godkjent" }, { timeout: 5000 })
    ).toBeInTheDocument();
  });
});
