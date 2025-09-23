import { test, expect } from "@playwright/test";

test("homepage shows Ekspertbistand header", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /Ekspertbistand/i })).toBeVisible();
});
