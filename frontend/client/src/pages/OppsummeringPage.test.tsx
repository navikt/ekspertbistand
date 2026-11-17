import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "../test/server";
import { opprettKomplettUtkast, renderSkjema } from "../test/skjema";
import { fetchJson } from "../utils/api";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";

beforeAll(() => {
  vi.spyOn(window, "scrollTo").mockImplementation(() => {});
});

type Kall = { metode: string; body: string };
let kall: Kall[] = [];
const lytter = ({ request }: { request: Request }) => {
  if (!["PATCH", "PUT"].includes(request.method)) return;
  const metode = request.method;
  void request
    .clone()
    .text()
    .then((body) => kall.push({ metode, body }));
};

beforeEach(() => {
  kall = [];
  server.events.on("request:start", lytter);
});
afterEach(() => server.events.removeListener("request:start", lytter));

const putKall = () => kall.filter(({ metode }) => metode === "PUT");
const sendSoknad = () => screen.getByRole("button", { name: /Send søknad/ });
const hentStatus = async (id: string) =>
  (await fetchJson<{ status: string }>(`${EKSPERTBISTAND_API_PATH}/${id}`))?.status;

const apneOppsummering = async () => {
  const id = await opprettKomplettUtkast();
  renderSkjema(`/skjema/${id}/oppsummering`);
  await screen.findByText("Dr. Ekspert");
  return id;
};

const endreBehovOgGaTilOppsummering = async (id: string, tekst: string) => {
  renderSkjema(`/skjema/${id}/steg-3`);
  const user = userEvent.setup();
  const behov = await screen.findByRole("textbox", { name: "Hva skal eksperten hjelpe dere med?" });
  await waitFor(() => expect(behov).toHaveValue("Kartlegging av arbeidssituasjonen."));
  await user.clear(behov);
  await user.type(behov, tekst);
  await user.click(screen.getByRole("button", { name: /Neste steg/ }));
  await screen.findByRole("heading", { name: "Oppsummering av søknad om ekspertbistand" });
  return user;
};

describe("OppsummeringPage", () => {
  it("sender inn det lagrede utkastet uten body", async () => {
    const id = await apneOppsummering();

    await userEvent.setup().click(sendSoknad());

    await waitFor(async () => expect(await hentStatus(id)).toBe("innsendt"));
    expect(putKall()).toEqual([{ metode: "PUT", body: "" }]);
  });

  it("venter på autolagringen før innsending", async () => {
    const id = await opprettKomplettUtkast();
    let slippLagring!: () => void;
    const lagringHoldes = new Promise<void>((resolve) => (slippLagring = resolve));
    server.use(
      http.patch(`${EKSPERTBISTAND_API_PATH}/:id`, async () => {
        await lagringHoldes;
      })
    );
    const user = await endreBehovOgGaTilOppsummering(id, "Ny tekst");

    await user.click(sendSoknad());
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(putKall()).toHaveLength(0);

    slippLagring();

    await waitFor(async () => expect(await hentStatus(id)).toBe("innsendt"));
    const innsendt = await fetchJson<{ behovForBistand: { behov: string } }>(
      `${EKSPERTBISTAND_API_PATH}/${id}`
    );
    expect(innsendt?.behovForBistand.behov).toBe("Ny tekst");
  });

  it("sender ikke inn når lagringen feiler", async () => {
    const id = await opprettKomplettUtkast();
    server.use(
      http.patch(`${EKSPERTBISTAND_API_PATH}/:id`, () =>
        HttpResponse.json({ message: "feil" }, { status: 500 })
      )
    );
    const user = await endreBehovOgGaTilOppsummering(id, "Ny tekst");

    await user.click(sendSoknad());

    expect(
      await screen.findByText(/Vi fikk ikke lagret de siste endringene, så søknaden er ikke sendt/)
    ).toBeInTheDocument();
    expect(putKall()).toHaveLength(0);
    expect(await hentStatus(id)).toBe("utkast");
  });

  it("viser feil fra backend på feltet i riktig steg", async () => {
    await apneOppsummering();
    server.use(
      http.put(`${EKSPERTBISTAND_API_PATH}/:id`, () =>
        HttpResponse.json(
          { kode: "UGYLDIG_INPUT", felt: "ekspert.virksomhetOrgnr", message: "må være 9 siffer" },
          { status: 400 }
        )
      )
    );

    await userEvent.setup().click(sendSoknad());

    expect(await screen.findByText("Steg 2 av 4")).toBeInTheDocument();
    expect(
      (await screen.findAllByText("Fyll ut feltet riktig før du sender inn.")).length
    ).toBeGreaterThan(0);
  });
});
