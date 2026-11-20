import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useSoknad } from "../hooks/useSoknad";
import { useSluttrapportStatus } from "../hooks/useSluttrapportStatus";
import { useVedtak } from "../hooks/useVedtak";
import { EKSPERTBISTAND_SLUTTRAPPORT_PATH } from "../utils/constants";
import { HttpError } from "../../utils/http";
import SluttrapportPage from "./SluttrapportPage";

vi.mock("../hooks/useSoknad", () => ({ useSoknad: vi.fn() }));
vi.mock("../hooks/useSluttrapportStatus", () => ({ useSluttrapportStatus: vi.fn() }));
vi.mock("../hooks/useVedtak", () => ({ useVedtak: vi.fn() }));

const vedtak = {
  utfall: "INNVILGET",
  vedtaksdato: "20. oktober 2026",
  deltakerNavn: "Testperson",
  virksomhetsnavn: "Eksempel AS",
  tiltaksperiode: { fra: "1. november 2026", til: "31. januar 2027" },
} satisfies NonNullable<ReturnType<typeof useVedtak>["vedtak"]>;

function VedtakMottaker() {
  const { state } = useLocation();
  return <pre aria-label="Navigeringstilstand">{JSON.stringify(state)}</pre>;
}

const renderPage = ({
  sluttrapport = null,
  statusError = null,
  soknad = {
    virksomhet: { virksomhetsnavn: "Eksempel AS" },
    ansatt: { fnr: "", navn: "Testperson" },
    ekspert: { navn: "Testekspert" },
    behovForBistand: { startdato: "2026-10-01" },
  },
  vedtakStatus = {},
}: {
  sluttrapport?: ReturnType<typeof useSluttrapportStatus>["sluttrapport"];
  statusError?: Error | null;
  soknad?: ReturnType<typeof useSoknad>["soknad"];
  vedtakStatus?: Partial<ReturnType<typeof useVedtak>>;
} = {}) => {
  const refreshStatus = vi.fn(async () => undefined);
  vi.mocked(useSoknad).mockReturnValue({
    soknad,
    isLoading: false,
    error: null,
  });
  vi.mocked(useSluttrapportStatus).mockReturnValue({
    sluttrapport,
    isLoading: false,
    error: statusError,
    mutate: refreshStatus,
  });
  vi.mocked(useVedtak).mockReturnValue({
    vedtak,
    isLoading: false,
    error: undefined,
    ...vedtakStatus,
  });

  const innhold = () => (
    <MemoryRouter initialEntries={["/v2/skjema/sak-1/sluttrapport"]}>
      <Routes>
        <Route path="/v2/skjema/:id/sluttrapport" element={<SluttrapportPage />} />
        <Route path="/v2/skjema/:id/vedtak" element={<VedtakMottaker />} />
        <Route path="/v2/skjema/:id/kvittering" element={<p>V2-kvittering</p>} />
      </Routes>
    </MemoryRouter>
  );
  const view = render(innhold());
  const lastOppFil = async () => {
    const user = userEvent.setup();
    const filvelger = view.container.querySelector<HTMLInputElement>('input[type="file"]');
    if (!filvelger) throw new Error("Fant ikke filvelgeren for sluttrapporten");
    await user.upload(
      filvelger,
      new File(["%PDF-1.4\n%%EOF\n"], "sluttrapport.pdf", { type: "application/pdf" })
    );
    return user;
  };
  return { refreshStatus, lastOppFil, rerenderPage: () => view.rerender(innhold()) };
};

