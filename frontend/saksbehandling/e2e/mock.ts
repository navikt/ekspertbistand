import { expect, type Page } from "@playwright/test";

async function waitForAppRender(page: Page) {
  await page.waitForFunction(
    () => Boolean(document.getElementById("root")?.textContent?.trim()),
    undefined,
    { timeout: 10_000 }
  );
}

export async function openMockedPage(page: Page, path: string) {
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
