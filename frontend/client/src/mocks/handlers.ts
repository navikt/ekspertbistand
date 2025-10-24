import { http, HttpResponse } from "msw";
import { createEmptyInputs, mergeInputs, type Inputs } from "../pages/types";

const virksomheter = [
  {
    organisasjonsnummer: "123456789",
    navn: "Eksempel Bedrift AS",
    underenheter: [
      { organisasjonsnummer: "123456780", navn: "Eksempel Bedrift AS Avd. Oslo", underenheter: [] },
      {
        organisasjonsnummer: "123456781",
        navn: "Eksempel Bedrift AS Avd. Bergen",
        underenheter: [],
      },
    ],
  },
  {
    organisasjonsnummer: "987654321",
    navn: "Testfirma Norge AS",
    underenheter: [],
  },
  {
    organisasjonsnummer: "111222333",
    navn: "Demo Solutions AS",
    underenheter: [],
  },
  {
    organisasjonsnummer: "444555666",
    navn: "Navn & Co AS",
    underenheter: [],
  },
];

const DRAFT_CACHE_NAME = "mock-soknad-draft";
const DRAFT_CACHE_URL = "/mock/soknad/draft";

let draft: Inputs | null = null;

const loadDraft = async (): Promise<Inputs | null> => {
  if (typeof caches === "undefined") return draft;

  try {
    const cache = await caches.open(DRAFT_CACHE_NAME);
    const cached = await cache.match(DRAFT_CACHE_URL);
    if (!cached) return draft;
    draft = (await cached.json()) as Inputs;
  } catch {
    // Ignore cache errors; rely on in-memory data.
  }
  return draft;
};

const persistDraftLocal = async (value: Inputs | null) => {
  draft = value;
  if (typeof caches === "undefined") return;

  try {
    const cache = await caches.open(DRAFT_CACHE_NAME);
    if (value) {
      await cache.put(
        DRAFT_CACHE_URL,
        new Response(JSON.stringify(value), {
          headers: { "Content-Type": "application/json" },
        })
      );
    } else {
      await cache.delete(DRAFT_CACHE_URL);
    }
  } catch {
    // Ignore cache errors; in-memory data already updated.
  }
};

type SkjemaStatus = "utkast" | "innsendt";

type MockSkjema = {
  id: string;
  status: SkjemaStatus;
  data: Inputs;
  opprettetAv: string;
  opprettetTidspunkt: string;
  innsendtTidspunkt?: string;
};

const MOCK_BRUKER = "01020312345";
const skjemaStore = new Map<string, MockSkjema>();

const randomId = () => crypto.randomUUID();
const nowIso = () => new Date().toISOString();
const deepCopy = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

const SKJEMA_CACHE_NAME = "mock-skjema-v1-store";
const SKJEMA_CACHE_URL = "/mock/skjema/store";

const loadSkjemaStore = async () => {
  if (typeof caches === "undefined") return;
  try {
    const cache = await caches.open(SKJEMA_CACHE_NAME);
    const cached = await cache.match(SKJEMA_CACHE_URL);
    if (!cached) return;
    const payload = (await cached.json()) as MockSkjema[];
    skjemaStore.clear();
    for (const entry of payload) {
      skjemaStore.set(entry.id, entry);
    }
  } catch {
    // Ignore cache errors; fall back to in-memory store.
  }
};

const persistSkjemaStore = async () => {
  if (typeof caches === "undefined") return;
  try {
    const cache = await caches.open(SKJEMA_CACHE_NAME);
    await cache.put(
      SKJEMA_CACHE_URL,
      new Response(JSON.stringify(Array.from(skjemaStore.values())), {
        headers: { "Content-Type": "application/json" },
      })
    );
  } catch {
    // Ignore persistence errors.
  }
};

let skjemaStoreLoaded = false;
const ensureSkjemaStoreLoaded = async () => {
  if (skjemaStoreLoaded) return;
  await loadSkjemaStore();
  skjemaStoreLoaded = true;
};

const toUtkastDto = (entry: MockSkjema) => ({
  id: entry.id,
  status: "utkast" as const,
  virksomhet: deepCopy(entry.data.virksomhet),
  ansatt: deepCopy(entry.data.ansatt),
  ekspert: deepCopy(entry.data.ekspert),
  behovForBistand: deepCopy(entry.data.behovForBistand),
  opprettetAv: entry.opprettetAv,
  opprettetTidspunkt: entry.opprettetTidspunkt,
});

const toSkjemaDto = (entry: MockSkjema) => ({
  id: entry.id,
  status: "innsendt" as const,
  virksomhet: deepCopy(entry.data.virksomhet),
  ansatt: deepCopy(entry.data.ansatt),
  ekspert: deepCopy(entry.data.ekspert),
  behovForBistand: deepCopy(entry.data.behovForBistand),
  opprettetAv: entry.opprettetAv,
  opprettetTidspunkt: entry.opprettetTidspunkt,
  innsendtTidspunkt: entry.innsendtTidspunkt,
});

const getSkjemaStatusParam = (request: Request): SkjemaStatus | null => {
  const url = new URL(request.url);
  const status = (url.searchParams.get("status") ?? "innsendt").toLowerCase();
  if (status === "utkast" || status === "innsendt") {
    return status;
  }
  return null;
};

