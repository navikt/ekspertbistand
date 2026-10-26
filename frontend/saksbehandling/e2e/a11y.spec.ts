import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const MOCK_CASE_ID = "9c2f7a10-0000-4000-8000-000000002010";

async function waitForAppRender(page: Page) {
  await page.waitForFunction(
    () => Boolean(document.getElementById("root")?.textContent?.trim()),
    undefined,
    { timeout: 10_000 }
  );
}

async function openMockedPage(page: Page, path: string) {
  await page.goto(path);
  await waitForAppRender(page);

  const serviceWorkerControlsPage = await page.evaluate(() =>
    Boolean(navigator.serviceWorker?.controller)
  );
  if (!serviceWorkerControlsPage) {
    await page.reload();
  }

  await waitForAppRender(page);
  await expect(page.getByRole("main").first()).toBeVisible();
}

async function expectNoAccessibilityViolations(page: Page) {
  const { violations } = await new AxeBuilder({ page }).analyze();
  expect(violations).toEqual([]);
}

test("the overview has no accessibility violations", async ({ page }) => {
  await openMockedPage(page, "/oversikt");

  await page.getByRole("combobox", { name: "Enhet" }).fill("Nav hjelpemiddelsentral Agder");
  await page.getByRole("option", { name: "Nav hjelpemiddelsentral Agder" }).click();
  await expect(page.getByRole("tab", { name: /^Oppgaver som venter \(\d+\)$/ })).toBeVisible();
  await expect(page.getByRole("table")).toBeVisible();
  await expect(page.getByRole("row", { name: /Hallandsbro hotell AS/ })).toBeVisible();
  await expectNoAccessibilityViolations(page);
});

test("a real case and its content tabs have no accessibility violations", async ({ page }) => {
  await openMockedPage(page, `/oversikt/${MOCK_CASE_ID}`);

  await expect(page.getByRole("heading", { name: "Situasjonen" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Søknad" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Vilkårsvurdering" })).toBeVisible();
  await expectNoAccessibilityViolations(page);

  await page.getByRole("tab", { name: "Sluttrapport" }).click();
  await expect(page.getByRole("heading", { name: "Sluttrapport", exact: true })).toBeVisible();
  await expectNoAccessibilityViolations(page);

  await page.getByRole("tab", { name: "Refusjon" }).click();
  await expect(page.getByRole("heading", { name: "Refusjonskrav", exact: true })).toBeVisible();
  await expectNoAccessibilityViolations(page);
});
