import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import SokRefusjonPage from "./SokRefusjonPage";
import { useSoknad } from "../hooks/useSoknad";
import { useRefusjonStatus, type RefusjonStatus } from "../hooks/useRefusjonStatus";
import { useMaksBelop } from "../hooks/useMaksBelop";
import { useKontonummerFinnes } from "../hooks/useKontonummerFinnes";
import { EKSPERTBISTAND_REFUSJON_PATH } from "../utils/constants";
import { HttpError } from "../../utils/http";

vi.mock("../hooks/useSoknad", () => ({ useSoknad: vi.fn() }));
vi.mock("../hooks/useRefusjonStatus", () => ({ useRefusjonStatus: vi.fn() }));
vi.mock("../hooks/useMaksBelop", () => ({ useMaksBelop: vi.fn() }));
vi.mock("../hooks/useKontonummerFinnes", () => ({ useKontonummerFinnes: vi.fn() }));

function VedtakMottaker() {
  const { state } = useLocation();
  return (
    <>
      <p>V2-vedtak</p>
      <pre aria-label="Navigeringstilstand">{JSON.stringify(state)}</pre>
    </>
  );
}

const renderPage = (
  refusjon: RefusjonStatus | null,
  {
    maksBelopError = null,
    statusError = null,
    statusLoading = false,
  }: {
    maksBelopError?: Error | null;
    statusError?: Error | null;
    statusLoading?: boolean;
  } = {}
) => {
  const refreshStatus = vi.fn(async () => undefined);
  vi.mocked(useSoknad).mockReturnValue({
    soknad: { virksomhet: { virksomhetsnummer: "123456789", virksomhetsnavn: "Eksempel AS" } },
    isLoading: false,
    error: null,
  });
  vi.mocked(useRefusjonStatus).mockReturnValue({
    refusjon,
    isLoading: statusLoading,
    error: statusError,
    mutate: refreshStatus,
  });
  vi.mocked(useMaksBelop).mockReturnValue({
    maksBelopKroner: maksBelopError ? null : 24_900,
    isLoading: false,
    error: maksBelopError,
    mutate: vi.fn(),
  });
  vi.mocked(useKontonummerFinnes).mockReturnValue({
    finnes: true,
    isLoading: false,
    error: null,
  });

  const view = render(
    <MemoryRouter initialEntries={["/v2/skjema/sak-1/refusjon"]}>
      <Routes>
        <Route path="/v2/skjema/:id/refusjon" element={<SokRefusjonPage />} />
        <Route path="/v2/skjema/:id/vedtak" element={<VedtakMottaker />} />
        <Route path="/v2/skjema/:id/kvittering" element={<p>V2-kvittering</p>} />
      </Routes>
    </MemoryRouter>
  );
  return { ...view, refreshStatus };
};

const fyllUtKrav = async (container: HTMLElement) => {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Beløp for refusjonskravet"), "1000");
  const filvelger = container.querySelector<HTMLInputElement>('input[type="file"]');
  if (!filvelger) throw new Error("Fant ikke filvelgeren for refusjonskravet");
  await user.upload(
    filvelger,
    new File(["%PDF-1.4\n%%EOF\n"], "kvittering.pdf", { type: "application/pdf" })
  );
  await user.click(
    screen.getByRole("checkbox", {
      name: "Jeg bekrefter at utgiftene som kreves refundert er betalt.",
    })
  );
  return user;
};

