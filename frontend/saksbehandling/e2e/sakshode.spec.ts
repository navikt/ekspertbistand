import { expect, test, type Page } from "@playwright/test";
import { formatFodselsnummer, formatOrganisasjonsnummer } from "../src/utils/formatNummer";
import { openMockedPage } from "./mock";

const sakUrl = (nummer: string) => `/oversikt/9c2f7a10-0000-4000-8000-00000000${nummer}`;

async function openCase(page: Page, nummer: string) {
  await openMockedPage(page, sakUrl(nummer));
  const sakshode = page.getByRole("region", { name: "Sakens parter" });
  await expect(sakshode).toBeVisible();
  return sakshode;
}

test("nummerformatering beholder ledende nuller", () => {
  expect(formatOrganisasjonsnummer("012345678")).toBe("012 345 678");
  expect(formatFodselsnummer("01019000000")).toBe("010190 00000");
});

test("nummerformatering endrer ikke verdier med uventet format", () => {
  expect(formatOrganisasjonsnummer("ukjent")).toBe("ukjent");
  expect(formatFodselsnummer("")).toBe("");
});

test("viser og kopierer arbeidsgiver og deltaker separat med tastaturet", async ({
  page,
  context,
}) => {
  await context.grantPermissions(["clipboard-read", "clipboard-write"]);
  const sakshode = await openCase(page, "2010");

  await expect(sakshode.getByText("Nordlys transport AS", { exact: true })).toBeVisible();
  await expect(sakshode.getByText("876 543 219", { exact: true })).toBeVisible();
  await expect(sakshode.getByRole("heading", { level: 1, name: "Kari Kalkun" })).toBeVisible();
  await expect(sakshode.getByText("120184 34566", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
  await expect(sakshode.getByText(/fortrolig adresse|Egen ansatt|år|Reservert i KRR/)).toHaveCount(
    0
  );

  const kopieringer = [
    ["Kopier virksomhetsnavn", "Nordlys transport AS", "Virksomhetsnavnet er kopiert"],
    ["Kopier organisasjonsnummer", "876543219", "Organisasjonsnummeret er kopiert"],
    ["Kopier deltakernavn", "Kari Kalkun", "Deltakernavnet er kopiert"],
    ["Kopier fødselsnummer", "12018434566", "Fødselsnummeret er kopiert"],
  ];
  const knapper = sakshode.getByRole("button");
  await expect(knapper).toHaveCount(4);
  await knapper.first().focus();
  for (const [index, [navn, verdi, bekreftelse]] of kopieringer.entries()) {
    const knapp = knapper.nth(index);
    await expect(knapp).toHaveAccessibleName(navn);
    await expect(knapp).toBeFocused();
    await page.keyboard.press(index % 2 === 0 ? "Enter" : "Space");
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(verdi);
    await expect(knapp).toHaveAccessibleName(bekreftelse);
    await page.keyboard.press("Tab");
  }
});

test("viser arbeidsgiver og deltaker på Arena-saker", async ({ page }) => {
  const sakshode = await openCase(page, "2011");

  await expect(sakshode.getByText("Elvebredden snekkerverksted", { exact: true })).toBeVisible();
  await expect(sakshode.getByRole("heading", { name: "Trond Trestamme" })).toBeVisible();
  await expect(sakshode.getByRole("button")).toHaveCount(4);
  await expect(page.getByRole("tab", { name: "Vilkårsvurdering" })).toHaveCount(0);
});

for (const { nummer, tekst, farge } of [
  { nummer: "2023", tekst: "Strengt fortrolig adresse", farge: "danger" },
  { nummer: "2024", tekst: "Fortrolig adresse", farge: "warning" },
]) {
  test(`viser ${tekst.toLowerCase()} med omriss i sakshodet`, async ({ page }) => {
    const sakshode = await openCase(page, nummer);
    const tag = sakshode.getByText(tekst, { exact: true });

    await expect(tag).toBeVisible();
    await expect(tag).toHaveAttribute("data-variant", "outline");
    await expect(tag).toHaveAttribute("data-color", farge);
  });
}

test("beholder merking av egne ansatte", async ({ page }) => {
  const sakshode = await openCase(page, "2019");
  await expect(sakshode.getByText("Egen ansatt", { exact: true })).toBeVisible();
});

test("sakslisten beholder fylt graderingstag", async ({ page }) => {
  await openMockedPage(page, "/oversikt");
  await page.getByRole("combobox", { name: "Enhet" }).fill("Nav hjelpemiddelsentral Agder");
  await page.getByRole("option", { name: "Nav hjelpemiddelsentral Agder" }).click();

  const tag = page
    .getByRole("table")
    .getByText("Strengt fortrolig adresse", { exact: true })
    .first();
  await expect(tag).toBeVisible();
  await expect(tag).toHaveAttribute("data-variant", "strong");
});

test("beholder sakshodet ved fanebytte", async ({ page }) => {
  const sakshode = await openCase(page, "2010");

  for (const fane of ["Sluttrapport", "Refusjon", "Søknad", "Foreløpig vedtak"]) {
    await page.getByRole("tab", { name: fane, exact: true }).click();
    await expect(sakshode.getByText("Nordlys transport AS", { exact: true })).toBeVisible();
    await expect(sakshode.getByRole("heading", { name: "Kari Kalkun" })).toBeVisible();
  }
});

test("beholder sakshodet på kvitteringen etter innsending til beslutter", async ({ page }) => {
  const sakshode = await openCase(page, "2010");
  await page.getByRole("tab", { name: "Foreløpig vedtak", exact: true }).click();
  await page.getByRole("radio", { name: "Avslå", exact: true }).check();
  await page
    .getByRole("textbox", { name: "Begrunnelse til deltaker", exact: true })
    .fill("Testavslag");
  await page
    .getByRole("textbox", { name: "Begrunnelse til arbeidsgiver", exact: true })
    .fill("Testavslag");
  await page.getByRole("button", { name: "Forhåndsvis foreløpig vedtak", exact: true }).click();
  await page.getByRole("button", { name: "Send til beslutter", exact: true }).click();

  await expect(
    page.getByRole("heading", { name: "Foreløpig vedtak sendt til beslutter" })
  ).toBeVisible();
  await expect(sakshode.getByText("Nordlys transport AS", { exact: true })).toBeVisible();
  await expect(sakshode.getByRole("heading", { name: "Kari Kalkun" })).toBeVisible();
});

for (const bredde of [320, 1280, 1920]) {
  test(`viser lange navn og alle kopierknapper uten overløp ved ${bredde}px`, async ({ page }) => {
    await page.setViewportSize({ width: bredde, height: 900 });
    const sakshode = await openCase(page, "2001");
    await page.evaluate(() => document.fonts.ready);

    await expect(
      sakshode.getByText("Lomma kommune Måsen omsorgsbolig", { exact: true })
    ).toBeVisible();
    await expect(sakshode.getByRole("heading", { name: "Mona Moonlight" })).toBeVisible();
    expect(await sakshode.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(
      true
    );

    for (const knapp of await sakshode.getByRole("button").all()) {
      await expect(knapp).toBeVisible();
      const boks = await knapp.boundingBox();
      if (!boks) throw new Error("Kopierknappen mangler synlig utstrekning");
      expect(boks.x).toBeGreaterThanOrEqual(0);
      expect(boks.x + boks.width).toBeLessThanOrEqual(bredde);
    }

    if (bredde === 1920) {
      await page.screenshot({ path: test.info().outputPath("sakshode-desktop.png") });
    }
    if (bredde === 320) {
      await page.screenshot({ path: test.info().outputPath("sakshode-mobil.png") });
    }
  });
}

for (const { nummer, feilmelding } of [
  { nummer: "2022", feilmelding: "Du har ikke tilgang til denne saken." },
  { nummer: "2999", feilmelding: "Fant ikke saken." },
]) {
  test(`viser ikke sakens parter når saken feiler (${nummer})`, async ({ page }) => {
    await openMockedPage(page, "/oversikt");
    await page.goto(sakUrl(nummer));

    await expect(page.getByRole("alert")).toContainText(feilmelding);
    await expect(page.getByRole("region", { name: "Sakens parter" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: /Kopier/ })).toHaveCount(0);
  });
}
