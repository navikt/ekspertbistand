import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "../../test/server";
import { fetchJson } from "../../utils/api";
import { EKSPERTBISTAND_API_PATH } from "../../utils/constants";
import { draftDtoServerSchema } from "./server-schemas";
import { opprettUtkast, validerDeltaker } from "./validerDeltaker";

const FNR = "01017012345";
const ANNEN_FNR = "02029012345";
const MOCK_BRUKER_FNR = "01819010001";
const ORGNR = "123456780";

const nyttUtkast = async () => {
  const resultat = await opprettUtkast({ fnr: FNR, virksomhetsnummer: ORGNR });
  if (!resultat.ok) throw new Error("Klarte ikke å opprette utkast i testoppsettet");
  return resultat.utkast;
};

const patchUtkast = async (id: string, body: unknown) =>
  draftDtoServerSchema.parse(
    await fetchJson(`${EKSPERTBISTAND_API_PATH}/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    })
  );

const VALIDER_URL = `${EKSPERTBISTAND_API_PATH}/:id/valider-deltaker`;

const svarMed = (url: string, status: number, body: Record<string, unknown>) =>
  server.use(http.post(url, () => HttpResponse.json(body, { status })));

const funksjoner = [
  [
    "opprettUtkast",
    EKSPERTBISTAND_API_PATH,
    () => opprettUtkast({ fnr: FNR, virksomhetsnummer: ORGNR }),
  ],
  [
    "validerDeltaker",
    VALIDER_URL,
    () => validerDeltaker("utkast-id", { fnr: FNR, virksomhetsnummer: ORGNR }),
  ],
] as const;

const forventetUtkast = (fnr: string) =>
  expect.objectContaining({
    deltakerBekreftet: true,
    opprettetAv: MOCK_BRUKER_FNR,
    ansatt: { fnr, navn: "Mona Moonlight" },
    virksomhet: expect.objectContaining({
      virksomhetsnummer: ORGNR,
      virksomhetsnavn: "Eksempel Bedrift AS Avd. Oslo",
    }),
  });

describe("opprettUtkast", () => {
  it("oppretter et bekreftet utkast med navn og virksomhet", async () => {
    const resultat = await opprettUtkast({ fnr: FNR, virksomhetsnummer: ORGNR });

    expect(resultat).toEqual({ ok: true, utkast: forventetUtkast(FNR) });
  });

  it("lagrer utkastet så det kan hentes", async () => {
    const { id } = await nyttUtkast();

    const lagret = draftDtoServerSchema.parse(await fetchJson(`${EKSPERTBISTAND_API_PATH}/${id}`));

    expect(lagret).toEqual(forventetUtkast(FNR));
  });
});

describe("validerDeltaker", () => {
  it("bytter deltaker på et lagret utkast", async () => {
    const { id } = await nyttUtkast();

    const resultat = await validerDeltaker(id!, {
      fnr: ANNEN_FNR,
      virksomhetsnummer: ORGNR,
    });

    expect(resultat).toEqual({ ok: true, utkast: forventetUtkast(ANNEN_FNR) });
  });

  it("PATCH endrer ikke deltaker eller virksomhet, men lagrer kontaktperson", async () => {
    const { id } = await nyttUtkast();

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
});

describe.each(funksjoner)("feil fra %s", (_, url, kall) => {
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
    svarMed(url, status, body);

    expect(await kall()).toEqual({ ok: false, feil });
  });

  it("gjør nettverksfeil om til oppslagFeilet", async () => {
    server.use(http.post(url, () => HttpResponse.error()));

    expect(await kall()).toEqual({ ok: false, feil: { type: "oppslagFeilet" } });
  });
});

describe("mocken", () => {
  it.each([
    ["01819030037", { type: "ingenArbeidsforhold" }],
    ["01819030118", { type: "forMangeOppslag" }],
    ["01819030207", { type: "oppslagFeilet" }],
  ])("gir feilsvar for test-fødselsnummer %s", async (fnr, feil) => {
    const { id } = await nyttUtkast();

    expect(await opprettUtkast({ fnr, virksomhetsnummer: ORGNR })).toEqual({ ok: false, feil });
    expect(await validerDeltaker(id!, { fnr, virksomhetsnummer: ORGNR })).toEqual({
      ok: false,
      feil,
    });
  });
});
