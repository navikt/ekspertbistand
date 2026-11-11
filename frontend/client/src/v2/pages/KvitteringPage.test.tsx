import { screen } from "@testing-library/react";
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
  });
});
