import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "../../test/server";
import {
  ANNEN_DELTAKER_FNR,
  DELTAKER_FNR,
  MOCK_BRUKER_FNR,
  opprettBekreftetUtkast,
  renderSkjema,
  tellValiderDeltakerKall,
} from "../../test/skjemaV2";
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
  renderSkjema(`/v2/skjema/${id}/steg-1`);
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
    expect(screen.getByRole("button", { name: /åpne virksomhetsmeny/ })).toHaveAttribute(
      "id",
      "virksomhet.virksomhetsnummer"
    );
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

  describe("nytt utkast", () => {
    let opprettKall: { request: Request }[] = [];
    const lytter = ({ request }: { request: Request }) => {
      if (request.method === "POST" && new URL(request.url).pathname === EKSPERTBISTAND_API_PATH)
        opprettKall.push({ request: request.clone() });
    };

    beforeEach(() => {
      opprettKall = [];
      server.events.on("request:start", lytter);
    });
    afterEach(() => server.events.removeListener("request:start", lytter));

    const apneNyttUtkast = async (fnr: string) => {
      renderSkjema("/v2/skjema/ny/steg-1");
      const fnrFelt = await screen.findByRole("textbox", { name: "Fødselsnummer" });
      await screen.findByRole("button", { name: /åpne virksomhetsmeny/ });
      const user = await skrivFnr(fnrFelt, fnr);
      return { user, fnrFelt };
    };

    it("oppretter utkastet med deltaker og virksomhet og går til steg 2", async () => {
      const { user } = await apneNyttUtkast(DELTAKER_FNR);

      await user.click(nesteSteg());

      await erPaSteg2();
      expect(opprettKall).toHaveLength(1);
      expect(await opprettKall[0].request.json()).toEqual({
        fnr: DELTAKER_FNR,
        virksomhetsnummer: expect.stringMatching(/^\d{9}$/),
      });
    });

    it("viser ikke «Fortsett senere» og «Slett søknaden» før utkastet finnes", async () => {
      renderSkjema("/v2/skjema/ny/steg-1");
      await screen.findByRole("textbox", { name: "Fødselsnummer" });

      expect(screen.queryByRole("button", { name: /Fortsett senere/ })).toBeNull();
      expect(screen.queryByRole("button", { name: /Slett søknaden/ })).toBeNull();
    });

    it("oppretter ikke utkast når fødselsnummeret er ugyldig", async () => {
      const { user } = await apneNyttUtkast("12345678901");

      await user.click(nesteSteg());

      expect(await screen.findAllByText("Fødselsnummer er ikke gyldig.")).not.toHaveLength(0);
      expect(opprettKall).toHaveLength(0);
    });

    it("viser varsel og blir på steg 1 når oppslaget feiler", async () => {
      server.use(
        http.post(EKSPERTBISTAND_API_PATH, () =>
          HttpResponse.json({ message: "feil" }, { status: 503 })
        )
      );
      const { user } = await apneNyttUtkast(DELTAKER_FNR);

      await user.click(nesteSteg());

      expect(
        await screen.findByText(/Vi kunne ikke sjekke arbeidsforholdet akkurat nå/)
      ).toBeInTheDocument();
      erIkkePaSteg2();
    });

    describe("når fødselsnummeret er brukerens eget", () => {
      const apneDialog = async () => {
        const { user } = await apneNyttUtkast(MOCK_BRUKER_FNR);
        await user.click(nesteSteg());
        await screen.findByRole("dialog", { name: "Er du selv deltakeren?" });
        return user;
      };

      it("oppretter utkastet før dialogen åpnes", async () => {
        await apneDialog();

        expect(opprettKall).toHaveLength(1);
      });

      it("«Ja, jeg er deltakeren» går til steg 2", async () => {
        const user = await apneDialog();

        await user.click(screen.getByRole("button", { name: "Ja, jeg er deltakeren" }));

        await erPaSteg2();
      });

      it("«Nei, endre fødselsnummer» åpner det nye utkastet og gir fokus til feltet", async () => {
        const user = await apneDialog();

        await user.click(screen.getByRole("button", { name: "Nei, endre fødselsnummer" }));

        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
        const fnrFelt = screen.getByRole("textbox", { name: "Fødselsnummer" });
        await waitFor(() => expect(fnrFelt).toHaveValue(MOCK_BRUKER_FNR));
        await waitFor(() => expect(fnrFelt).toHaveFocus());
        expect(screen.getByRole("button", { name: /Fortsett senere/ })).toBeInTheDocument();
        expect(opprettKall).toHaveLength(1);
      });
    });
  });
});
