import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router";
import { SWRConfig } from "swr";
import App from "./App";
import HealthPage from "./pages/HealthPage";
import { fetchJson } from "./utils/api";

const mockIsFeatureEnabled = vi.hoisted(() => vi.fn());

vi.mock("./utils/featureFlags", () => ({
  isFeatureEnabled: mockIsFeatureEnabled,
}));

const renderApp = (path: string) =>
  render(
    <SWRConfig value={{ fetcher: fetchJson, provider: () => new Map(), revalidateOnFocus: false }}>
      <MemoryRouter initialEntries={[path]}>
        <CurrentPath />
        <App />
      </MemoryRouter>
    </SWRConfig>
  );

function CurrentPath() {
  const { pathname } = useLocation();
  return <output data-testid="current-path">{pathname}</output>;
}

describe("App routes", () => {
  beforeEach(() => {
    mockIsFeatureEnabled.mockReturnValue(false);
  });

  it("shows health", async () => {
    render(
      <SWRConfig value={{ fetcher: fetchJson }}>
        <HealthPage />
      </SWRConfig>
    );
    expect(await screen.findByText(/Health: ok/i)).toBeInTheDocument();
  });

  it("keeps the v1 overview entry and hides the v2 entry when the flag is off", async () => {
    renderApp("/soknader");

    expect(await screen.findByRole("heading", { name: "Søknader" })).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Opprett søknad om tilskudd til ekspertbistand" })
    ).toHaveAttribute("href", "/skjema/start");
    expect(screen.queryByText("Prøv ny løsning (v2)")).not.toBeInTheDocument();
  });

  it("opens the v2 overview from an accessible v1 overview link when its flag is on", async () => {
    mockIsFeatureEnabled.mockImplementation((flag) => flag === "EKSPERTBISTAND_V2");
    const user = userEvent.setup();
    renderApp("/soknader");

    const entry = await screen.findByRole("link", { name: "Prøv ny løsning (v2)" });
    expect(entry).toHaveAttribute("href", "/v2/soknader");

    await user.click(entry);

    await waitFor(() => {
      expect(screen.getByTestId("current-path")).toHaveTextContent(/^\/v2\/soknader$/);
    });
    expect(
      screen.getByRole("link", { name: "Opprett søknad om tilskudd til ekspertbistand" })
    ).toHaveAttribute("href", "/v2/skjema/start");
  });

  it("redirects /v2 to its overview when enabled", async () => {
    mockIsFeatureEnabled.mockImplementation((flag) => flag === "EKSPERTBISTAND_V2");

    renderApp("/v2");

    await waitFor(() => {
      expect(screen.getByTestId("current-path")).toHaveTextContent(/^\/v2\/soknader$/);
    });
    expect(
      screen.getByRole("link", { name: "Opprett søknad om tilskudd til ekspertbistand" })
    ).toHaveAttribute("href", "/v2/skjema/start");
  });

  it.each([
    "/v2",
    "/v2/soknader",
    "/v2/skjema",
    "/v2/skjema/start",
    "/v2/skjema/test-id",
    "/v2/skjema/test-id/steg-1",
    "/v2/skjema/test-id/steg-2",
    "/v2/skjema/test-id/oppsummering",
    "/v2/skjema/test-id/kvittering",
    "/v2/tilskuddsbrev/2024-123-1",
    "/v2/skjema/test-id/refusjon",
    "/v2/skjema/test-id/sluttrapport",
  ])("rejects %s when disabled", async (path) => {
    renderApp(path);

    await waitFor(() => {
      expect(screen.getByTestId("current-path")).toHaveTextContent(/^\/soknader$/);
    });
    expect(screen.queryByRole("button", { name: "Start søknad" })).not.toBeInTheDocument();
  });

  it("keeps the v1 start route available when v2 is disabled", async () => {
    renderApp("/skjema/start");
    expect(
      await screen.findByRole("heading", { name: "Søknad om tilskudd til ekspertbistand" })
    ).toBeInTheDocument();
  });

  it("does not keep the temporary /skjema/v2 route", async () => {
    mockIsFeatureEnabled.mockImplementation((flag) => flag === "EKSPERTBISTAND_V2");

    renderApp("/skjema/v2/start");

    await waitFor(() => {
      expect(screen.getByTestId("current-path")).toHaveTextContent(/^\/soknader$/);
    });
    expect(screen.queryByRole("button", { name: "Start søknad" })).not.toBeInTheDocument();
  });

  it.each([
    ["/v2/skjema/test-id/refusjon", "Søk refusjon"],
    ["/v2/skjema/test-id/sluttrapport", "Send inn sluttrapport"],
  ])("requires the beta flag for %s", async (path, heading) => {
    mockIsFeatureEnabled.mockImplementation(
      (flag) => flag === "EKSPERTBISTAND_V2" || flag === "EKSPERTBISTAND_SAKSBEHANDLING_BETA"
    );
    const app = renderApp(path);

    expect(
      await screen.findByText("Kunne ikke hente søknadsdata. Prøv å laste siden på nytt.")
    ).toBeInTheDocument();
    expect(screen.getByTestId("current-path").textContent).toBe(path);

    app.unmount();
    mockIsFeatureEnabled.mockImplementation((flag) => flag === "EKSPERTBISTAND_V2");
    renderApp(path);

    await waitFor(() => {
      expect(screen.getByTestId("current-path")).toHaveTextContent(/^\/soknader$/);
    });
    expect(screen.queryByRole("heading", { name: heading })).not.toBeInTheDocument();
  });
});
