import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, it, vi } from "vitest";
import SokRefusjonPage from "./SokRefusjonPage";

vi.mock("../hooks/useSoknad", () => ({
  useSoknad: () => ({
    soknad: { virksomhet: { virksomhetsnummer: "123456789" } },
    isLoading: false,
    error: null,
  }),
}));

vi.mock("../hooks/useRefusjonStatus", () => ({
  useRefusjonStatus: () => ({
    refusjon: {
      belopKroner: 1000,
      utgifter: "Utgifter til ekspertbistand",
      opprettet: "2026-05-20T12:00:00",
      kontonummer: null,
      vedlegg: [],
    },
    isLoading: false,
    error: null,
  }),
}));

vi.mock("../hooks/useKontonummerFinnes", () => ({
  useKontonummerFinnes: () => ({
    finnes: true,
    isLoading: false,
    error: null,
  }),
}));

describe("SokRefusjonPage", () => {
  it("shows submission date in the card and details without repeating the page title", () => {
    render(
      <MemoryRouter initialEntries={["/skjema/sak-1/refusjon"]}>
        <Routes>
          <Route path="/skjema/:id/refusjon" element={<SokRefusjonPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(
      screen.getByRole("heading", { level: 1, name: "Refusjonskrav sendt inn" })
    ).toBeInTheDocument();
    expect(screen.getByText("Opplysninger om refusjonskravet")).toBeInTheDocument();
    expect(screen.getAllByText("20.05.2026 kl. 12.00")).toHaveLength(2);
    expect(screen.getByText("Sendt inn til Nav")).toBeInTheDocument();
  });
});