afterEach(() => {
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe("SokRefusjonPage", () => {
  it("sender eksisterende krav til vedtakssiden uten ny innsendingsbekreftelse", () => {
    renderPage({
      belopKroner: 1000,
      opprettet: "2026-05-20T12:00:00",
      kontonummer: null,
      vedlegg: [],
    });

    expect(screen.getByText("V2-vedtak")).toBeInTheDocument();
    expect(screen.getByLabelText("Navigeringstilstand")).toHaveTextContent("null");
    expect(screen.queryByText("V2-kvittering")).not.toBeInTheDocument();
  });

  it("returns an unsubmitted refund form to the v2 vedtak page", () => {
    renderPage(null);

    expect(screen.getByRole("heading", { level: 1, name: "Søk refusjon" })).toBeInTheDocument();
    expect(
      screen.queryByLabelText("Hvilke utgifter skal tilskuddet til ekspertbistand dekke?")
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Tilbake til avtalen/ })).toHaveAttribute(
      "href",
      "/v2/skjema/sak-1/vedtak"
    );
    expect(
      screen.getByText("Du kan kreve inntil 24 900 kr. Oppgi beløp i hele kroner, f.eks. 12500.")
    ).toBeInTheDocument();
  });

  it("viser feltfeil for beløp over innvilget beløp", async () => {
    const user = userEvent.setup();
    const submit = vi.spyOn(globalThis, "fetch");
    renderPage(null);

    await user.type(screen.getByLabelText("Beløp for refusjonskravet"), "24901");
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    expect(
      await screen.findAllByText("Beløpet kan ikke være høyere enn innvilget beløp (24 900 kr).")
    ).toHaveLength(2);
    expect(submit).not.toHaveBeenCalled();
  });

  it("viser melding i stedet for skjemaet når maksbeløpet ikke kan hentes", () => {
    renderPage(null, {
      maksBelopError: new HttpError("Refusjonskrav for denne saken sendes ikke inn her", {
        status: 409,
      }),
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Refusjonskrav for denne saken sendes ikke inn her"
    );
    expect(screen.queryByRole("button", { name: /^Send inn$/ })).not.toBeInTheDocument();
  });

  it("går direkte til vedtaket etter innsending og metadataoppslag", async () => {
    const user = userEvent.setup();
    const submit = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 201 }));
    const { container, refreshStatus } = renderPage(null);

    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));
    expect(await screen.findAllByText("Du må oppgi beløp for refusjonskravet.")).toHaveLength(2);
    await user.type(screen.getByLabelText("Beløp for refusjonskravet"), "1000");
    const fileInput = container.querySelector<HTMLInputElement>('input[type="file"]');
    if (!fileInput) throw new Error("Missing refund attachment input.");
    const file = new File(["%PDF-1.4\n%%EOF\n"], "kvittering.pdf", { type: "application/pdf" });
    await user.upload(fileInput, file);
    await user.click(
      screen.getByRole("checkbox", {
        name: "Jeg bekrefter at utgiftene som kreves refundert er betalt.",
      })
    );
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    await waitFor(() =>
      expect(submit).toHaveBeenCalledWith(EKSPERTBISTAND_REFUSJON_PATH("sak-1"), {
        method: "POST",
        body: expect.any(FormData),
      })
    );
    const formData = submit.mock.calls[0][1]?.body;
    expect(formData).toBeInstanceOf(FormData);
    if (!(formData instanceof FormData)) throw new Error("Missing refund form data.");
    expect(Array.from(formData.keys())).toEqual(["belop", "filer"]);
    expect(formData.has("utgifter")).toBe(false);
    expect(formData.get("belop")).toBe("1000");
    expect(formData.getAll("filer")).toEqual([file]);
    expect(await screen.findByText("V2-vedtak")).toBeInTheDocument();
    expect(screen.getByLabelText("Navigeringstilstand")).toHaveTextContent(
      JSON.stringify({ refusjonSendtInn: true })
    );
    expect(screen.queryByText("V2-kvittering")).not.toBeInTheDocument();
    expect(refreshStatus).toHaveBeenCalledTimes(1);
  });

  it("beholder beløp, filer og bekreftelse når innsendingen feiler", async () => {
    const submit = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        new Response(JSON.stringify({ message: "Kunne ikke ta imot kravet" }), { status: 500 })
      );
    const { container, refreshStatus } = renderPage(null);
    const user = await fyllUtKrav(container);
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Kunne ikke ta imot kravet");
    expect(screen.getByLabelText("Beløp for refusjonskravet")).toHaveValue("1000");
    expect(screen.getByText("kvittering.pdf")).toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", {
        name: "Jeg bekrefter at utgiftene som kreves refundert er betalt.",
      })
    ).toBeChecked();
    expect(refreshStatus).not.toHaveBeenCalled();
    expect(screen.queryByText("V2-vedtak")).not.toBeInTheDocument();

    submit.mockResolvedValue(new Response(null, { status: 201 }));
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));
    expect(await screen.findByText("V2-vedtak")).toBeInTheDocument();
    expect(submit).toHaveBeenCalledTimes(2);
  });

  it("venter på bekreftet mottak fra backend før navigering", async () => {
    let bekreftMottak = (_response: Response) => {};
    const mottak = new Promise<Response>((resolve) => {
      bekreftMottak = resolve;
    });
    vi.spyOn(globalThis, "fetch").mockReturnValue(mottak);
    const { container, refreshStatus } = renderPage(null);
    const user = await fyllUtKrav(container);

    try {
      await user.click(screen.getByRole("button", { name: /^Send inn$/ }));
      expect(screen.getByRole("heading", { name: "Søk refusjon" })).toBeInTheDocument();
      expect(refreshStatus).not.toHaveBeenCalled();
      expect(screen.queryByText("V2-vedtak")).not.toBeInTheDocument();
    } finally {
      bekreftMottak(new Response(null, { status: 201 }));
    }

    expect(await screen.findByText("V2-vedtak")).toBeInTheDocument();
    expect(refreshStatus).toHaveBeenCalledTimes(1);
  });

  it("viser ikke skjemaet mens refusjonsstatus lastes", () => {
    renderPage(null, { statusLoading: true });

    expect(screen.getByTitle("Laster søknad")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Send inn$/ })).not.toBeInTheDocument();
  });

  it.each([403, 404, 500])("viser statusfeil %s i stedet for et nytt skjema", (status) => {
    renderPage(null, { statusError: new HttpError("Statusoppslaget feilet", { status }) });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Kunne ikke hente status for refusjonskravet. Prøv å laste siden på nytt."
    );
    expect(screen.queryByRole("button", { name: /^Send inn$/ })).not.toBeInTheDocument();
    expect(screen.queryByText("V2-vedtak")).not.toBeInTheDocument();
  });

  it("keeps amount, paid-expense confirmation and attachment required", async () => {
    const user = userEvent.setup();
    const submit = vi.spyOn(globalThis, "fetch");
    renderPage(null);

    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    expect(await screen.findAllByText("Du må oppgi beløp for refusjonskravet.")).toHaveLength(2);
    expect(screen.getAllByText("Du må bekrefte at utgiftene er betalt.")).toHaveLength(2);
    expect(screen.getAllByText("Du må laste opp minst én fil.")).toHaveLength(2);
    expect(submit).not.toHaveBeenCalled();
  });
});
