import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "../../test/server";
import { fetchJson } from "../../utils/api";
import { EKSPERTBISTAND_API_PATH } from "../../utils/constants";
import { draftDtoServerSchema } from "./server-schemas";
import { validerDeltaker } from "./validerDeltaker";

const FNR = "01017012345";
const ORGNR = "123456780";

const opprettUtkast = async () =>
  draftDtoServerSchema.parse(await fetchJson(EKSPERTBISTAND_API_PATH, { method: "POST" }));

const patchUtkast = async (id: string, body: unknown) =>
  draftDtoServerSchema.parse(
    await fetchJson(`${EKSPERTBISTAND_API_PATH}/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    })
  );

const svarMed = (status: number, body: Record<string, unknown>) =>
  server.use(
    http.post(`${EKSPERTBISTAND_API_PATH}/:id/valider-deltaker`, () =>
      HttpResponse.json(body, { status })
    )
  );

describe("validerDeltaker", () => {
  it("returnerer utkastet med navn, virksomhet og bekreftelse", async () => {
    const { id } = await opprettUtkast();

    const resultat = await validerDeltaker(id!, { fnr: FNR, virksomhetsnummer: ORGNR });

    expect(resultat).toEqual({
      ok: true,
      utkast: expect.objectContaining({
        deltakerBekreftet: true,
        ansatt: { fnr: FNR, navn: "Mona Moonlight" },
        virksomhet: expect.objectContaining({
          virksomhetsnummer: ORGNR,
          virksomhetsnavn: "Eksempel Bedrift AS Avd. Oslo",
        }),
      }),
    });
  });

  it("PATCH endrer ikke deltaker eller virksomhet, men lagrer kontaktperson", async () => {
    const { id } = await opprettUtkast();
    await validerDeltaker(id!, { fnr: FNR, virksomhetsnummer: ORGNR });

    const utkast = await patchUtkast(id!, {
      ansatt: { fnr: "02029012345", navn: "Skrevet Selv" },
      virksomhet: {
        virksomhetsnummer: "987654321",
        virksomhetsnavn: "Feil AS",
        kontaktperson: { navn: "Kari Nordmann", epost: "kari@test.no", telefonnummer: "99999999" },
      },
    });

    expect(utkast.deltakerBekreftet).toBe(true);
    expect(utkast.ansatt).toEqual({ fnr: FNR, navn: "Mona Moonlight" });
    expect(utkast.virksomhet?.virksomhetsnummer).toBe(ORGNR);
    expect(utkast.virksomhet?.virksomhetsnavn).toBe("Eksempel Bedrift AS Avd. Oslo");
    expect(utkast.virksomhet?.kontaktperson?.navn).toBe("Kari Nordmann");
  });

  it("nytt utkast er ikke bekreftet", async () => {
    expect((await opprettUtkast()).deltakerBekreftet).toBe(false);
  });

  it.each([
    [422, { kode: "INGEN_ARBEIDSFORHOLD", message: "" }, { type: "ingenArbeidsforhold" }],
    [429, { kode: "FOR_MANGE_OPPSLAG", message: "" }, { type: "forMangeOppslag" }],
    [503, { kode: "OPPSLAG_FEILET", message: "" }, { type: "oppslagFeilet" }],
    [409, { message: "soknad ikke i utkast-status" }, { type: "oppslagFeilet" }],
    [
      400,
      { kode: "UGYLDIG_INPUT", felt: "fnr", message: "ugyldig fødselsnummer" },
      { type: "ugyldigInput", felt: "fnr", message: "ugyldig fødselsnummer" },
    ],
  ])("gjør %i om til riktig feil", async (status, body, feil) => {
    svarMed(status, body);

    const resultat = await validerDeltaker("utkast-id", { fnr: FNR, virksomhetsnummer: ORGNR });

    expect(resultat).toEqual({ ok: false, feil });
  });

  it("gjør nettverksfeil om til oppslagFeilet", async () => {
    server.use(
      http.post(`${EKSPERTBISTAND_API_PATH}/:id/valider-deltaker`, () => HttpResponse.error())
    );

    const resultat = await validerDeltaker("utkast-id", { fnr: FNR, virksomhetsnummer: ORGNR });

    expect(resultat).toEqual({ ok: false, feil: { type: "oppslagFeilet" } });
  });
});
