import { http, HttpResponse } from "msw";
import {
  MAX_ESTIMERT_KOSTNAD,
  MIN_ESTIMERT_KOSTNAD,
  createEmptyInputs,
  type SoknadInputs,
} from "../features/soknad/schema";
import { ensureIsoDateString } from "../utils/date";
import type { Organisasjon } from "@navikt/virksomhetsvelger";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";

const organisasjoner: Organisasjon[] = [
  {
    orgnr: "123456789",
    navn: "Eksempel Bedrift AS",
    underenheter: [
      { orgnr: "123456780", navn: "Eksempel Bedrift AS Avd. Oslo", underenheter: [] },
      {
        orgnr: "123456781",
        navn: "Eksempel Bedrift AS Avd. Bergen",
        underenheter: [],
      },
    ],
  },
  {
    orgnr: "987654321",
    navn: "Testfirma Norge AS",
    underenheter: [],
  },
  {
    orgnr: "111222333",
    navn: "Demo Solutions AS",
    underenheter: [],
  },
  {
    orgnr: "444555666",
    navn: "Navn & Co AS",
    underenheter: [],
  },
];

const DRAFT_CACHE_NAME = "mock-soknad-draft";
const DRAFT_CACHE_URL = "/mock/soknad/draft";

let draft: SoknadInputs | null = null;

const loadDraft = async (): Promise<SoknadInputs | null> => {
  if (typeof caches === "undefined") return draft;

  try {
    const cache = await caches.open(DRAFT_CACHE_NAME);
    const cached = await cache.match(DRAFT_CACHE_URL);
    if (!cached) return draft;
    draft = (await cached.json()) as SoknadInputs;
  } catch {
    // Ignore cache errors; rely on in-memory data.
  }
  return draft;
};

const persistDraftLocal = async (value: SoknadInputs | null) => {
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
  data: SoknadInputs;
  opprettetAv: string;
  opprettetTidspunkt: string;
  innsendtTidspunkt?: string;
};

const MOCK_BRUKER = "01020312345";
const skjemaStore = new Map<string, MockSkjema>();

const randomId = () => crypto.randomUUID();
const nowIso = () => new Date().toISOString();
const deepCopy = <T>(value: T): T => {
  if (value === undefined || value === null) {
    return value;
  }
  return JSON.parse(JSON.stringify(value)) as T;
};

// Shallow merge helper that ignores undefined values to keep existing data intact.
const mergeShallow = <T extends Record<string, unknown>>(base: T, update?: Partial<T>): T =>
  ({
    ...base,
    ...Object.fromEntries(Object.entries(update ?? {}).filter(([, val]) => val !== undefined)),
  }) as T;

const mergeInputs = (
  base: Partial<SoknadInputs> | undefined,
  update: Partial<SoknadInputs>
): SoknadInputs => {
  const b = (base as SoknadInputs | undefined) ?? createEmptyInputs();

  const virksomhet = mergeShallow(b.virksomhet, update.virksomhet);
  const kontaktperson = mergeShallow(b.virksomhet.kontaktperson, update.virksomhet?.kontaktperson);
  const behovForBistand = mergeShallow(b.behovForBistand, update.behovForBistand);

  return {
    virksomhet: { ...virksomhet, kontaktperson },
    ansatt: mergeShallow(b.ansatt, update.ansatt),
    ekspert: mergeShallow(b.ekspert, update.ekspert),
    behovForBistand,
    nav: mergeShallow(b.nav, update.nav),
  };
};

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

const toEstimertKostnadNumber = (
  value: SoknadInputs["behovForBistand"]["estimertKostnad"]
): number => {
  const numeric =
    typeof value === "number"
      ? value
      : typeof value === "string"
        ? Number.parseInt(value.trim(), 10)
        : Number.NaN;

  if (!Number.isFinite(numeric)) {
    return MIN_ESTIMERT_KOSTNAD;
  }

  const rounded = Math.round(numeric);
  return Math.min(MAX_ESTIMERT_KOSTNAD, Math.max(MIN_ESTIMERT_KOSTNAD, rounded));
};

const toTimerNumber = (value: SoknadInputs["behovForBistand"]["timer"]): number => {
  const numeric =
    typeof value === "number"
      ? value
      : typeof value === "string"
        ? Number.parseInt(value.trim(), 10)
        : Number.NaN;

  if (!Number.isFinite(numeric)) {
    return 0;
  }

  return Math.max(0, Math.round(numeric));
};

const toStartdatoString = (value: SoknadInputs["behovForBistand"]["startdato"]): string =>
  ensureIsoDateString(typeof value === "string" ? value : null);

