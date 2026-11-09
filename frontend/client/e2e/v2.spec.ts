import { tnr } from "@navikt/fnrvalidator";
import { expect, test, type Browser, type Page, type Request } from "@playwright/test";
import { ensureMockServiceWorkerReady } from "./mockApi";

const API_PATH = "/ekspertbistand-backend/api/soknad/v1";

const createTestTnr = (): string => {
  // T-numbers are test identifiers; do not put a real fødselsnummer in browser-test data.
  for (let individualNumber = 500; individualNumber < 1000; individualNumber += 1) {
    for (let checksum = 0; checksum < 100; checksum += 1) {
      const candidate = `410100${individualNumber}${String(checksum).padStart(2, "0")}`;
      if (tnr(candidate).status === "valid") return candidate;
    }
  }
  throw new Error("Could not generate a valid test T-number.");
};

const createFormInputs = () => {
  const startdato = new Date();
  startdato.setDate(startdato.getDate() + 7);
  return {
    virksomhet: {
      virksomhetsnummer: "123456789",
      virksomhetsnavn: "Eksempel Bedrift AS",
      beliggenhetsadresse: "Testveien 1, 0557 Oslo",
      kontaktperson: {
        navn: "Lise Kontakt",
        epost: "lise@example.no",
        telefonnummer: "99112233",
      },
    },
    ansatt: { fnr: createTestTnr(), navn: "Testperson" },
    ekspert: {
      navn: "Dr. Ekspert",
      virksomhet: "Ekspert & Co AS",
      virksomhetNavn: "Ekspert & Co AS",
      virksomhetOrgnr: "910825226",
      godkjentUtdanningEllerAutorisasjon: ["Psykolog"],
      relevantKompetanse: ["Tilrettelegging på arbeidsplassen"],
    },
    behovForBistand: {
      begrunnelse: "Behov for tilrettelegging.",
      behov: "Kartlegging av arbeidssituasjonen.",
      timer: "20",
      estimertKostnad: "20000",
      tilrettelegging: "Tilrettelegging av arbeidsoppgaver.",
      startdato: startdato.toISOString().slice(0, 10),
    },
    nav: { kontaktperson: "Nav Kontakt" },
  };
};

// Navnet mocken for valider-deltaker setter på deltakeren.
const MOCK_DELTAKER_NAVN = "Mona Moonlight";
const MOCK_UNDERENHET = "123456780";

const isDraftApiRequest = (request: Request, draftId: string, method: string) =>
  request.method() === method && new URL(request.url()).pathname === `${API_PATH}/${draftId}`;

const openOnHost = async (browser: Browser, hostname: string) => {
  const context = await browser.newContext();
  await context.route(`http://${hostname}:5173/**`, (route) => {
    const localUrl = new URL(route.request().url());
    localUrl.hostname = "localhost";
    return route.continue({ url: localUrl.toString() });
  });
  await context.route("https://telemetry.nav.no/**", (route) => route.fulfill({ status: 204 }));
  await context.route("**/oauth2/session", (route) =>
    route.fulfill({ json: { session: { ends_in_seconds: 3600 } } })
  );
  await context.route(
    (url) => url.pathname === "/ekspertbistand-backend/api/organisasjoner/v1",
    (route) =>
      route.fulfill({
        json: {
          hierarki: [
            {
              orgnr: "123456789",
              navn: "Eksempel Bedrift AS",
              underenheter: [{ orgnr: "123456780", navn: "Eksempel Bedrift AS Avd. Oslo" }],
            },
          ],
        },
      })
  );
  await context.route(
    (url) => url.pathname === API_PATH,
    (route) => route.fulfill({ json: [] })
  );

  const page = await context.newPage();
  await page.goto(`http://${hostname}:5173/soknader`);
  return { context, page };
};

const findApprovedDraftId = async (page: Page) =>
  page.evaluate(async (apiPath) => {
    const response = await fetch(`${apiPath}?status=innsendt`);
    if (!response.ok)
      throw new Error(`Could not load submitted mock applications (${response.status}).`);
    const applications = (await response.json()) as Array<{
      id?: string;
      beslutning?: { status?: string | null } | null;
    }>;
    return (
      applications.find(
        (application) => application.beslutning?.status?.toLowerCase() === "godkjent"
      )?.id ?? null
    );
  }, API_PATH);

