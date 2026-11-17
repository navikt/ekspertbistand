import { screen, waitFor, within } from "@testing-library/react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { DELTAKER_FNR, opprettBekreftetUtkast, renderSkjema } from "../../test/skjemaV2";

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
});
