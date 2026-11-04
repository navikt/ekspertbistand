import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "../../test/server";
import { EKSPERTBISTAND_API_PATH } from "../../utils/constants";
import { fetchSkjema, resolveStatusTag } from "./soknader";

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
});

describe("fetchSkjema", () => {
  it("viser utkast uten virksomhet og deltaker, slik backend sender dem med null", async () => {
    server.use(
      http.get(EKSPERTBISTAND_API_PATH, ({ request }) => {
        const status = new URL(request.url).searchParams.get("status");
        if (status !== "utkast") return HttpResponse.json([]);
        return HttpResponse.json([
          {
            id: "utkast-uten-virksomhet",
            status: "utkast",
            virksomhet: null,
            ansatt: null,
            ekspert: null,
            nav: null,
            opprettetAv: "01819010001",
            opprettetTidspunkt: "2026-11-02T10:00:00Z",
            deltakerBekreftet: false,
          },
        ]);
      })
    );

    const soknader = await fetchSkjema();

    expect(soknader).toEqual([
      expect.objectContaining({
        id: "utkast-uten-virksomhet",
        title: "Søknad om tilskudd til ekspertbistand",
        description: "Virksomhetsinformasjon mangler.",
        href: "/skjema/utkast-uten-virksomhet",
      }),
    ]);
  });
});
