import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import SokRefusjonPage from "./SokRefusjonPage";
import { useSoknad } from "../hooks/useSoknad";
import { useRefusjonStatus, type RefusjonStatus } from "../hooks/useRefusjonStatus";
import { useKontonummerFinnes } from "../hooks/useKontonummerFinnes";
import { EKSPERTBISTAND_REFUSJON_PATH } from "../utils/constants";

vi.mock("../hooks/useSoknad", () => ({ useSoknad: vi.fn() }));
vi.mock("../hooks/useRefusjonStatus", () => ({ useRefusjonStatus: vi.fn() }));
vi.mock("../hooks/useKontonummerFinnes", () => ({ useKontonummerFinnes: vi.fn() }));

const renderPage = (refusjon: RefusjonStatus | null) => {
  const refreshStatus = vi.fn(async () => undefined);
  vi.mocked(useSoknad).mockReturnValue({
    soknad: { virksomhet: { virksomhetsnummer: "123456789", virksomhetsnavn: "Eksempel AS" } },
    isLoading: false,
    error: null,
  });
  vi.mocked(useRefusjonStatus).mockReturnValue({
    refusjon,
    isLoading: false,
    error: null,
    mutate: refreshStatus,
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
        <Route path="/v2/skjema/:id/kvittering" element={<p>V2-kvittering</p>} />
      </Routes>
    </MemoryRouter>
  );
  return { ...view, refreshStatus };
};

afterEach(() => {
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe("SokRefusjonPage", () => {
  it("shows submission date in the card and details without repeating the page title", () => {
    renderPage({
      belopKroner: 1000,
      opprettet: "2026-05-20T12:00:00",
      kontonummer: null,
      vedlegg: [],
    });

    expect(
      screen.getByRole("heading", { level: 1, name: "Refusjonskrav sendt inn" })
    ).toBeInTheDocument();
    expect(screen.getByText("Opplysninger om refusjonskravet")).toBeInTheDocument();
    expect(screen.queryByText("Hvilke utgifter skal tilskuddet dekke")).not.toBeInTheDocument();
    expect(screen.getByText("1 000 kr")).toBeInTheDocument();
    expect(screen.getAllByText("20.05.2026 kl. 12.00")).toHaveLength(2);
    expect(screen.getByText("Sendt inn til Nav")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Tilbake til avtalen/ })).toHaveAttribute(
      "href",
      "/v2/skjema/sak-1/kvittering"
    );
  });

  it("returns an unsubmitted refund form to the v2 receipt", () => {
    renderPage(null);

    expect(screen.getByRole("heading", { level: 1, name: "Søk refusjon" })).toBeInTheDocument();
    expect(
      screen.queryByLabelText("Hvilke utgifter skal tilskuddet til ekspertbistand dekke?")
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Tilbake til avtalen/ })).toHaveAttribute(
      "href",
      "/v2/skjema/sak-1/kvittering"
    );
  });

  it("refreshes refund status on successful submission", async () => {
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
    expect(await screen.findByText("V2-kvittering")).toBeInTheDocument();
    expect(refreshStatus).toHaveBeenCalledTimes(1);
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