const toUtkastDto = (entry: MockSkjema) => {
  const data = entry.data ?? createEmptyInputs();
  return {
    id: entry.id,
    status: "utkast" as const,
    virksomhet: deepCopy(data.virksomhet),
    ansatt: deepCopy(data.ansatt),
    ekspert: deepCopy(data.ekspert),
    behovForBistand: {
      ...deepCopy(data.behovForBistand),
      timer: toTimerNumber(data.behovForBistand.timer),
      estimertKostnad: toEstimertKostnadNumber(data.behovForBistand.estimertKostnad),
      startdato: toStartdatoString(data.behovForBistand.startdato),
    },
    nav: deepCopy(data.nav),
    opprettetAv: entry.opprettetAv,
    opprettetTidspunkt: entry.opprettetTidspunkt,
  };
};

const toSkjemaDto = (entry: MockSkjema) => {
  const data = entry.data ?? createEmptyInputs();
  return {
    id: entry.id,
    status: "innsendt" as const,
    virksomhet: deepCopy(data.virksomhet),
    ansatt: deepCopy(data.ansatt),
    ekspert: deepCopy(data.ekspert),
    behovForBistand: {
      ...deepCopy(data.behovForBistand),
      timer: toTimerNumber(data.behovForBistand.timer),
      estimertKostnad: toEstimertKostnadNumber(data.behovForBistand.estimertKostnad),
      startdato: toStartdatoString(data.behovForBistand.startdato),
    },
    nav: deepCopy(data.nav),
    opprettetAv: entry.opprettetAv,
    opprettetTidspunkt: entry.opprettetTidspunkt,
    innsendtTidspunkt: entry.innsendtTidspunkt,
  };
};

const getSkjemaStatusParam = (request: Request): SkjemaStatus | null => {
  const url = new URL(request.url);
  const status = (url.searchParams.get("status") ?? "innsendt").toLowerCase();
  if (status === "utkast" || status === "innsendt") {
    return status;
  }
  return null;
};

const getParamValue = (value: string | readonly string[] | undefined): string | null => {
  if (!value) return null;
  return typeof value === "string" ? value : (value[0] ?? null);
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
  http.get("/internal/isAlive", () => HttpResponse.json({ status: "ok" })),
  http.get("/ekspertbistand-backend/api/organisasjoner/v1", () =>
    HttpResponse.json({ hierarki: organisasjoner })
  ),
  http.get("/api/soknad/draft", async () => {
    const currentDraft = await loadDraft();
    return HttpResponse.json(currentDraft);
  }),
  http.post("/api/soknad/draft", async ({ request }) => {
    try {
      const payload = (await request.json()) as SoknadInputs;
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
  http.post(EKSPERTBISTAND_API_PATH, async () => {
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
  http.get(EKSPERTBISTAND_API_PATH, async ({ request }) => {
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
  http.get(`${EKSPERTBISTAND_API_PATH}/:id`, async ({ params }) => {
    await ensureSkjemaStoreLoaded();
    const id = getParamValue(params.id);
    if (!id) {
      return HttpResponse.json({ message: "ugyldig id" }, { status: 400 });
    }
    const entry = skjemaStore.get(id);
    if (!entry) {
      return HttpResponse.json({ message: "skjema ikke funnet" }, { status: 404 });
    }
    return HttpResponse.json(entry.status === "utkast" ? toUtkastDto(entry) : toSkjemaDto(entry));
  }),
  http.patch(`${EKSPERTBISTAND_API_PATH}/:id`, async ({ params, request }) => {
    await ensureSkjemaStoreLoaded();
    const id = getParamValue(params.id);
    if (!id) {
      return HttpResponse.json({ message: "ugyldig id" }, { status: 400 });
    }
    const entry = skjemaStore.get(id);
    if (!entry || entry.status !== "utkast") {
      return HttpResponse.json({ message: "skjema ikke i utkast-status" }, { status: 409 });
    }
    let payload: Partial<SoknadInputs>;
    try {
      payload = (await request.json()) as Partial<SoknadInputs>;
    } catch {
      return HttpResponse.json({ message: "ugyldig payload" }, { status: 400 });
    }
    entry.data = mergeInputs(entry.data as Partial<SoknadInputs>, payload);
    skjemaStore.set(id, entry);
    await persistSkjemaStore();
    return HttpResponse.json(toUtkastDto(entry));
  }),
  http.delete(`${EKSPERTBISTAND_API_PATH}/:id`, async ({ params }) => {
    await ensureSkjemaStoreLoaded();
    const id = getParamValue(params.id);
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
  http.put(`${EKSPERTBISTAND_API_PATH}/:id`, async ({ params, request }) => {
    await ensureSkjemaStoreLoaded();
    const id = getParamValue(params.id);
    if (!id) {
      return HttpResponse.json({ message: "ugyldig id" }, { status: 400 });
    }
    const entry = skjemaStore.get(id);
    if (!entry || entry.status !== "utkast") {
      return HttpResponse.json({ message: "skjema ikke i utkast-status" }, { status: 409 });
    }
    let payload: SoknadInputs;
    try {
      payload = (await request.json()) as SoknadInputs;
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
  http.get("/oauth2/session", () => HttpResponse.json(loginSessionJson)),
];
