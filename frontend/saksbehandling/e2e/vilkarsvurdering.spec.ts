import { expect, test, type Page } from "@playwright/test";
import { openMockedPage } from "./mock";

const sakUrl = (nummer: string) => `/oversikt/9c2f7a10-0000-4000-8000-00000000${nummer}`;

async function openVilkårsvurdering(page: Page, nummer: string) {
  await openMockedPage(page, sakUrl(nummer));
  await page.getByRole("tab", { name: "Vilkårsvurdering" }).click();
}

const vilkårItem = (page: Page, tittel: string) =>
  page
    .locator(".aksel-accordion__item")
    .filter({ has: page.getByRole("button", { name: tittel, exact: true }) });

const automatiskTag = (page: Page, tittel: string) =>
  vilkårItem(page, tittel).getByText("Automatisk", { exact: true });

test("arbeidsforhold og deltaker er enig er forhåndsutfylt som oppfylt og merket automatisk", async ({
  page,
}) => {
  await openVilkårsvurdering(page, "2002");

  for (const tittel of ["Arbeidsforhold", "Deltaker er enig"]) {
    const item = vilkårItem(page, tittel);
    await expect(item.getByText("Oppfylt", { exact: true })).toBeVisible();
    await expect(automatiskTag(page, tittel)).toBeVisible();
    await expect(item.getByText(/Vurdert av/)).toHaveCount(0);
  }
  await expect(automatiskTag(page, "Prøvd tilrettelegging")).toHaveCount(0);
});

for (const knapp of ["Oppfylt", "Ikke oppfylt"]) {
  test(`manuell lagring som «${knapp}» fjerner automatisk-taggen`, async ({ page }) => {
    await openVilkårsvurdering(page, "2002");
    const item = vilkårItem(page, "Arbeidsforhold");

    await item.getByRole("button", { name: "Endre" }).click();
    await item.getByRole("button", { name: knapp, exact: true }).click();

    await expect(item.getByText(knapp, { exact: true })).toBeVisible();
    await expect(automatiskTag(page, "Arbeidsforhold")).toHaveCount(0);
    await expect(item.getByText(/Vurdert av/)).toBeVisible();
    await expect(automatiskTag(page, "Deltaker er enig")).toBeVisible();
  });
}

test("avbryt beholder automatisk-taggen", async ({ page }) => {
  await openVilkårsvurdering(page, "2002");
  const item = vilkårItem(page, "Arbeidsforhold");

  await item.getByRole("button", { name: "Endre" }).click();
  await item.getByRole("button", { name: "Avbryt" }).click();

  await expect(automatiskTag(page, "Arbeidsforhold")).toBeVisible();
});

test("feilet lagring beholder automatisk-taggen", async ({ page }) => {
  await openVilkårsvurdering(page, "2002");
  const item = vilkårItem(page, "Arbeidsforhold");

  await item.getByRole("button", { name: "Endre" }).click();
  await item.getByLabel("Begrunnelse").fill("<ugyldig>");
  await item.getByRole("button", { name: "Oppfylt", exact: true }).click();
  await expect(item.getByText(/inneholder < eller >/)).toBeVisible();

  await item.getByRole("button", { name: "Avbryt" }).click();
  await expect(automatiskTag(page, "Arbeidsforhold")).toBeVisible();
});

test("manuelt vurderte vilkår får ikke automatisk-taggen", async ({ page }) => {
  await openVilkårsvurdering(page, "2018");

  await expect(vilkårItem(page, "Arbeidsforhold").getByText(/Vurdert av/)).toBeVisible();
  await expect(page.getByText("Automatisk", { exact: true })).toHaveCount(0);
});
