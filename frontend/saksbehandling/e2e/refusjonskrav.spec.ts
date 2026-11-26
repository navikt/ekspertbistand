import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { openMockedPage } from "./mock";

const sakId = "9c2f7a10-0000-4000-8000-000000002030";

test("sluttrapport må bekreftes før dokumentkontroll og avvisning av refusjonskrav", async ({
  page,
}) => {
  await openMockedPage(page, `/oversikt/${sakId}?fane=refusjon`);
  await expect(
    page.getByText("Sluttrapporten må være bekreftet før refusjonskravet kan vurderes.")
  ).toBeVisible();
  await page.getByRole("tab", { name: "Sluttrapport" }).click();
  await page.getByRole("button", { name: "Registrer som mottatt" }).click();
  await expect(
    page.getByRole("heading", { name: "Sluttrapport registrert som mottatt" })
  ).toBeVisible();
  await page.getByRole("tab", { name: "Refusjon", exact: true }).click();
  const valg = page.getByRole("combobox", {
    name: "Kan refusjonskravet behandles med dokumentasjonen som er mottatt?",
  });
  await valg.selectOption("nei");
  await page.getByRole("button", { name: "Avvis, be arbeidsgiver sende på nytt" }).click();
  await expect(page.getByText("Velg minst én årsak.")).toBeVisible();
  await page.getByRole("checkbox", { name: "Annet", exact: true }).check();
  await expect(page.getByText(/Vennligst send refusjonskravet på nytt/)).toBeVisible();
  await expect(page.getByRole("textbox")).toHaveCount(0);
  await page.getByRole("checkbox", { name: "Dokumentet er ikke leselig" }).check();
  const uu = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
  expect(uu.violations).toEqual([]);
  await page.getByRole("button", { name: "Avvis, be arbeidsgiver sende på nytt" }).click();
  await expect(page.getByRole("heading", { name: "Refusjonskrav avvist" })).toBeVisible();
  await expect(page.getByText("Venter nytt refusjonskrav", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Ingen refusjonskrav til behandling" })
  ).toBeVisible();
  await expect(page.getByRole("tab", { name: "Refusjon", exact: true })).toBeVisible();
});

test("kontroll forsvinner når saken tildeles en annen mens skjemaet er åpent", async ({ page }) => {
  await openMockedPage(page, `/oversikt/${sakId}?fane=sluttrapport`);
  await page.getByRole("button", { name: "Registrer som mottatt" }).click();
  await expect(
    page.getByRole("heading", { name: "Sluttrapport registrert som mottatt" })
  ).toBeVisible();
  await page.getByRole("tab", { name: "Refusjon", exact: true }).click();
  await page
    .getByRole("combobox", {
      name: "Kan refusjonskravet behandles med dokumentasjonen som er mottatt?",
    })
    .selectOption("nei");
  await page.evaluate(async (id) => {
    const { endreSak } = await import("/e2e/sakstilstand.ts");
    await endreSak(id, { saksbehandlerIdent: "Z999999" });
  }, sakId);
  await expect(
    page.getByRole("button", { name: "Avvis, be arbeidsgiver sende på nytt" })
  ).toHaveCount(0);
  await expect(
    page.getByText("Det er bare tildelt saksbehandler som kan vurdere refusjonskravet.")
  ).toBeVisible();
});
