import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useSoknad } from "../hooks/useSoknad";
import { useSluttrapportStatus } from "../hooks/useSluttrapportStatus";
import { EKSPERTBISTAND_SLUTTRAPPORT_PATH } from "../utils/constants";
import SluttrapportPage from "./SluttrapportPage";

vi.mock("../hooks/useSoknad", () => ({ useSoknad: vi.fn() }));
vi.mock("../hooks/useSluttrapportStatus", () => ({ useSluttrapportStatus: vi.fn() }));

function VedtakMottaker() {
  const { state } = useLocation();
  return <pre aria-label="Navigeringstilstand">{JSON.stringify(state)}</pre>;
}

const renderPage = ({
  sluttrapport = null,
  statusError = null,
}: {
  sluttrapport?: ReturnType<typeof useSluttrapportStatus>["sluttrapport"];
  statusError?: Error | null;
} = {}) => {
  const refreshStatus = vi.fn(async () => undefined);
  vi.mocked(useSoknad).mockReturnValue({
    soknad: { virksomhet: { virksomhetsnavn: "Eksempel AS" } },
    isLoading: false,
    error: null,
  });
  vi.mocked(useSluttrapportStatus).mockReturnValue({
    sluttrapport,
    isLoading: false,
    error: statusError,
    mutate: refreshStatus,
  });

  const view = render(
    <MemoryRouter initialEntries={["/v2/skjema/sak-1/sluttrapport"]}>
      <Routes>
        <Route path="/v2/skjema/:id/sluttrapport" element={<SluttrapportPage />} />
        <Route path="/v2/skjema/:id/vedtak" element={<VedtakMottaker />} />
        <Route path="/v2/skjema/:id/kvittering" element={<p>V2-kvittering</p>} />
      </Routes>
    </MemoryRouter>
  );
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
  return { refreshStatus, lastOppFil };
};

afterEach(() => {
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe("SluttrapportPage", () => {
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