afterEach(() => {
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe("SluttrapportPage", () => {
  it("viser fire synlige rader med vedtakets periode i stedet for søknadens startdato", () => {
    renderPage();

    expect(screen.getAllByRole("term").map((term) => term.textContent)).toEqual([
      "Arbeidsgiver",
      "Ansatt",
      "Ekspert",
      "Tiltaksperiode",
    ]);
    expect(screen.getAllByRole("definition").map((value) => value.textContent)).toEqual([
      "Eksempel AS",
      "Testperson",
      "Testekspert",
      "1. november 2026 – 31. januar 2027",
    ]);
    for (const term of screen.getAllByRole("term")) {
      expect(term.nextElementSibling?.tagName).toBe("DD");
    }
    expect(useVedtak).toHaveBeenCalledWith("sak-1");
    expect(screen.queryByText("Startdato")).not.toBeInTheDocument();
    expect(screen.queryByText("Opplysninger om saken")).not.toBeInTheDocument();
  });

  it("beholder rader med plassholder når navn og vedtaksperiode mangler", () => {
    renderPage({
      soknad: { virksomhet: { virksomhetsnavn: " " }, ekspert: { navn: "" } },
      vedtakStatus: { vedtak: { ...vedtak, tiltaksperiode: null } },
    });

    expect(screen.getAllByRole("term")).toHaveLength(4);
    expect(screen.getAllByRole("definition").map((value) => value.textContent)).toEqual([
      "–",
      "–",
      "–",
      "–",
    ]);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it.each([403, 500])("viser vedtaksfeil %s uten å skjule filopplastingen", (status) => {
    renderPage({
      vedtakStatus: {
        vedtak: undefined,
        error: new HttpError("Vedtaksoppslaget feilet", { status }),
      },
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Kunne ikke hente tiltaksperioden. Prøv å laste siden på nytt."
    );
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent("Kunne ikke hentes");
    expect(screen.getByLabelText("Last opp sluttrapport fra eksperten")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Send inn$/ })).toBeInTheDocument();
  });

  it("viser en egen melding når vedtaket ikke finnes", () => {
    renderPage({ vedtakStatus: { vedtak: null } });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Fant ikke vedtaket for denne søknaden. Prøv å laste siden på nytt."
    );
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent("Kunne ikke hentes");
    expect(screen.getByRole("button", { name: /^Send inn$/ })).toBeInTheDocument();
  });

  it("beholder valgt fil mens vedtaket lastes, hentes og revalideres med feil", async () => {
    const { lastOppFil, rerenderPage } = renderPage({
      vedtakStatus: { vedtak: undefined, isLoading: true },
    });
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent("Laster tiltaksperiode");
    await lastOppFil();

    vi.mocked(useVedtak).mockReturnValue({ vedtak, isLoading: false, error: undefined });
    rerenderPage();
    expect(screen.getByText("sluttrapport.pdf")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Send inn$/ })).toBeEnabled();
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent(
      "1. november 2026 – 31. januar 2027"
    );

    vi.mocked(useVedtak).mockReturnValue({
      vedtak,
      isLoading: false,
      error: new HttpError("Vedtaksoppslaget feilet", { status: 500 }),
    });
    rerenderPage();
    expect(screen.getByText("sluttrapport.pdf")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Send inn$/ })).toBeEnabled();
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent("Kunne ikke hentes");
  });

  it("går direkte til vedtaket med bekreftelse etter innsending og metadataoppslag", async () => {
    const submit = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 201 }));
    const { refreshStatus, lastOppFil } = renderPage();
    const user = await lastOppFil();
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    expect(await screen.findByLabelText("Navigeringstilstand")).toHaveTextContent(
      JSON.stringify({ sluttrapportSendtInn: true })
    );
    expect(refreshStatus).toHaveBeenCalledTimes(1);
    expect(submit).toHaveBeenCalledWith(EKSPERTBISTAND_SLUTTRAPPORT_PATH("sak-1"), {
      method: "POST",
      body: expect.any(FormData),
    });
    expect(screen.queryByText("V2-kvittering")).not.toBeInTheDocument();
  });

  it("sender en eksisterende sluttrapport til vedtakssiden uten bekreftelse", () => {
    renderPage({
      sluttrapport: {
        filnavn: "sluttrapport.pdf",
        opprettet: "2026-05-20T12:00:00",
        storrelse: 10,
      },
    });

    expect(screen.getByLabelText("Navigeringstilstand")).toHaveTextContent("null");
    expect(screen.queryByRole("button", { name: /^Send inn$/ })).not.toBeInTheDocument();
  });

  it("viser statusfeil i stedet for et tomt skjema", () => {
    renderPage({ statusError: new Error("Statusoppslaget feilet") });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Kunne ikke hente status for sluttrapporten. Prøv å laste siden på nytt."
    );
    expect(screen.queryByRole("button", { name: /^Send inn$/ })).not.toBeInTheDocument();
  });

  it("beholder filen og viser innsendingsfeil når POST feiler", async () => {
    const submit = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 500 }));
    const { refreshStatus, lastOppFil } = renderPage();
    const user = await lastOppFil();
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Feil ved innsending (500)");
    expect(screen.getByRole("heading", { name: "Send inn sluttrapport" })).toBeInTheDocument();
    expect(screen.getByText("sluttrapport.pdf")).toBeInTheDocument();
    expect(refreshStatus).not.toHaveBeenCalled();
    expect(screen.queryByLabelText("Navigeringstilstand")).not.toBeInTheDocument();

    submit.mockResolvedValue(new Response(null, { status: 201 }));
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));
    expect(await screen.findByLabelText("Navigeringstilstand")).toBeInTheDocument();
    expect(submit).toHaveBeenCalledTimes(2);
  });

  it("viser feilmeldingen fra backend når filen avvises", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("Virus funnet i filen 'sluttrapport.pdf'", { status: 422 })
    );
    const { lastOppFil } = renderPage();
    const user = await lastOppFil();
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Virus funnet i filen 'sluttrapport.pdf'"
    );
    expect(screen.queryByLabelText("Navigeringstilstand")).not.toBeInTheDocument();
  });

  it("venter på bekreftet mottak fra backend før navigering og metadataoppslag", async () => {
    let bekreftMottak = (_response: Response) => {};
    const mottak = new Promise<Response>((resolve) => {
      bekreftMottak = resolve;
    });
    vi.spyOn(globalThis, "fetch").mockReturnValue(mottak);
    const { refreshStatus, lastOppFil } = renderPage();
    const user = await lastOppFil();

    try {
      await user.click(screen.getByRole("button", { name: /^Send inn$/ }));
      expect(screen.getByRole("heading", { name: "Send inn sluttrapport" })).toBeInTheDocument();
      expect(refreshStatus).not.toHaveBeenCalled();
      expect(screen.queryByLabelText("Navigeringstilstand")).not.toBeInTheDocument();
    } finally {
      bekreftMottak(new Response(null, { status: 201 }));
    }

    await waitFor(() => expect(refreshStatus).toHaveBeenCalledTimes(1));
    expect(await screen.findByLabelText("Navigeringstilstand")).toBeInTheDocument();
  });
});
