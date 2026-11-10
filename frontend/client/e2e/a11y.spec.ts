import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { createDraftId, ensureMockServiceWorkerReady } from "./mockApi";

const waitForAppReady = async (page: Page) => {
  await expect(page.getByRole("main")).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
};

// label-content-name-mismatch ble slått på som standard i axe-core 4.14
const globallyDisabledRules = ["label-content-name-mismatch"];

const runAxe = async (page: Page, disabledRules: string[] = []) => {
  const builder = new AxeBuilder({ page });
  builder.disableRules([...globallyDisabledRules, ...disabledRules]);
  const results = await builder.analyze();
  expect(results.violations).toEqual([]);
};

const routes = [
  { name: "landing page", path: "/" },
  { name: "soknader page", path: "/soknader" },
  { name: "soknad start page", path: "/skjema/start" },
  { name: "skjema steg 1 page", path: (id: string) => `/skjema/${id}/steg-1` },
  { name: "skjema steg 2 page", path: (id: string) => `/skjema/${id}/steg-2` },
  { name: "skjema steg 3 page", path: (id: string) => `/skjema/${id}/steg-3` },
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

// Faste id-er fra MOCK_VEDTAK_SOKNAD_ID i src/mocks/handlers.ts.
const vedtak = [
  { utfall: "innvilget", id: "7e0d5a00-0000-4000-8000-000000000691", status: "Søknad godkjent" },
  {
    utfall: "redusert beløp",
    id: "7e0d5a00-0000-4000-8000-000000000692",
    status: "Søknad godkjent med redusert beløp",
  },
  { utfall: "avslått", id: "7e0d5a00-0000-4000-8000-000000000693", status: "Søknad avslått" },
];

for (const { utfall, id, status } of vedtak) {
  for (const apentKort of [false, true]) {
    const beskrivelse = apentKort ? " with the vedtak card open" : "";
    test(`v2 vedtak page (${utfall})${beskrivelse} has no detectable accessibility violations`, async ({
      page,
    }) => {
      await page.goto("/v2/soknader");
      await waitForAppReady(page);
      await ensureMockServiceWorkerReady(page);

      await page.goto(`/v2/skjema/${id}/vedtak`);
      await waitForAppReady(page);
      await expect(page.getByRole("heading", { level: 2, name: status })).toBeVisible();

      if (apentKort) {
        const kort = page.getByRole("region", { name: /^Vedtak om/ });
        await kort.getByRole("button", { expanded: false }).click();
        await expect(
          kort.getByRole("link", { name: "Vedtaksbrev ekspertbistand.pdf" })
        ).toBeVisible();
        await expect(kort.locator("canvas").first()).toBeVisible();
      }

      await runAxe(page);
    });
  }
}
