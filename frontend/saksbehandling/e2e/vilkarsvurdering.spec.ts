import { expect, test, type Page } from "@playwright/test";
import type { SakDetaljer } from "../src/hooks/useSak";
import { openMockedPage } from "./mock";

const sakId = (nummer: string) => `9c2f7a10-0000-4000-8000-00000000${nummer}`;
const sakUrl = (nummer: string) => `/oversikt/${sakId(nummer)}`;

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

const redigeringsknapper = (page: Page) =>
  page.getByRole("tabpanel").getByRole("button", {
    name: /^(Endre|Vurder|Oppfylt|Ikke oppfylt|Avbryt)$/,
  });

async function endreSak(page: Page, nummer: string, endring: Partial<SakDetaljer>) {
  await page.evaluate(
    async ({ sakId, endring }) => {
      const modul = "/e2e/sakstilstand.ts";
      const { endreSak } = (await import(/* @vite-ignore */ modul)) as {
        endreSak: (sakId: string, endring: object) => Promise<void>;
      };
      await endreSak(sakId, endring);
    },
    { sakId: sakId(nummer), endring }
  );
}

test.describe("låst vilkårsvurdering", () => {
  for (const [nummer, beskrivelse] of [
    ["2001", "ny sak uten saksbehandler"],
    ["2003", "egen sak til beslutning"],
    ["2004", "kollegas sak under behandling"],
    ["2006", "innvilget sak"],
    ["2007", "avslått sak"],
    ["2014", "sak der jeg er beslutter"],
    ["2017", "sak til beslutning der jeg er både saksbehandler og beslutter"],
  ]) {
    test(`${beskrivelse} viser vurderingene uten redigeringshandlinger`, async ({ page }) => {
      await openVilkårsvurdering(page, nummer);

      await expect(vilkårItem(page, "Arbeidsforhold")).toBeVisible();
      await expect(
        vilkårItem(page, "Arbeidsforhold").getByText("Oppfylt", { exact: true })
      ).toBeVisible();
      await expect(redigeringsknapper(page)).toHaveCount(0);
      await expect(page.getByRole("textbox", { name: "Begrunnelse" })).toHaveCount(0);
    });
  }

  test("ikke vurderte vilkår vises som «Ikke vurdert»", async ({ page }) => {
    await openVilkårsvurdering(page, "2003");

    await expect(
      vilkårItem(page, "Prøvd tilrettelegging").getByText("Ikke vurdert", { exact: true })
    ).toBeVisible();
  });

  test("lagrede vurderinger og metadata vises i låst visning", async ({ page }) => {
    await openVilkårsvurdering(page, "2018");
    await endreSak(page, "2018", { status: "TIL_BESLUTNING", saksbehandlerIdent: "H654321" });

    const item = vilkårItem(page, "Arbeidsforhold");
    await expect(item.getByText("Oppfylt", { exact: true })).toBeVisible();
    await expect(item.getByText(/Vurdert av S112233/)).toBeVisible();
    await expect(redigeringsknapper(page)).toHaveCount(0);
  });

  test("beslutter kan fortsatt åpne og lukke vilkårene", async ({ page }) => {
    await openVilkårsvurdering(page, "2014");
    const header = page.getByRole("button", { name: "Arbeidsforhold", exact: true });

    await header.click();
    await expect(header).toHaveAttribute("aria-expanded", "false");
    await header.click();
    await expect(header).toHaveAttribute("aria-expanded", "true");
  });

  test("tildeling som beslutter gir ikke redigeringstilgang", async ({ page }) => {
    await openVilkårsvurdering(page, "2012");
    await page.getByRole("button", { name: "Tildel meg" }).click();

    await expect(page.getByRole("button", { name: "Tildel meg" })).toHaveCount(0);
    await expect(redigeringsknapper(page)).toHaveCount(0);
  });

  test("overtakelse som saksbehandler gir redigeringstilgang", async ({ page }) => {
    await openVilkårsvurdering(page, "2004");
    await expect(redigeringsknapper(page)).toHaveCount(0);

    await page.getByRole("button", { name: "Meny" }).click();
    await page.getByRole("menuitem", { name: "Overta som saksbehandler" }).click();

    await expect(
      vilkårItem(page, "Arbeidsforhold").getByRole("button", { name: "Endre" })
    ).toBeVisible();
  });
});

test.describe("låsing under redigering", () => {
  for (const [beskrivelse, endring] of [
    ["status endres til beslutning", { status: "TIL_BESLUTNING" }],
    ["saken tildeles en annen", { saksbehandlerIdent: "H654321" }],
  ] as const) {
    test(`åpen redigering forkastes når ${beskrivelse}`, async ({ page }) => {
      await openVilkårsvurdering(page, "2018");
      const item = vilkårItem(page, "Arbeidsforhold");
      await item.getByRole("button", { name: "Endre" }).click();
      await item.getByLabel("Begrunnelse").fill("Ulagret utkast");

      await endreSak(page, "2018", endring);

      await expect(item.getByLabel("Begrunnelse")).toHaveCount(0);
      await expect(redigeringsknapper(page)).toHaveCount(0);
      await expect(item.getByText("Oppfylt", { exact: true })).toBeVisible();

      await endreSak(page, "2018", { status: "UNDER_BEHANDLING", saksbehandlerIdent: "S112233" });
      await item.getByRole("button", { name: "Endre" }).click();
      await expect(item.getByLabel("Begrunnelse")).not.toHaveValue("Ulagret utkast");
    });
  }
});