test("v2 overview and form use v2 routes while sharing the v1 API", async ({ page }) => {
  const apiRequests: Request[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.includes("/ekspertbistand-backend/api/soknad/")) {
      apiRequests.push(request);
    }
  });

  await page.goto("/soknader");
  await ensureMockServiceWorkerReady(page);
  const v2Entry = page.getByRole("link", { name: "Prøv ny løsning (v2)" });
  await expect(v2Entry).toHaveAttribute("href", "/v2/soknader");
  await v2Entry.click();
  await expect(page).toHaveURL(/\/v2\/soknader$/);
  await expect(
    page.getByRole("link", { name: /^Søknad om tilskudd til ekspertbistand –/ }).first()
  ).toHaveAttribute("href", /^\/v2\/skjema\//);

  const createEntry = page.getByRole("link", {
    name: "Opprett søknad om tilskudd til ekspertbistand",
  });
  await expect(createEntry).toHaveAttribute("href", "/v2/skjema/start");
  await createEntry.click();
  await expect(page).toHaveURL(/\/v2\/skjema\/start$/);
  await expect(
    page.getByRole("heading", {
      name: "Søknad om tilskudd til ekspertbistand",
      level: 1,
      exact: true,
    })
  ).toBeVisible();
  await page.getByRole("checkbox").nth(0).check();
  await page.getByRole("checkbox").nth(1).check();

  const createRequestPromise = page.waitForRequest(
    (request) => request.method() === "POST" && new URL(request.url()).pathname === API_PATH
  );
  await page.getByRole("button", { name: "Start søknad" }).click();
  const createRequest = await createRequestPromise;
  expect(new URL(createRequest.url()).pathname).toBe(API_PATH);
  await expect(page).toHaveURL(/\/v2\/skjema\/[^/]+\/steg-1$/);

  const stepOnePath = new URL(page.url()).pathname;
  const draftId = stepOnePath.match(/\/v2\/skjema\/([^/]+)\/steg-1$/)?.[1];
  expect(draftId).toBeTruthy();
  if (!draftId) throw new Error("The v2 create-draft route did not contain a draft id.");

  const seedStatuses = await page.evaluate(
    async ({ id, inputs, virksomhetsnummer }) => {
      const patch = await fetch(`/ekspertbistand-backend/api/soknad/v1/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(inputs),
      });
      // PATCH setter ikke deltaker eller virksomhet. Det gjør bare valider-deltaker.
      const validering = await fetch(
        `/ekspertbistand-backend/api/soknad/v1/${id}/valider-deltaker`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ fnr: inputs.ansatt.fnr, virksomhetsnummer }),
        }
      );
      return [patch.status, validering.status];
    },
    { id: draftId, inputs: createFormInputs(), virksomhetsnummer: MOCK_UNDERENHET }
  );
  expect(seedStatuses).toEqual([200, 200]);

  await page.goto(`/v2/skjema/${draftId}`);
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${draftId}/steg-1$`));
  await expect(page.getByRole("heading", { name: "Søknadsskjema – ekspertbistand" })).toBeVisible();

  await page.goto("/v2/soknader");
  await ensureMockServiceWorkerReady(page);
  const v2DraftLink = page.getByRole("link", { name: new RegExp(MOCK_DELTAKER_NAVN) });
  await expect(v2DraftLink).toHaveAttribute("href", `/v2/skjema/${draftId}`);
  await v2DraftLink.click();
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${draftId}/steg-1$`));
  await expect(page.getByRole("heading", { name: "Søknadsskjema – ekspertbistand" })).toBeVisible();

  await page.goto("/soknader");
  const v1DraftLink = page.getByRole("link", {
    name: `Søknad om tilskudd til ekspertbistand – ${MOCK_DELTAKER_NAVN}`,
  });
  await expect(v1DraftLink).toHaveAttribute("href", `/skjema/${draftId}`);
  await v1DraftLink.click();
  await expect(page).toHaveURL(new RegExp(`/skjema/${draftId}/steg-1$`));
  await expect(page.getByRole("heading", { name: "Søknadsskjema – ekspertbistand" })).toBeVisible();

  await page.goto(`/v2/skjema/${draftId}/oppsummering`);
  await expect(
    page.getByRole("heading", { name: "Oppsummering av søknad om ekspertbistand" })
  ).toBeVisible();
  await page.getByRole("link", { name: /Endre/ }).first().click();
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${draftId}/steg-1$`));
  await page.getByRole("button", { name: "Neste steg" }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${draftId}/steg-2$`));

  const patchResponsePromise = page.waitForResponse((response) =>
    isDraftApiRequest(response.request(), draftId, "PATCH")
  );
  await page.locator('[name="virksomhet.kontaktperson.navn"]').fill("Oppdatert kontakt");
  const patchResponse = await patchResponsePromise;
  expect(patchResponse.ok()).toBe(true);

  await page.getByRole("button", { name: "Neste steg" }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${draftId}/steg-3$`));
  await expect(page.getByRole("link", { name: "Forrige steg" })).toHaveAttribute(
    "href",
    `/v2/skjema/${draftId}/steg-2`
  );
  await expect(page.getByRole("heading", { name: "Behov for bistand" })).toBeVisible();
  await expect(page.getByText(/Maksgrensen er/)).toBeVisible();
  await page.getByRole("button", { name: "Neste steg" }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${draftId}/oppsummering$`));
  await expect(page.getByRole("link", { name: "Forrige steg" })).toHaveAttribute(
    "href",
    `/v2/skjema/${draftId}/steg-3`
  );
  await expect(page.getByText("Oppdatert kontakt")).toBeVisible();

  const submitRequestPromise = page.waitForRequest((request) =>
    isDraftApiRequest(request, draftId, "PUT")
  );
  const submitResponsePromise = page.waitForResponse((response) =>
    isDraftApiRequest(response.request(), draftId, "PUT")
  );
  await page.getByRole("button", { name: "Send søknad" }).click();
  const submitRequest = await submitRequestPromise;
  const submitResponse = await submitResponsePromise;
  expect(submitResponse.ok()).toBe(true);
  expect(submitRequest.postDataJSON()).toMatchObject({
    id: draftId,
    virksomhet: { kontaktperson: { navn: "Oppdatert kontakt" } },
    behovForBistand: { estimertKostnad: "20000" },
  });
  expect(apiRequests.length).toBeGreaterThan(0);
  const usesOnlyV1Api = apiRequests.every((request) =>
    new URL(request.url()).pathname.startsWith(API_PATH)
  );
  expect(usesOnlyV1Api).toBe(true);
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${draftId}/kvittering$`));
  await expect(page.getByRole("heading", { name: "Nav har mottatt søknaden" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Tilbake til oversikt" }).first()).toHaveAttribute(
    "href",
    "/v2/soknader"
  );

  await page.goto(`/v2/skjema/${draftId}/steg-1`);
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${draftId}/kvittering$`));

  await page.goto("/v2/soknader");
  const v2ReceiptLink = page.getByRole("link", { name: new RegExp(MOCK_DELTAKER_NAVN) });
  await expect(v2ReceiptLink).toHaveAttribute("href", `/v2/skjema/${draftId}/kvittering`);

  await page.goto("/soknader");
  const v1ReceiptLink = page.getByRole("link", { name: new RegExp(MOCK_DELTAKER_NAVN) });
  await expect(v1ReceiptLink).toHaveAttribute("href", `/skjema/${draftId}/kvittering`);
});

test("v2 receipt preserves external actions and its direct pages return to v2 routes", async ({
  page,
}) => {
  await page.goto("/v2/soknader");
  await ensureMockServiceWorkerReady(page);
  await expect(page.locator('a[href*="/refusjon"], a[href*="/sluttrapport"]')).toHaveCount(0);

  const approvedDraftId = await findApprovedDraftId(page);
  expect(approvedDraftId).toBeTruthy();
  if (!approvedDraftId) throw new Error("The mock API did not include an approved application.");

  await page.goto(`/v2/skjema/${approvedDraftId}/kvittering`);
  await expect(page.getByRole("heading", { name: "Søknaden godkjent" })).toBeVisible();
  await expect(page.getByRole("link", { name: /Søk refusjon/ })).toHaveAttribute(
    "href",
    "https://www.nav.no/fyllut/nav761390?sub=paper"
  );
  await expect(page.getByRole("link", { name: "Førsteside til sluttrapport" })).toHaveAttribute(
    "href",
    "https://www.nav.no/fyllut-ettersending/lospost?tema=OPP&sub=paper"
  );
  await expect(page.locator('a[href*="/v2/skjema/"][href*="/refusjon"]')).toHaveCount(0);
  await expect(page.locator('a[href*="/v2/skjema/"][href*="/sluttrapport"]')).toHaveCount(0);

  await page.goto("/v2/tilskuddsbrev/2024-123-1");
  await expect(page.getByRole("heading", { name: "Tilskuddsbrev 2024-123-1" })).toBeVisible();
  await page.getByRole("link", { name: "Tilbake til oversikt" }).first().click();
  await expect(page).toHaveURL(/\/v2\/soknader$/);

  await page.goto(`/v2/skjema/${approvedDraftId}/sluttrapport`);
  await expect(page.getByRole("heading", { name: "Send inn sluttrapport" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Tilbake til avtalen" })).toHaveAttribute(
    "href",
    `/v2/skjema/${approvedDraftId}/kvittering`
  );
  await page.getByRole("link", { name: "Tilbake til avtalen" }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${approvedDraftId}/kvittering$`));

  await page.goto(`/v2/skjema/${approvedDraftId}/refusjon`);
  await expect(page.getByRole("heading", { name: "Søk refusjon" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Tilbake til avtalen" })).toHaveAttribute(
    "href",
    `/v2/skjema/${approvedDraftId}/kvittering`
  );
  await page.getByRole("link", { name: "Tilbake til avtalen" }).click();
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${approvedDraftId}/kvittering$`));
});

test("v2 refunds and final reports submit to the shared API and return to v2", async ({ page }) => {
  await page.goto("/v2/soknader");
  await ensureMockServiceWorkerReady(page);
  const approvedDraftId = await findApprovedDraftId(page);
  if (!approvedDraftId) throw new Error("The mock API did not include an approved application.");

  await page.goto(`/v2/skjema/${approvedDraftId}/refusjon`);
  await expect(
    page.getByLabel("Hvilke utgifter skal tilskuddet til ekspertbistand dekke?")
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Send inn", exact: true }).click();
  await expect(page.getByText("Du må oppgi beløp for refusjonskravet.").first()).toBeVisible();
  await expect(page.getByText("Du må bekrefte at utgiftene er betalt.").first()).toBeVisible();
  await expect(page.getByText("Du må laste opp minst én fil.").first()).toBeVisible();
  await page.getByLabel("Beløp for refusjonskravet").fill("1000");
  await page.locator('input[type="file"]').setInputFiles({
    name: "kvittering.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\n%%EOF\n"),
  });
  await page
    .getByRole("checkbox", { name: "Jeg bekrefter at utgiftene som kreves refundert er betalt." })
    .check();
  await page
    .getByRole("checkbox", { name: "Jeg bekrefter at utgiftene som kreves refundert er betalt." })
    .press("Tab");
  await expect(page.getByText("Du må bekrefte at utgiftene er betalt.")).toHaveCount(0);

  const refundResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      new URL(response.url()).pathname === `${API_PATH}/${approvedDraftId}/refusjon`
  );
  const refundStatusResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "GET" &&
      response.status() === 200 &&
      new URL(response.url()).pathname === `${API_PATH}/${approvedDraftId}/refusjon`
  );
  await page.getByRole("button", { name: "Send inn", exact: true }).click();
  const submittedRefund = await refundResponse;
  expect(submittedRefund.status()).toBe(201);
  const refundStatus: unknown = await (await refundStatusResponse).json();
  expect(refundStatus).toMatchObject({
    belopKroner: 1000,
    vedlegg: [expect.objectContaining({ filnavn: "kvittering.pdf" })],
  });
  expect(refundStatus).not.toHaveProperty("utgifter");
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${approvedDraftId}/kvittering$`));
  await expect(page.getByRole("heading", { name: "Søknaden godkjent", exact: true })).toBeVisible();

  // The local mock keeps refund and report status in memory, so return through SPA history.
  await page.goBack();
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${approvedDraftId}/refusjon$`));
  await expect(page.getByRole("heading", { name: "Refusjonskrav sendt inn" })).toBeVisible();
  await expect(page.getByText("Hvilke utgifter skal tilskuddet dekke")).toHaveCount(0);
  await expect(page.getByText("1 000 kr", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "kvittering.pdf" })).toHaveAttribute(
    "href",
    new RegExp(`${API_PATH}/${approvedDraftId}/refusjon/vedlegg/`)
  );

  await page.goto(`/v2/skjema/${approvedDraftId}/sluttrapport`);
  await page.locator('input[type="file"]').setInputFiles({
    name: "sluttrapport.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\n%%EOF\n"),
  });
  const reportResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      new URL(response.url()).pathname === `${API_PATH}/${approvedDraftId}/sluttrapport`
  );
  await page.getByRole("button", { name: "Send inn", exact: true }).click();
  expect((await reportResponse).status()).toBe(201);
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${approvedDraftId}/kvittering$`));
  await expect(page.getByRole("heading", { name: "Søknaden godkjent", exact: true })).toBeVisible();

  await page.goBack();
  await expect(page).toHaveURL(new RegExp(`/v2/skjema/${approvedDraftId}/sluttrapport$`));
  await expect(page.getByText("Sluttrapport sendt inn", { exact: true })).toBeVisible();
  await expect(page.getByText("sluttrapport.pdf", { exact: true })).toBeVisible();
});

