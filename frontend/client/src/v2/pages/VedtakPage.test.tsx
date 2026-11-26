import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MOCK_VEDTAK_SOKNAD_ID } from "../../mocks/handlers";
import { server } from "../../test/server";
import { renderSkjema } from "../../test/skjemaV2";
import {
  EKSPERTBISTAND_API_PATH,
  EKSPERTBISTAND_REFUSJON_PATH,
  EKSPERTBISTAND_REFUSJON_VEDLEGG_PATH,
  EKSPERTBISTAND_SLUTTRAPPORT_PATH,
} from "../utils/constants";

const vedtakssiden = (id: string) => `/v2/skjema/${id}/vedtak`;
const sluttrapport = {
  filnavn: `${"Sluttrapport_".repeat(12)}.pdf`,
  opprettet: "2026-11-17T10:00:00Z",
  storrelse: 1234,
  status: "MOTTATT",
  vurdertTidspunkt: null,
  begrunnelse: null,
};
const refusjon = {
  belopKroner: 22400,
  opprettet: "2026-11-17T11:00:00Z",
  kontonummer: "skal-ikke-vises",
  vedlegg: [
    {
      id: "00000000-0000-4000-8000-000000000001",
      filnavn: "Faktura.pdf",
      storrelse: 2048,
    },
    {
      id: "00000000-0000-4000-8000-000000000002",
      filnavn: `${"Reiseutgifter_".repeat(12)}.pdf`,
      storrelse: 1536,
    },
  ],
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
    ),
    http.get(
      `${EKSPERTBISTAND_API_PATH}/:id/refusjon`,
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
    expect(screen.queryByText("Refusjonskravet er sendt inn.")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "50 000 kr satt av til ekspertbistand" })
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "Send inn sluttrapport fra eksperten" })
    ).toHaveAttribute("href", `/v2/skjema/${id}/sluttrapport`);
    expect(await screen.findByRole("button", { name: "Søk refusjon" })).toHaveAttribute(
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
        expect(await screen.findByRole("button", { name: "Søk refusjon" })).toBeInTheDocument();
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

  it.each([
    ["INNVILGET", false],
    ["INNVILGET", true],
    ["INNVILGET_REDUSERT", false],
    ["INNVILGET_REDUSERT", true],
  ] as const)(
    "viser refusjonskort med nedlastingslenker ved %s og sluttrapport=%s",
    async (utfall, medSluttrapport) => {
      const id = MOCK_VEDTAK_SOKNAD_ID[utfall];
      const dokumentKall: string[] = [];
      server.use(
        http.get(EKSPERTBISTAND_REFUSJON_PATH(id), () => HttpResponse.json(refusjon)),
        http.get(EKSPERTBISTAND_SLUTTRAPPORT_PATH(id), () =>
          medSluttrapport
            ? HttpResponse.json(sluttrapport)
            : new HttpResponse(null, { status: 204 })
        )
      );
      const lytter = ({ request }: { request: Request }) => {
        if (request.url.includes("/vedlegg/")) dokumentKall.push(request.url);
      };
      server.events.on("request:start", lytter);
      try {
        renderSkjema(vedtakssiden(id));

        const forventetStatus =
          utfall === "INNVILGET" ? "Søknad godkjent" : "Søknad godkjent med redusert beløp";
        await ventPaVedtak(forventetStatus);
        const kort = await screen.findByRole("region", { name: "Refusjonskrav sendt inn" });
        expect(screen.queryByText("Nav behandler refusjonskravet")).not.toBeInTheDocument();
        if (medSluttrapport) {
          await screen.findByRole("region", { name: "Sluttrapport sendt inn" });
        } else {
          expect(
            await screen.findByRole("button", { name: "Send inn sluttrapport fra eksperten" })
          ).toBeInTheDocument();
        }
        const knapp = within(kort).getByRole("button", { expanded: false });
        const belop = utfall === "INNVILGET" ? "50 000" : "40 000";
        const forventetBelop = `${belop} kr satt av til ekspertbistand`;
        expect(screen.getByRole("heading", { name: forventetBelop })).toBeInTheDocument();
        expect(screen.queryByText("Vilkår og informasjon om utbetaling")).not.toBeInTheDocument();
        expect(screen.queryByText(/^Frist/)).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Søk refusjon" })).not.toBeInTheDocument();
        expect(screen.queryByText(/Saksbehandlingstiden|e-post/)).not.toBeInTheDocument();
        expect(
          screen.getAllByRole("region").map((region) => region.getAttribute("aria-label"))
        ).toEqual([
          "Refusjonskrav sendt inn",
          ...(medSluttrapport ? ["Sluttrapport sendt inn"] : []),
          utfall === "INNVILGET"
            ? "Vedtak om godkjent søknad"
            : "Vedtak om godkjent søknad med redusert beløp",
          "Søknad sendt inn",
        ]);
        for (const region of screen.getAllByRole("region")) {
          expect(within(region).getByRole("button", { expanded: false })).toBeInTheDocument();
        }
        expect(dokumentKall).toHaveLength(0);

        knapp.focus();
        await userEvent.keyboard("{Enter}");
        expect(knapp).toHaveAttribute("aria-expanded", "true");
        expect(within(kort).getByText("Beløp for refusjonskravet")).toHaveRole("term");
        expect(within(kort).getByText("22 400 kr")).toBeVisible();
        expect(within(kort).getByText("2 kB")).toBeVisible();
        expect(within(kort).getByText("1,5 kB")).toBeVisible();
        expect(within(kort).getAllByText("17. nov. 2026")).toHaveLength(2);
        for (const vedlegg of refusjon.vedlegg) {
          const lenke = within(kort).getByRole("link", { name: vedlegg.filnavn });
          expect(lenke).toHaveAttribute(
            "href",
            EKSPERTBISTAND_REFUSJON_VEDLEGG_PATH(id, vedlegg.id)
          );
          expect(lenke).toHaveAttribute("download", vedlegg.filnavn);
        }
        expect(within(kort).queryByText("Kontonummer")).not.toBeInTheDocument();
        expect(screen.queryByText(refusjon.kontonummer)).not.toBeInTheDocument();
        expect(dokumentKall).toHaveLength(0);
        for (const region of screen.getAllByRole("region").slice(1)) {
          expect(within(region).getByRole("button", { expanded: false })).toBeInTheDocument();
        }
        if (medSluttrapport) {
          const rapportKort = screen.getByRole("region", { name: "Sluttrapport sendt inn" });
          expect(within(rapportKort).queryByRole("link")).not.toBeInTheDocument();
        }

        await userEvent.keyboard(" ");
        expect(knapp).toHaveAttribute("aria-expanded", "false");
        expect(screen.getByRole("heading", { name: forventetBelop })).toBeInTheDocument();
        expect(screen.getByRole("heading", { name: forventetStatus })).toBeInTheDocument();
        expect(screen.queryByText("Nav behandler refusjonskravet")).not.toBeInTheDocument();
      } finally {
        server.events.removeListener("request:start", lytter);
      }
    }
  );

  it("viser plassholdere når refusjonen mangler dato og vedlegg", async () => {
    const id = MOCK_VEDTAK_SOKNAD_ID.INNVILGET;
    server.use(
      http.get(EKSPERTBISTAND_REFUSJON_PATH(id), () =>
        HttpResponse.json({ ...refusjon, opprettet: "", vedlegg: [] })
      )
    );
    renderSkjema(vedtakssiden(id));

    const kort = await screen.findByRole("region", { name: "Refusjonskrav sendt inn" });
    await userEvent.click(within(kort).getByRole("button", { expanded: false }));
    expect(within(kort).getAllByText("—")).toHaveLength(2);
    expect(within(kort).getByText("–")).toBeVisible();
    expect(within(kort).queryByRole("link")).not.toBeInTheDocument();
  });

  it("venter på refusjonsstatus før refusjonsknappen vises", async () => {
    let fullfor = () => {};
    const svarKlar = new Promise<void>((resolve) => {
      fullfor = resolve;
    });
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/refusjon`, async () => {
        await svarKlar;
        return new HttpResponse(null, { status: 204 });
      })
    );
    renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

    try {
      expect(await screen.findByTitle("Laster status for refusjonskravet")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "Søk refusjon" })).not.toBeInTheDocument();
      expect(
        await screen.findByRole("button", { name: "Send inn sluttrapport fra eksperten" })
      ).toBeInTheDocument();
    } finally {
      fullfor();
    }

    expect(await screen.findByRole("button", { name: "Søk refusjon" })).toBeInTheDocument();
    expect(screen.getByText("Frist 31. juli 2027")).toBeInTheDocument();
  });

  it.each([403, 404, 500])(
    "viser refusjonsstatusfeil %s uten å tilby ny innsending",
    async (status) => {
      server.use(
        http.get(`${EKSPERTBISTAND_API_PATH}/:id/refusjon`, () =>
          HttpResponse.json({ message: "Kunne ikke hente status" }, { status })
        ),
        http.get(`${EKSPERTBISTAND_API_PATH}/:id/sluttrapport`, () =>
          HttpResponse.json(sluttrapport)
        )
      );
      renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Kunne ikke hente status for refusjonskravet. Prøv å laste siden på nytt."
      );
      expect(screen.queryByRole("button", { name: "Søk refusjon" })).not.toBeInTheDocument();
      expect(
        screen.queryByRole("region", { name: "Refusjonskrav sendt inn" })
      ).not.toBeInTheDocument();
      expect(
        await screen.findByRole("region", { name: "Sluttrapport sendt inn" })
      ).toBeInTheDocument();
      expect(screen.getByRole("region", { name: "Vedtak om godkjent søknad" })).toBeInTheDocument();
      expect(screen.getByRole("region", { name: "Søknad sendt inn" })).toBeInTheDocument();
    }
  );

  it.each([false, true])(
    "bekrefter refusjonsinnsending på vedtakssiden med metadatafeil=%s",
    async (metadatafeil) => {
      const id = MOCK_VEDTAK_SOKNAD_ID.INNVILGET;
      let sendtInn = false;
      server.use(
        http.get(EKSPERTBISTAND_REFUSJON_PATH(id), () => {
          if (!sendtInn) return new HttpResponse(null, { status: 204 });
          return metadatafeil
            ? HttpResponse.json({ message: "Metadataoppslaget feilet" }, { status: 500 })
            : HttpResponse.json(refusjon);
        }),
        http.post(EKSPERTBISTAND_REFUSJON_PATH(id), () => {
          sendtInn = true;
          return new HttpResponse(null, { status: 201 });
        })
      );
      const { container } = renderSkjema(`/v2/skjema/${id}/refusjon`);
      await screen.findByRole("heading", { name: "Søk refusjon" });
      const user = userEvent.setup();
      await user.type(screen.getByLabelText("Beløp for refusjonskravet"), "22400");
      const filvelger = container.querySelector<HTMLInputElement>('input[type="file"]');
      if (!filvelger) throw new Error("Fant ikke filvelgeren for refusjonskravet");
      await user.upload(
        filvelger,
        new File(["%PDF-1.4\n%%EOF\n"], "Faktura.pdf", { type: "application/pdf" })
      );
      await user.click(
        screen.getByRole("checkbox", {
          name: "Jeg bekrefter at utgiftene som kreves refundert er betalt.",
        })
      );
      await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

      await ventPaVedtak("Søknad godkjent");
      expect(screen.getByRole("status")).toHaveTextContent("Refusjonskravet er sendt inn.");
      expect(screen.queryByText("Nav behandler refusjonskravet")).not.toBeInTheDocument();
      expect(screen.queryByText("Sluttrapporten er sendt inn.")).not.toBeInTheDocument();
      if (metadatafeil) {
        expect(await screen.findByRole("alert")).toHaveTextContent(
          "Refusjonskravet er sendt inn, men opplysningene kunne ikke hentes. Prøv å laste siden på nytt."
        );
        expect(
          screen.queryByRole("region", { name: "Refusjonskrav sendt inn" })
        ).not.toBeInTheDocument();
      } else {
        expect(
          await screen.findByRole("region", { name: "Refusjonskrav sendt inn" })
        ).toBeInTheDocument();
      }
      expect(screen.queryByRole("button", { name: "Søk refusjon" })).not.toBeInTheDocument();
      expect(screen.queryByText(/^Frist/)).not.toBeInTheDocument();
    }
  );

  it("viser metadatafeil for refusjon også når sluttrapporten finnes", async () => {
    const id = MOCK_VEDTAK_SOKNAD_ID.INNVILGET;
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/sluttrapport`, () =>
        HttpResponse.json({
          filnavn: "sluttrapport.pdf",
          opprettet: "2026-11-01T10:00:00",
          storrelse: 2048,
        })
      ),
      http.get(EKSPERTBISTAND_REFUSJON_PATH(id), () => new HttpResponse(null, { status: 204 })),
      http.post(EKSPERTBISTAND_REFUSJON_PATH(id), () => new HttpResponse(null, { status: 201 }))
    );
    const { container } = renderSkjema(`/v2/skjema/${id}/refusjon`);
    await screen.findByRole("heading", { name: "Søk refusjon" });
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Beløp for refusjonskravet"), "22400");
    const filvelger = container.querySelector<HTMLInputElement>('input[type="file"]');
    if (!filvelger) throw new Error("Fant ikke filvelgeren for refusjonskravet");
    await user.upload(
      filvelger,
      new File(["%PDF-1.4\n%%EOF\n"], "Faktura.pdf", { type: "application/pdf" })
    );
    await user.click(
      screen.getByRole("checkbox", {
        name: "Jeg bekrefter at utgiftene som kreves refundert er betalt.",
      })
    );
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    await ventPaVedtak("Søknad godkjent");
    expect(
      await screen.findByRole("region", { name: "Sluttrapport sendt inn" })
    ).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Refusjonskravet er sendt inn, men opplysningene kunne ikke hentes. Prøv å laste siden på nytt."
    );
    expect(screen.queryByRole("button", { name: "Søk refusjon" })).not.toBeInTheDocument();
  });

  it("sender eksisterende refusjonskrav fra skjemaet til vedtakssiden", async () => {
    const id = MOCK_VEDTAK_SOKNAD_ID.INNVILGET;
    server.use(http.get(EKSPERTBISTAND_REFUSJON_PATH(id), () => HttpResponse.json(refusjon)));
    renderSkjema(`/v2/skjema/${id}/refusjon`);

    await ventPaVedtak("Søknad godkjent");
    expect(
      await screen.findByRole("region", { name: "Refusjonskrav sendt inn" })
    ).toBeInTheDocument();
    expect(screen.queryByText("Nav behandler refusjonskravet")).not.toBeInTheDocument();
    expect(screen.queryByText("Refusjonskravet er sendt inn.")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Send inn$/ })).not.toBeInTheDocument();
  });

  it("viser avvist sluttrapport med Navs begrunnelse og lar arbeidsgiver sende inn på nytt", async () => {
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/sluttrapport`, () =>
        HttpResponse.json({
          ...sluttrapport,
          status: "NY_INNSENDING_KREVES",
          vurdertTidspunkt: "2026-11-20T10:00:00Z",
          begrunnelse: "Send sluttrapporten på nytt.",
        })
      )
    );
    renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

    const knapp = await screen.findByRole("button", {
      name: "Send inn sluttrapport fra eksperten",
    });
    expect(knapp).toHaveAttribute(
      "href",
      `/v2/skjema/${MOCK_VEDTAK_SOKNAD_ID.INNVILGET}/sluttrapport`
    );
    const kort = screen.getByRole("region", { name: "Sluttrapport avvist" });
    expect(within(kort).getByRole("heading", { name: "Sluttrapport avvist" })).toBeInTheDocument();
    expect(within(kort).getByText("20. nov. 2026")).toBeInTheDocument();
    expect(within(kort).getByText("Send sluttrapporten på nytt.")).toBeVisible();
    expect(within(kort).getByText(sluttrapport.filnavn)).toBeVisible();
  });

  it("viser at Nav har godkjent sluttrapporten", async () => {
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/sluttrapport`, () =>
        HttpResponse.json({ ...sluttrapport, status: "BEKREFTET" })
      )
    );
    renderSkjema(vedtakssiden(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

    expect(await screen.findByText("Godkjent av Nav")).toBeInTheDocument();
    expect(screen.queryByText("Sluttrapport avvist")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Send inn.*sluttrapport/ })
    ).not.toBeInTheDocument();
  });

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
    const refusjonKall = vi.fn(() => HttpResponse.json(refusjon));
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/sluttrapport`, statusKall),
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/refusjon`, refusjonKall)
    );
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
    expect(refusjonKall).not.toHaveBeenCalled();
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
