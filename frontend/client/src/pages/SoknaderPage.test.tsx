import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createElement, Fragment, type ReactNode } from "react";

vi.mock("react-router-dom", () => ({
  MemoryRouter: ({ children }: { children?: ReactNode }) => createElement(Fragment, null, children),
  Link: ({ to, children }: { to?: string; children?: ReactNode }) =>
    createElement("a", { href: to ?? "" }, children),
}));

import { MemoryRouter } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import SoknaderPage from "./SoknaderPage.tsx";

describe("SoknaderPage", () => {
  beforeEach(() => {
    vi.spyOn(globalThis, "fetch").mockImplementation((input: RequestInfo | URL) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : (input.url ?? "");

      if (url.includes("status=")) {
        return Promise.resolve(
          new Response("[]", {
            status: 200,
            headers: { "Content-Type": "application/json" },
          })
        );
      }

      return Promise.resolve(
        new Response("[]", {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows create link card and empty state when there are no applications", async () => {
    render(
      <MemoryRouter>
        <SoknaderPage />
      </MemoryRouter>
    );

    expect(
      screen.getByRole("link", { name: /Tilbake til Min side - arbeidsgiver/i })
    ).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByText(/Du har ingen søknader ennå/i)).toBeInTheDocument()
    );

    expect(screen.getByRole("heading", { name: "Søknader" })).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /Opprett søknad om tilskudd til ekspertbistand/i })
    ).toHaveAttribute("href", "/skjema/start");
  });
});
