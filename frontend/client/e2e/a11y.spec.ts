import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const waitForAppReady = async (page: Page) => {
  await expect(page.getByRole("main")).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
};

const runAxe = async (page: Page) => {
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
};

const createDraftId = async (page: Page) => {
  const id = await page.evaluate(async () => {
    const response = await fetch("/ekspertbistand-backend/api/skjema/v1", { method: "POST" });
    const data = (await response.json()) as { id?: string | null };
    return data.id ?? null;
  });
  if (!id) {
    throw new Error("Mock draft id was missing");
  }
  return id;
};

const routes = [
  { name: "landing page", path: "/" },
  { name: "soknader page", path: "/soknader" },
  { name: "soknad start page", path: "/skjema/start" },
  { name: "skjema steg 1 page", path: (id: string) => `/skjema/${id}/steg-1` },
  { name: "skjema steg 2 page", path: (id: string) => `/skjema/${id}/steg-2` },
  { name: "oppsummering page", path: (id: string) => `/skjema/${id}/oppsummering` },
  { name: "kvittering page", path: (id: string) => `/skjema/${id}/kvittering` },
];

for (const route of routes) {
  test(`${route.name} has no detectable accessibility violations`, async ({ page }) => {
    if (typeof route.path === "string") {
      await page.goto(route.path);
      await waitForAppReady(page);
      await runAxe(page);
      return;
    }

    await page.goto("/");
    await waitForAppReady(page);
    const id = await createDraftId(page);

    await page.goto(route.path(id));
    await waitForAppReady(page);
    await runAxe(page);
  });
}
