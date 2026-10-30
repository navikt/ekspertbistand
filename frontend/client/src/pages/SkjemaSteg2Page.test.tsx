import { screen, waitFor, within } from "@testing-library/react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { DELTAKER_FNR, opprettBekreftetUtkast, opprettUtkast, renderSkjema } from "../test/skjema";

beforeAll(() => {
  vi.spyOn(window, "scrollTo").mockImplementation(() => {});
});

describe("SkjemaSteg2Page", () => {
  it("viser bekreftet deltaker og virksomhet som låste felt", async () => {
    const id = await opprettBekreftetUtkast();

    renderSkjema(`/skjema/${id}/steg-2`);

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

  it("sender arbeidsgiver til steg 1 når deltakeren ikke er bekreftet", async () => {
    const id = await opprettUtkast();

    renderSkjema(`/skjema/${id}/steg-2`);

    await waitFor(() =>
      expect(screen.getByRole("textbox", { name: "Fødselsnummer" })).not.toHaveAttribute("readonly")
    );
    expect(screen.queryByText("Steg 2 av 4")).not.toBeInTheDocument();
  });
});