const loginSessionJson = {
  session: {
    ends_in_seconds: 3600,
  },
  tokens: {
    expire_in_seconds: 3600,
  },
};

export const handlers = [
  http.get("/api/health", () => HttpResponse.json({ status: "ok" })),
  http.get("/api/virksomheter", () => HttpResponse.json({ virksomheter })),
  http.get("/api/soknad/draft", async () => {
    const currentDraft = await loadDraft();
    return HttpResponse.json(currentDraft);
  }),
  http.post("/api/soknad/draft", async ({ request }) => {
    try {
      const payload = (await request.json()) as Inputs;
      await persistDraftLocal(payload);
    } catch {
      await persistDraftLocal(null);
    }
    return HttpResponse.json({ status: "stored" });
  }),
  http.delete("/api/soknad/draft", async () => {
    await persistDraftLocal(null);
    return HttpResponse.json({ status: "deleted" });
  }),
  http.post("/api/skjema/v1", async () => {
    await ensureSkjemaStoreLoaded();
    const id = randomId();
    const now = nowIso();
    const entry: MockSkjema = {
      id,
      status: "utkast",
      data: createEmptyInputs(),
      opprettetAv: MOCK_BRUKER,
      opprettetTidspunkt: now,
    };
    skjemaStore.set(id, entry);
    await persistSkjemaStore();
    return HttpResponse.json(toUtkastDto(entry), { status: 201 });
  }),
  http.get("/api/skjema/v1", async ({ request }) => {
    await ensureSkjemaStoreLoaded();
    const status = getSkjemaStatusParam(request);
    if (!status) {
      return HttpResponse.json(
        { message: "ugyldig parameter status, gyldige verdier er: utkast, innsendt" },
        { status: 400 }
      );
    }
    const results = Array.from(skjemaStore.values())
      .filter((entry) => entry.status === status)
      .sort((a, b) => b.opprettetTidspunkt.localeCompare(a.opprettetTidspunkt))
      .map((entry) => (entry.status === "utkast" ? toUtkastDto(entry) : toSkjemaDto(entry)));
    return HttpResponse.json(results);
  }),
  http.get("/api/skjema/v1/:id", async ({ params }) => {
    await ensureSkjemaStoreLoaded();
    const id = params.id;
    if (!id) {
      return HttpResponse.json({ message: "ugyldig id" }, { status: 400 });
    }
    const entry = skjemaStore.get(id);
    if (!entry) {
      return HttpResponse.json({ message: "skjema ikke funnet" }, { status: 404 });
    }
    return HttpResponse.json(entry.status === "utkast" ? toUtkastDto(entry) : toSkjemaDto(entry));
  }),
  http.patch("/api/skjema/v1/:id", async ({ params, request }) => {
    await ensureSkjemaStoreLoaded();
    const id = params.id;
    if (!id) {
      return HttpResponse.json({ message: "ugyldig id" }, { status: 400 });
    }
    const entry = skjemaStore.get(id);
    if (!entry || entry.status !== "utkast") {
      return HttpResponse.json({ message: "skjema ikke i utkast-status" }, { status: 409 });
    }
    let payload: Partial<Inputs>;
    try {
      payload = (await request.json()) as Partial<Inputs>;
    } catch {
      return HttpResponse.json({ message: "ugyldig payload" }, { status: 400 });
    }
    entry.data = mergeInputs(entry.data as Partial<Inputs>, payload);
    skjemaStore.set(id, entry);
    await persistSkjemaStore();
    return HttpResponse.json(toUtkastDto(entry));
  }),
  http.delete("/api/skjema/v1/:id", async ({ params }) => {
    await ensureSkjemaStoreLoaded();
    const id = params.id;
    if (!id) {
      return HttpResponse.json({ message: "ugyldig id" }, { status: 400 });
    }
    const entry = skjemaStore.get(id);
    if (!entry || entry.status !== "utkast") {
      return HttpResponse.json({ message: "skjema ikke i utkast-status" }, { status: 409 });
    }
    skjemaStore.delete(id);
    await persistSkjemaStore();
    return new HttpResponse(null, { status: 204 });
  }),
  http.put("/api/skjema/v1/:id", async ({ params, request }) => {
    await ensureSkjemaStoreLoaded();
    const id = params.id;
    if (!id) {
      return HttpResponse.json({ message: "ugyldig id" }, { status: 400 });
    }
    const entry = skjemaStore.get(id);
    if (!entry || entry.status !== "utkast") {
      return HttpResponse.json({ message: "skjema ikke i utkast-status" }, { status: 409 });
    }
    let payload: Inputs;
    try {
      payload = (await request.json()) as Inputs;
    } catch {
      return HttpResponse.json({ message: "ugyldig payload" }, { status: 400 });
    }
    entry.data = payload;
    entry.status = "innsendt";
    entry.innsendtTidspunkt = nowIso();
    skjemaStore.set(id, entry);
    await persistSkjemaStore();
    return HttpResponse.json(toSkjemaDto(entry));
  }),
  http.get("https://login.ekstern.dev.nav.no/oauth2/session", () =>
    HttpResponse.json(loginSessionJson)
  ),
];
