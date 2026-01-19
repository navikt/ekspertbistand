import { test, expect } from "@playwright/test";

test("homepage redirects to soknader", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/soknader$/);
  await expect(page.getByRole("heading", { name: "Søknader" })).toBeVisible();
});
