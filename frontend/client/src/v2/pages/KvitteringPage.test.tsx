import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { MOCK_VEDTAK_SOKNAD_ID } from "../../mocks/handlers";
import { server } from "../../test/server";
import { renderSkjema } from "../../test/skjemaV2";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";

const kvitteringen = (id: string) => `/v2/skjema/${id}/kvittering`;

describe("KvitteringPage", () => {
  it("går til vedtakssiden når en godkjent søknad har vedtak", async () => {
    renderSkjema(kvitteringen(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

    expect(
      await screen.findByRole("heading", { level: 1, name: "Tilskudd til ekspertbistand" })
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("heading", { level: 2, name: "Søknad godkjent" })
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Søknaden godkjent" })).not.toBeInTheDocument();
  });

  it("viser kvitteringen når en godkjent søknad ikke har vedtak", async () => {
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id/vedtak`, () =>
        HttpResponse.json({ message: "vedtak ikke funnet" }, { status: 404 })
      )
    );
    renderSkjema(kvitteringen(MOCK_VEDTAK_SOKNAD_ID.INNVILGET));

    expect(await screen.findByRole("heading", { name: "Søknaden godkjent" })).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1, name: "Søknad om tilskudd til ekspertbistand" })
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("heading", {
        level: 3,
        name: /Dere har fått innvilget tilskudd til ekspertbistand: 2024-123-1/,
      })
    ).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Søknad sendt inn" })).toBeInTheDocument();
  });

  it("viser innsendt søknad i et lukket kort som kan åpnes", async () => {
    const id = "innsendt-uten-beslutning";
    server.use(
      http.get(`${EKSPERTBISTAND_API_PATH}/:id`, () =>
        HttpResponse.json({
          id,
          status: "innsendt",
          ansatt: { fnr: "12345678910", navn: "Kari Ansatt" },
          opprettetTidspunkt: "2026-10-01T10:00:00.000Z",
          innsendtTidspunkt: "2026-10-01T10:00:00.000Z",
        })
      )
    );
    renderSkjema(kvitteringen(id));

    expect(
      await screen.findByRole("heading", { level: 2, name: "Nav har mottatt søknaden" })
    ).toBeInTheDocument();
    const kort = await screen.findByRole("region", { name: "Søknad sendt inn" });
    expect(
      within(kort).getByRole("heading", { level: 2, name: "Søknad sendt inn" })
    ).toBeInTheDocument();
    expect(within(kort).queryByRole("heading", { name: "Søknad" })).not.toBeInTheDocument();

    await userEvent.click(within(kort).getByRole("button", { expanded: false }));

    expect(
      await within(kort).findByRole("heading", { level: 3, name: "Søknad" })
    ).toBeInTheDocument();
    expect(within(kort).getByText("Kari Ansatt")).toBeInTheDocument();
  });
});
