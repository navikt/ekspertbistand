import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const waitForAppReady = async (page: Page) => {
  await expect(page.getByRole("main")).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
};

const ensureMockServiceWorkerReady = async (page: Page) => {
  await page.evaluate(async () => {
    if ("serviceWorker" in navigator) {
      await navigator.serviceWorker.ready;
    }
  });

  const hasController = await page.evaluate(
    () => !!navigator.serviceWorker && !!navigator.serviceWorker.controller
  );

  if (!hasController) {
    await page.reload();
    await page.evaluate(async () => {
      if ("serviceWorker" in navigator) {
        await navigator.serviceWorker.ready;
      }
    });
  }
};

const runAxe = async (page: Page, disabledRules: string[] = []) => {
  const builder = new AxeBuilder({ page });
  if (disabledRules.length > 0) {
    builder.disableRules(disabledRules);
  }
  const results = await builder.analyze();
  expect(results.violations).toEqual([]);
};

const tryCreateDraftId = async (page: Page): Promise<string | null> =>
  page.evaluate(async () => {
    const response = await fetch("/ekspertbistand-backend/api/skjema/v1", { method: "POST" });
    if (!response.ok) {
      return null;
    }
    const data = (await response.json()) as { id?: string | null };
    return data?.id ?? null;
  });

const createDraftId = async (page: Page) => {
  let id = await tryCreateDraftId(page);
  if (!id) {
    await page.reload();
    await ensureMockServiceWorkerReady(page);
    id = await tryCreateDraftId(page);
  }
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
      const disabledRules = route.name === "soknader page" ? ["heading-order"] : [];
      await runAxe(page, disabledRules);
      return;
    }

    await page.goto("/");
    await waitForAppReady(page);
    await ensureMockServiceWorkerReady(page);
    const id = await createDraftId(page);

    await page.goto(route.path(id));
    await waitForAppReady(page);
    await runAxe(page);
  });
}
