import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import SoknaderPage from "./SoknaderPage";
import { useSoknader, type SkjemaListItem } from "../hooks/useSoknader";

vi.mock("../hooks/useSoknader.ts", () => ({ useSoknader: vi.fn() }));

const renderPage = (soknader: SkjemaListItem[] = [], error: string | null = null) => {
  vi.mocked(useSoknader).mockReturnValue({
    soknader,
    error,
    loading: false,
    requiresLogin: false,
  });

  return render(
    <MemoryRouter initialEntries={["/v2/soknader"]}>
      <SoknaderPage />
    </MemoryRouter>
  );
};

afterEach(() => vi.clearAllMocks());

describe("SoknaderPage", () => {
  it("shows create link card and empty state when there are no applications", () => {
    renderPage();

    expect(
      screen.getByRole("link", { name: /Tilbake til Min side - arbeidsgiver/i })
    ).toBeInTheDocument();
    expect(screen.getByText(/Du har ingen søknader ennå/i)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Søknader" })).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /Opprett søknad om tilskudd til ekspertbistand/i })
    ).toHaveAttribute("href", "/v2/skjema/start");
    expect(screen.queryByRole("link", { name: /Prøv ny løsning \(v2\)/i })).not.toBeInTheDocument();
  });

  it("links drafts to the form and submitted applications to their receipt", () => {
    renderPage([
      {
        id: "draft-1",
        title: "Utkast",
        description: "Eksempel AS",
        href: "/v2/skjema/draft-1",
        tag: { label: "Utkast", variant: "neutral" },
      },
      {
        id: "submitted-1",
        title: "Innsendt søknad",
        description: "Eksempel AS",
        href: "/v2/skjema/submitted-1/kvittering",
        tag: { label: "Innsendt", variant: "alt1" },
      },
    ]);

    expect(screen.getByRole("link", { name: "Utkast" })).toHaveAttribute(
      "href",
      "/v2/skjema/draft-1"
    );
    expect(screen.getByRole("link", { name: "Innsendt søknad" })).toHaveAttribute(
      "href",
      "/v2/skjema/submitted-1/kvittering"
    );
  });

  it("shows a generic error without a login action for non-authentication failures", () => {
    renderPage([], "Kunne ikke hente søknader akkurat nå.");

    expect(screen.getByText("Kunne ikke hente søknader akkurat nå.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Logg inn" })).not.toBeInTheDocument();
  });
});
