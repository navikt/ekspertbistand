import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { server } from "../test/server";
import {
  ANNEN_DELTAKER_FNR,
  DELTAKER_FNR,
  MOCK_BRUKER_FNR,
  opprettBekreftetUtkast,
  renderSkjema,
  tellValiderDeltakerKall,
} from "../test/skjema";
import { EKSPERTBISTAND_API_PATH, TILGANGSSTYRING_URL } from "../utils/constants";

beforeAll(() => {
  vi.spyOn(window, "scrollTo").mockImplementation(() => {});
});

let kall: ReturnType<typeof tellValiderDeltakerKall> | undefined;
afterEach(() => {
  kall?.stopp();
  kall = undefined;
});

const antallKall = () => kall?.teller.antall ?? 0;

const svarMed = (status: number) =>
  server.use(
    http.post(`${EKSPERTBISTAND_API_PATH}/:id/valider-deltaker`, () =>
      HttpResponse.json({ message: "feil" }, { status })
    )
  );

const apneSteg1 = async (fnr = DELTAKER_FNR) => {
  const id = await opprettBekreftetUtkast(fnr);
  renderSkjema(`/skjema/${id}/steg-1`);
  const fnrFelt = await screen.findByRole("textbox", { name: "Fødselsnummer" });
  await waitFor(() => expect(fnrFelt).toHaveValue(fnr));
  kall = tellValiderDeltakerKall();
  return fnrFelt;
};

const skrivFnr = async (felt: HTMLElement, fnr: string) => {
  const user = userEvent.setup();
  await user.clear(felt);
  await user.type(felt, fnr);
  return user;
};

const nesteSteg = () => screen.getByRole("button", { name: /Neste steg/ });

const erPaSteg2 = () => screen.findByText("Steg 2 av 4");
const erIkkePaSteg2 = () => expect(screen.queryByText("Steg 2 av 4")).toBeNull();

describe("SkjemaSteg1Page", () => {
  it("går til steg 2 når deltakeren har arbeidsforhold i virksomheten", async () => {
    const fnr = await apneSteg1();
    const user = await skrivFnr(fnr, ANNEN_DELTAKER_FNR);

    await user.click(nesteSteg());

    await erPaSteg2();
    expect(antallKall()).toBe(1);
  });

  it("går til steg 2 uten kall når deltakeren er uendret og bekreftet", async () => {
    await apneSteg1();

    await userEvent.setup().click(nesteSteg());

    await erPaSteg2();
    expect(antallKall()).toBe(0);
  });

  it("viser feiloppsummering med lenke til Altinn ved 422", async () => {
    const fnr = await apneSteg1();
    svarMed(422);
    const user = await skrivFnr(fnr, ANNEN_DELTAKER_FNR);

    await user.click(nesteSteg());

    expect(
      await screen.findByRole("heading", { name: "For å gå videre må du rette opp følgende:" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /Velg virksomheten som deltaker er ansatt i/ })
    ).toHaveAttribute("href", "#virksomhet.virksomhetsnummer");
    expect(screen.getByRole("link", { name: /be om tilgang til enkelttjenesten/ })).toHaveAttribute(
      "href",
      TILGANGSSTYRING_URL
    );
    erIkkePaSteg2();
  });

  it.each([
    [429, "Du har slått opp for mange ulike fødselsnummer på kort tid."],
    [503, "Vi kunne ikke sjekke arbeidsforholdet akkurat nå. Prøv igjen senere."],
  ])("viser varsel ved %i", async (status, tekst) => {
    const fnr = await apneSteg1();
    svarMed(status);
    const user = await skrivFnr(fnr, ANNEN_DELTAKER_FNR);

    await user.click(nesteSteg());

    expect(await screen.findByText(new RegExp(tekst))).toBeInTheDocument();
    erIkkePaSteg2();
  });

  it("gjør ikke kallet når fødselsnummeret er ugyldig", async () => {
    const fnr = await apneSteg1();
    const user = await skrivFnr(fnr, "12345678901");

    await user.click(nesteSteg());

    expect(await screen.findAllByText("Fødselsnummer er ikke gyldig.")).not.toHaveLength(0);
    expect(antallKall()).toBe(0);
  });

  describe("når fødselsnummeret er brukerens eget", () => {
    const apneDialog = async () => {
      const fnr = await apneSteg1();
      const user = await skrivFnr(fnr, MOCK_BRUKER_FNR);
      await user.click(nesteSteg());
      await screen.findByRole("dialog", { name: "Er du selv deltakeren?" });
      return { user, fnr };
    };

    it("åpner dialogen før kallet", async () => {
      await apneDialog();

      expect(antallKall()).toBe(0);
    });

    it("«Ja, jeg er deltakeren» gjør kallet og går til steg 2", async () => {
      const { user } = await apneDialog();

      await user.click(screen.getByRole("button", { name: "Ja, jeg er deltakeren" }));

      await erPaSteg2();
      expect(antallKall()).toBe(1);
    });

    it("«Nei, endre fødselsnummer» lukker dialogen uten kall og gir fokus til feltet", async () => {
      const { user, fnr } = await apneDialog();

      await user.click(screen.getByRole("button", { name: "Nei, endre fødselsnummer" }));

      await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
      await waitFor(() => expect(fnr).toHaveFocus());
      expect(antallKall()).toBe(0);
    });
  });
});
