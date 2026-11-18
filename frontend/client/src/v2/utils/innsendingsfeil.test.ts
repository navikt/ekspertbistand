import { describe, expect, it } from "vitest";
import { lesInnsendingsfeil } from "./innsendingsfeil";

const svar = (body: string, contentType: string | null, status = 400) =>
  new Response(body, {
    status,
    headers: contentType ? { "Content-Type": contentType } : undefined,
  });

describe("lesInnsendingsfeil", () => {
  it("bruker message fra JSON", async () => {
    await expect(
      lesInnsendingsfeil(
        svar(JSON.stringify({ message: "Beløpet er for høyt" }), "application/json")
      )
    ).resolves.toBe("Beløpet er for høyt");
  });

  it("bruker ren tekst fra backend", async () => {
    await expect(
      lesInnsendingsfeil(svar("Virus funnet i filen 'a.pdf'", "text/plain; charset=UTF-8", 422))
    ).resolves.toBe("Virus funnet i filen 'a.pdf'");
  });

  it("viser ikke HTML-feilsider fra proxy", async () => {
    await expect(
      lesInnsendingsfeil(svar("<html><body>502 Bad Gateway</body></html>", "text/html", 502))
    ).resolves.toBe("Feil ved innsending (502)");
  });

  it("faller tilbake når svaret er tomt eller mangler message", async () => {
    await expect(lesInnsendingsfeil(svar("", null, 500))).resolves.toBe(
      "Feil ved innsending (500)"
    );
    await expect(
      lesInnsendingsfeil(svar(JSON.stringify({ kode: "X" }), "application/json", 409))
    ).resolves.toBe("Feil ved innsending (409)");
  });
});