test("production keeps v1 available and rejects every v2 route", async ({ browser }) => {
  test.setTimeout(60_000);
  const v2Routes = [
    "/v2",
    "/v2/soknader",
    "/v2/skjema/start",
    "/v2/skjema/test-id",
    "/v2/skjema/test-id/steg-1",
    "/v2/skjema/test-id/steg-2",
    "/v2/skjema/test-id/steg-3",
    "/v2/skjema/test-id/oppsummering",
    "/v2/skjema/test-id/kvittering",
    "/v2/tilskuddsbrev/2024-123-1",
    "/v2/skjema/test-id/refusjon",
    "/v2/skjema/test-id/sluttrapport",
  ];

  const hostname = "arbeidsgiver.nav.no";
  const { context, page } = await openOnHost(browser, hostname);
  try {
    await expect(page.getByRole("heading", { name: "Søknader" })).toBeVisible();
    await expect(page.getByText("Prøv ny løsning (v2)", { exact: true })).toHaveCount(0);
    await expect(
      page.getByRole("link", { name: "Opprett søknad om tilskudd til ekspertbistand" })
    ).toBeVisible();

    await page.goto(`http://${hostname}:5173/skjema/start`);
    await expect(
      page.getByRole("heading", {
        name: "Søknad om tilskudd til ekspertbistand",
        level: 1,
        exact: true,
      })
    ).toBeVisible();

    for (const path of v2Routes) {
      await page.goto(`http://${hostname}:5173${path}`);
      await expect(page).toHaveURL((url) => url.pathname === "/soknader");
    }
  } finally {
    await context.close();
  }
});

test("an invalid v2 draft returns to the v2 overview", async ({ page }) => {
  await page.goto("/v2/skjema/missing-draft/steg-1");
  await ensureMockServiceWorkerReady(page);
  await expect(page.getByRole("heading", { name: "Søknaden finnes ikke" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Gå til oversikt" })).toHaveAttribute(
    "href",
    "/v2/soknader"
  );
  await page.getByRole("button", { name: "Gå til søknadsoversikten" }).click();
  await expect(page).toHaveURL(/\/v2\/soknader$/);
});
