import { test, expect } from "@playwright/test";
import { createDraftId, ensureMockServiceWorkerReady } from "./mockApi";

test("smoke: landing route", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/soknader$/);
  await expect(page.getByRole("heading", { name: "Søknader" })).toBeVisible();
});

test("smoke: soknader route", async ({ page }) => {
  await page.goto("/soknader");
  await expect(page.getByRole("heading", { name: "Søknader" })).toBeVisible();
});

test("smoke: skjema start route", async ({ page }) => {
  await page.goto("/skjema/start");
  await expect(
    page.getByRole("heading", { name: "Søknad om tilskudd til ekspertbistand" })
  ).toBeVisible();
});

test("smoke: skjema steg-1 route", async ({ page }) => {
  await page.goto("/");
  await ensureMockServiceWorkerReady(page);
  const draftId = await createDraftId(page);

  await page.goto(`/skjema/${draftId}/steg-1`);
  await expect(page.getByRole("heading", { name: "Søknadsskjema – ekspertbistand" })).toBeVisible();
});

test("smoke: skjema steg-2 route", async ({ page }) => {
  await page.goto("/");
  await ensureMockServiceWorkerReady(page);
  const draftId = await createDraftId(page);

  await page.goto(`/skjema/${draftId}/steg-2`);
  await expect(page.getByRole("heading", { name: "Behov for bistand" })).toBeVisible();
});

test("smoke: skjema oppsummering route", async ({ page }) => {
  await page.goto("/");
  await ensureMockServiceWorkerReady(page);
  const draftId = await createDraftId(page);

  await page.goto(`/skjema/${draftId}/oppsummering`);
  await expect(
    page.getByRole("heading", { name: "Oppsummering av søknad om ekspertbistand" })
  ).toBeVisible();
});

test("smoke: skjema kvittering route", async ({ page }) => {
  await page.goto("/");
  await ensureMockServiceWorkerReady(page);
  const draftId = await createDraftId(page);

  await page.goto(`/skjema/${draftId}/kvittering`);
  await expect(page.getByRole("heading", { name: "Nav har mottatt søknaden" })).toBeVisible();
});

test("smoke: health route", async ({ page }) => {
  await page.goto("/health");
  await expect(page.getByText(/Health: ok/i)).toBeVisible();
});
