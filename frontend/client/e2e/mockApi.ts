import { expect, type Page } from "@playwright/test";

export const ensureMockServiceWorkerReady = async (page: Page) => {
  await expect(page.getByRole("main")).toBeVisible();

  if (!(await page.evaluate(() => !!navigator.serviceWorker?.controller))) {
    await page.reload();
  }

  await expect(page.getByRole("main")).toBeVisible();
  await page.waitForFunction(() => !!navigator.serviceWorker?.controller, undefined, {
    timeout: 10000,
  });
};

/** Oppretter et utkast med bekreftet deltaker, slik at alle stegene kan åpnes. */
export const createDraftId = async (page: Page): Promise<string> =>
  page.evaluate(async () => {
    const response = await fetch("/ekspertbistand-backend/api/soknad/v1", { method: "POST" });
    if (!response.ok) {
      throw new Error(`Mock API returned ${response.status} when creating draft.`);
    }
    const data = (await response.json()) as { id?: string | null };
    if (!data?.id) {
      throw new Error("Missing draft id from mock API.");
    }
    const validering = await fetch(
      `/ekspertbistand-backend/api/soknad/v1/${data.id}/valider-deltaker`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ fnr: "01819010192", virksomhetsnummer: "123456780" }),
      }
    );
    if (!validering.ok) {
      throw new Error(`Mock API returned ${validering.status} when validating deltaker.`);
    }
    return data.id;
  });
