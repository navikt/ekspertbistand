import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchJson } from "../../../utils/api";
import { EKSPERTBISTAND_API_PATH } from "../../utils/constants";
import { fetchSkjema, resolveStatusTag } from "./soknader";

vi.mock("../../../utils/api", () => ({ fetchJson: vi.fn() }));

beforeEach(() => vi.resetAllMocks());

describe("resolveStatusTag", () => {
  it("viser avslått søknad som Avslått", () => {
    expect(resolveStatusTag("avslatt")).toEqual({ label: "Avslått", variant: "error" });
  });

  it("viser avlyst Arena-søknad som Trukket eller avslått", () => {
    expect(resolveStatusTag("avlyst")).toEqual({
      label: "Trukket eller avslått",
      variant: "error",
    });
  });

  it("viser godkjent søknad som Godkjent", () => {
    expect(resolveStatusTag("godkjent")).toEqual({ label: "Godkjent", variant: "success" });
  });

  it("viser innsendt søknad og utkast med egne statuser", () => {
    expect(resolveStatusTag("innsendt")).toEqual({ label: "Innsendt", variant: "alt1" });
    expect(resolveStatusTag("utkast")).toEqual({ label: "Utkast", variant: "neutral" });
  });

  it("viser ukjente statuser uten å behandle dem som avslag", () => {
    expect(resolveStatusTag("under_behandling")).toEqual({
      label: "under_behandling",
      variant: "info",
    });
  });
});

describe("fetchSkjema", () => {
  it("sorterer søknader, gir utkast redigeringslenke og lar beslutningen overstyre status", async () => {
    vi.mocked(fetchJson)
      .mockResolvedValueOnce([
        {
          id: "besluttet",
          status: "avslatt",
          beslutning: { status: "godkjent" },
          innsendtTidspunkt: "2026-01-03T12:00:00",
          ansatt: { navn: "Ada" },
          virksomhet: { virksomhetsnavn: "Eksempel AS", virksomhetsnummer: "123456789" },
        },
        {
          id: "innsendt",
          status: "innsendt",
          innsendtTidspunkt: "2026-01-02T12:00:00",
        },
      ])
      .mockResolvedValueOnce([
        {
          id: "utkast",
          status: "utkast",
          opprettetTidspunkt: "2026-01-01T12:00:00",
        },
      ]);

    const soknader = await fetchSkjema();

    expect(fetchJson).toHaveBeenNthCalledWith(1, `${EKSPERTBISTAND_API_PATH}?status=innsendt`);
    expect(fetchJson).toHaveBeenNthCalledWith(2, `${EKSPERTBISTAND_API_PATH}?status=utkast`);
    expect(soknader).toMatchObject([
      {
        id: "besluttet",
        title: "Søknad om tilskudd til ekspertbistand – Ada",
        description: "Eksempel AS (org.nr 123456789)",
        href: "/v2/skjema/besluttet/kvittering",
        tag: { label: expect.stringMatching(/^Godkjent: /), variant: "success" },
      },
      {
        id: "innsendt",
        href: "/v2/skjema/innsendt/kvittering",
        tag: { label: expect.stringMatching(/^Innsendt: /), variant: "alt1" },
      },
      {
        id: "utkast",
        href: "/v2/skjema/utkast",
        tag: { label: expect.stringMatching(/^Utkast: /), variant: "neutral" },
      },
    ]);
  });
});
