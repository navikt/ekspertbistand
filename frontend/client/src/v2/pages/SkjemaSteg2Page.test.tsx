import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { MOCK_EREG_FEIL_ORGNR } from "../../mocks/handlers";
import { server } from "../../test/server";
import {
  DELTAKER_FNR,
  opprettBekreftetUtkast,
  opprettKomplettUtkast,
  renderSkjema,
} from "../../test/skjemaV2";
import { fetchJson } from "../../utils/api";
import type { DraftDto } from "../features/soknad/payload";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";

beforeAll(() => {
  vi.spyOn(window, "scrollTo").mockImplementation(() => {});
});

describe("SkjemaSteg2Page", () => {
  it("viser bekreftet deltaker og virksomhet som låste felt", async () => {
    const id = await opprettBekreftetUtkast();

    renderSkjema(`/v2/skjema/${id}/steg-2`);

    const deltaker = within(await screen.findByRole("group", { name: "Deltaker" }));
    await waitFor(() =>
      expect(deltaker.getByRole("textbox", { name: /Navn/ })).toHaveValue("Mona Moonlight")
    );
    const felter = [
      [deltaker.getByRole("textbox", { name: /Fødselsnummer/ }), DELTAKER_FNR],
      [deltaker.getByRole("textbox", { name: /Navn/ }), "Mona Moonlight"],
      [
        deltaker.getByRole("textbox", { name: /Virksomheten som deltakeren er ansatt i/ }),
        "Eksempel Bedrift AS Avd. Oslo",
      ],
    ] as const;
    for (const [felt, verdi] of felter) {
      expect(felt).toHaveValue(verdi);
      expect(felt).toHaveAttribute("readonly");
    }
    expect(deltaker.getByText("Org.nr. 123456780")).toBeInTheDocument();
  });

  describe("feil fra oppslaget av ekspertens virksomhet", () => {
    const patchSvarer = (status: number, body: Record<string, unknown>) =>
      server.use(
        http.patch(`${EKSPERTBISTAND_API_PATH}/:id`, () => HttpResponse.json(body, { status }))
      );

    const endreEkspertnavn = async (patchSvar: Parameters<typeof patchSvarer>) => {
      const id = await opprettKomplettUtkast();
      patchSvarer(...patchSvar);
      renderSkjema(`/v2/skjema/${id}/steg-2`);
      const navn = await screen.findByDisplayValue("Dr. Ekspert");
      const user = userEvent.setup();
      await user.type(navn, " Jr.");
      return user;
    };

    const virksomhetsfeltet = () => screen.getByRole("combobox", { name: /Tilknyttet virksomhet/ });

    it.each([
      [
        400,
        { kode: "UGYLDIG_INPUT", felt: "ekspert.virksomhetOrgnr", message: "finnes ikke" },
        "Vi fant ikke virksomheten i Enhetsregisteret.",
      ],
      [
        503,
        { kode: "OPPSLAG_FEILET", message: "oppslag feilet" },
        "Vi fikk ikke hentet virksomheten fra Enhetsregisteret.",
      ],
    ])("viser %i på feltet i stedet for som lagringsfeil", async (status, body, tekst) => {
      await endreEkspertnavn([status, body]);

      const feil = await screen.findByText(new RegExp(tekst), {}, { timeout: 3000 });
      expect(virksomhetsfeltet()).toHaveAttribute(
        "aria-describedby",
        expect.stringContaining(feil.closest("[id]")!.id)
      );
      expect(screen.queryByText(/Utkastet ble ikke lagret/)).toBeNull();
    });

    it("viser andre lagringsfeil som før", async () => {
      await endreEkspertnavn([500, { message: "noe gikk galt" }]);

      expect(
        await screen.findByText(/Utkastet ble ikke lagret/, {}, { timeout: 3000 })
      ).toBeInTheDocument();
      expect(screen.queryByText(/Enhetsregisteret/)).toBeNull();
    });

    it("går ikke til steg 3 så lenge virksomheten ikke er lagret", async () => {
      const user = await endreEkspertnavn([
        400,
        { kode: "UGYLDIG_INPUT", felt: "ekspert.virksomhetOrgnr", message: "" },
      ]);
      await screen.findByText(/Vi fant ikke virksomheten/, {}, { timeout: 3000 });

      await user.click(screen.getByRole("button", { name: /Neste steg/ }));

      expect(
        await screen.findByRole("heading", {
          name: "Du må rette disse feilene før du kan fortsette:",
        })
      ).toBeInTheDocument();
      expect(screen.getByText("Steg 2 av 4")).toBeInTheDocument();
    });
  });

  describe("mocken slår opp ekspertens virksomhet ved PATCH", () => {
    const patchEkspert = async (virksomhetOrgnr: string) => {
      const id = await opprettKomplettUtkast();
      return fetchJson<DraftDto>(`${EKSPERTBISTAND_API_PATH}/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ekspert: { virksomhet: "Feil AS", virksomhetNavn: "Feil AS", virksomhetOrgnr },
        }),
      });
    };

    it("henter navnet fra Enhetsregisteret og ignorerer navnet fra klienten", async () => {
      const utkast = await patchEkspert("915933149");

      expect(utkast?.ekspert).toMatchObject({
        virksomhet: "Ekspertpartner Norge AS (915933149)",
        virksomhetNavn: "Ekspertpartner Norge AS",
        virksomhetOrgnr: "915933149",
      });
    });

    it.each([
      ["123123123", 400, "ekspert.virksomhetOrgnr", "UGYLDIG_INPUT"],
      [MOCK_EREG_FEIL_ORGNR, 503, undefined, "OPPSLAG_FEILET"],
    ])("gir feil for %s", async (orgnr, status, field, code) => {
      await expect(patchEkspert(orgnr)).rejects.toMatchObject({ status, field, code });
    });
  });
});
