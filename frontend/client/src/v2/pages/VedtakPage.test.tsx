import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MOCK_VEDTAK_SOKNAD_ID } from "../../mocks/handlers";
import { server } from "../../test/server";
import { renderSkjema } from "../../test/skjemaV2";
import { EKSPERTBISTAND_API_PATH, EKSPERTBISTAND_SLUTTRAPPORT_PATH } from "../utils/constants";

const vedtakssiden = (id: string) => `/v2/skjema/${id}/vedtak`;
const sluttrapport = {
  filnavn: `${"Sluttrapport_".repeat(12)}.pdf`,
  opprettet: "2026-11-17T10:00:00Z",
  storrelse: 1234,
};

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
  server.use(
    http.get(
      `${EKSPERTBISTAND_API_PATH}/:id/sluttrapport`,
      () => new HttpResponse(null, { status: 204 })
    )
  );
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
    expect(screen.queryByText("Sluttrapporten er sendt inn.")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "50 000 kr satt av til ekspertbistand" })
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "Send inn sluttrapport fra eksperten" })
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

  it.each(["INNVILGET", "INNVILGET_REDUSERT"] as const)(
    "viser sluttrapport som et lukket kort uten dokumenttilgang ved %s",
    async (utfall) => {
      const id = MOCK_VEDTAK_SOKNAD_ID[utfall];
      const rapportKall: string[] = [];
      server.use(
        http.get(EKSPERTBISTAND_SLUTTRAPPORT_PATH(id), () => HttpResponse.json(sluttrapport))
      );
      const lytter = ({ request }: { request: Request }) => {
        if (request.url.includes("/sluttrapport")) rapportKall.push(request.url);
      };
      server.events.on("request:start", lytter);
      try {
        renderSkjema(vedtakssiden(id));

        const kort = await screen.findByRole("region", { name: "Sluttrapport sendt inn" });
        const knapp = within(kort).getByRole("button", { expanded: false });
        expect(within(kort).getByText("17. nov. 2026", { selector: "p" })).toBeVisible();
        expect(
          screen.queryByRole("button", { name: "Send inn sluttrapport fra eksperten" })
        ).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Søk refusjon" })).toBeInTheDocument();
        expect(screen.getByText("Frist 31. juli 2027")).toBeInTheDocument();
        expect(
          screen.getAllByRole("region").map((region) => region.getAttribute("aria-label"))
        ).toEqual([
          "Sluttrapport sendt inn",
          utfall === "INNVILGET"
            ? "Vedtak om godkjent søknad"
            : "Vedtak om godkjent søknad med redusert beløp",
          "Søknad sendt inn",
        ]);

        knapp.focus();
        await userEvent.keyboard("{Enter}");
        expect(knapp).toHaveAttribute("aria-expanded", "true");
        expect(within(kort).getByText(sluttrapport.filnavn)).toBeVisible();
        expect(within(kort).getByText("Vedlegg")).toHaveRole("term");
        expect(within(kort).getByText("Sendt inn til Nav")).toHaveRole("term");
        expect(within(kort).getAllByText("17. nov. 2026")).toHaveLength(2);
        expect(within(kort).queryByRole("link")).not.toBeInTheDocument();
        expect(within(kort).getAllByRole("button")).toEqual([knapp]);
        expect(kort.querySelector("iframe, object, embed, canvas, img")).toBeNull();
        expect(rapportKall).toEqual([
          expect.stringContaining(EKSPERTBISTAND_SLUTTRAPPORT_PATH(id)),
        ]);
        await userEvent.keyboard(" ");
        expect(knapp).toHaveAttribute("aria-expanded", "false");
      } finally {
        server.events.removeListener("request:start", lytter);
      }
    }
  );

  it("venter på sluttrapportstatus før innsendingsknappen vises", async () => {
    let fullfor = () => {};
    const svarKlar = new Promise<void>((resolve) => {
      fullfor = resolve;
    });
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/sluttrapport`, async () => {
        await svarKlar;
        return new HttpResponse(null, { status: 204 });
      })
    );
    renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

    try {
      expect(await screen.findByTitle("Laster status for sluttrapporten")).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "Send inn sluttrapport fra eksperten" })
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("region", { name: "Sluttrapport sendt inn" })
      ).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Søk refusjon" })).toBeInTheDocument();
    } finally {
      fullfor();
    }

    expect(
      await screen.findByRole("button", { name: "Send inn sluttrapport fra eksperten" })
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByTitle("Laster status for sluttrapporten")).not.toBeInTheDocument()
    );
  });

  it.each([403, 404, 500])("viser statusfeil %s uten å tilby ny innsending", async (status) => {
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/sluttrapport`, () =>
        HttpResponse.json({ message: "Kunne ikke hente status" }, { status })
      )
    );
    renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Kunne ikke hente status for sluttrapporten. Prøv å laste siden på nytt."
    );
    expect(
      screen.queryByRole("button", { name: "Send inn sluttrapport fra eksperten" })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("region", { name: "Sluttrapport sendt inn" })
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Søk refusjon" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Vedtak om godkjent søknad" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Søknad sendt inn" })).toBeInTheDocument();
  });

  it.each([false, true])(
    "bekrefter innsending på vedtakssiden med metadatafeil=%s",
    async (metadatafeil) => {
      const id = MOCK_VEDTAK_SOKNAD_ID.INNVILGET;
      let sendtInn = false;
      server.use(
        http.get(EKSPERTBISTAND_SLUTTRAPPORT_PATH(id), () => {
          if (!sendtInn) return new HttpResponse(null, { status: 204 });
          return metadatafeil
            ? HttpResponse.json({ message: "Metadataoppslaget feilet" }, { status: 500 })
            : HttpResponse.json(sluttrapport);
        }),
        http.post(EKSPERTBISTAND_SLUTTRAPPORT_PATH(id), () => {
          sendtInn = true;
          return new HttpResponse(null, { status: 201 });
        })
      );
      const { container } = renderSkjema(`/v2/skjema/${id}/sluttrapport`);
      await screen.findByRole("heading", { name: "Send inn sluttrapport" });
      const filvelger = container.querySelector<HTMLInputElement>('input[type="file"]');
      if (!filvelger) throw new Error("Fant ikke filvelgeren for sluttrapporten");
      const user = userEvent.setup();
      await user.upload(
        filvelger,
        new File(["%PDF-1.4\n%%EOF\n"], sluttrapport.filnavn, { type: "application/pdf" })
      );
      await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

      expect(await screen.findByRole("heading", { name: "Søknad godkjent" })).toBeInTheDocument();
      expect(screen.getByRole("status")).toHaveTextContent("Sluttrapporten er sendt inn.");
      if (metadatafeil) {
        expect(await screen.findByRole("alert")).toHaveTextContent(
          "Sluttrapporten er sendt inn, men opplysningene kunne ikke hentes. Prøv å laste siden på nytt."
        );
        expect(
          screen.queryByRole("region", { name: "Sluttrapport sendt inn" })
        ).not.toBeInTheDocument();
      } else {
        expect(
          await screen.findByRole("region", { name: "Sluttrapport sendt inn" })
        ).toBeInTheDocument();
      }
      expect(
        screen.queryByRole("button", { name: "Send inn sluttrapport fra eksperten" })
      ).not.toBeInTheDocument();
    }
  );

  it("viser verken beløp, vilkår, knapper eller frist ved avslag", async () => {
    const statusKall = vi.fn(() => HttpResponse.json(sluttrapport));
    server.use(http.get(`${EKSPERTBISTAND_API_PATH}/:id/sluttrapport`, statusKall));
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
    expect(
      screen.queryByRole("region", { name: "Sluttrapport sendt inn" })
    ).not.toBeInTheDocument();
    expect(statusKall).not.toHaveBeenCalled();
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
