import { vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EkspertVirksomhetVelger } from "./EkspertVirksomhetVelger";

const sok = vi.fn();

vi.mock("../hooks/useEkspertVirksomhetSok", () => ({
  useEkspertVirksomhetSok: (navn: string) => sok(navn),
}));

const girTreff = (organisasjoner: { organisasjonsnummer: string; navn: string }[]) =>
  sok.mockReturnValue({ organisasjoner, isLoading: false, error: undefined });

beforeEach(() => {
  sok.mockReset();
});

/**
 * Ereg gjør søket, og matcher på ordnivå. Combobox må derfor ikke filtrere treffene
 * på nytt internt — da forsvinner gyldige treff og listen blir tom eller utdatert.
 */
it("viser treff fra Ereg uavhengig av ordstilling i søket", async () => {
  girTreff([{ organisasjonsnummer: "910825226", navn: "EKSPERT BISTAND AS" }]);

  render(<EkspertVirksomhetVelger label="Virksomhet" value="" onChange={vi.fn()} />);
  await userEvent.type(
    screen.getByRole("combobox", { name: /virksomhet/i }),
    "bistand ekspert"
  );

  expect(
    await screen.findByRole("option", { name: /EKSPERT BISTAND AS \(910825226\)/ })
  ).toBeInTheDocument();
});

it("viser treff der sammensattnavn har andre mellomrom enn søkeordet", async () => {
  girTreff([{ organisasjonsnummer: "937895321", navn: "SPAREBANK  1 SR-BANK ASA" }]);

  render(<EkspertVirksomhetVelger label="Virksomhet" value="" onChange={vi.fn()} />);
  await userEvent.type(
    screen.getByRole("combobox", { name: /virksomhet/i }),
    "sparebank 1"
  );

  expect(await screen.findByRole("option", { name: /SR-BANK/ })).toBeInTheDocument();
});

it("melder valg og avvelging til onChange", async () => {
  girTreff([{ organisasjonsnummer: "910825226", navn: "EKSPERT BISTAND AS" }]);
  const onChange = vi.fn();

  const { rerender } = render(
    <EkspertVirksomhetVelger label="Virksomhet" value="" onChange={onChange} />
  );

  const input = screen.getByRole("combobox", { name: /virksomhet/i });
  await userEvent.type(input, "ekspert");
  await userEvent.click(await screen.findByRole("option", { name: /910825226/ }));

  expect(onChange).toHaveBeenCalledWith({
    navn: "EKSPERT BISTAND AS",
    orgnr: "910825226",
  });

  onChange.mockClear();
  rerender(
    <EkspertVirksomhetVelger
      label="Virksomhet"
      value="EKSPERT BISTAND AS (910825226)"
      onChange={onChange}
    />
  );

  await userEvent.type(input, "ekspert");
  const valgtTreff = await screen.findByRole("option", { name: /910825226/ });
  expect(valgtTreff).toHaveAttribute("aria-selected", "true");

  await userEvent.click(valgtTreff);
  expect(onChange).toHaveBeenCalledWith(null);
});
