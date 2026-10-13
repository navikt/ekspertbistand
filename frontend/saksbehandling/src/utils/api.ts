import { LOGIN_URL } from "./constants";
import { HttpError, parseErrorPayload } from "./http";

let loginRedirectTriggered = false;

const redirectToLogin = () => {
  if (typeof window === "undefined" || loginRedirectTriggered) return;
  loginRedirectTriggered = true;
  window.location.replace(LOGIN_URL);
};

async function handleResponse<T>(response: Response): Promise<T | null> {
  const isUnauthorized = response.status === 401;

  if (!response.ok) {
    if (isUnauthorized) {
      redirectToLogin();
    }

    const { message, begrunnelse, kode } = isUnauthorized
      ? { message: "Du er logget ut. Vennligst logg inn igjen.", begrunnelse: null, kode: null }
      : await parseErrorPayload(response);

    throw new HttpError(message ?? `Kunne ikke hente data (${response.status}).`, {
      status: response.status,
      statusText: response.statusText,
      begrunnelse,
      kode,
    });
  }

  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  if (!text || text.trim().length === 0) {
    return null;
  }

  try {
    return JSON.parse(text) as T;
  } catch (error) {
    throw new Error(error instanceof Error ? error.message : "Kunne ikke tolke svar fra serveren.");
  }
}

export async function fetchJson<T>(input: string, init?: RequestInit): Promise<T | null> {
  const headers = new Headers(init?.headers ?? undefined);
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }

  const response = await fetch(input, {
    ...init,
    headers,
  });

  return handleResponse<T>(response);
}

function nedlastingsfilnavn(response: Response): string {
  const contentDisposition = response.headers.get("Content-Disposition") ?? "";
  const match = /(?:^|;)\s*filename=(?:"([^"]+)"|([^;\s]+))/i.exec(contentDisposition);
  const filnavn = match?.[1] ?? match?.[2] ?? "";
  return /^[a-z0-9._-]{1,90}\.pdf$/i.test(filnavn) ? filnavn : "refusjonsvedlegg.pdf";
}

export async function lastNedPdf(input: string, signal: AbortSignal): Promise<void> {
  const response = await fetch(input, {
    signal,
    cache: "no-store",
    headers: { Accept: "application/pdf" },
  });

  if (!response.ok) {
    if (response.status === 401) {
      redirectToLogin();
    }
    throw new HttpError("Kunne ikke laste ned vedlegget.", {
      status: response.status,
      statusText: response.statusText,
    });
  }

  if (!/^application\/pdf(?:\s*;|$)/i.test(response.headers.get("Content-Type") ?? "")) {
    throw new Error("Serveren returnerte ikke en PDF.");
  }

  const blob = await response.blob();
  if (signal.aborted) {
    throw new DOMException("Nedlastingen ble avbrutt.", "AbortError");
  }

  const objektUrl = URL.createObjectURL(blob);
  try {
    const lenke = document.createElement("a");
    lenke.href = objektUrl;
    lenke.download = nedlastingsfilnavn(response);
    document.body.appendChild(lenke);
    try {
      lenke.click();
    } finally {
      lenke.remove();
    }
  } finally {
    window.setTimeout(() => URL.revokeObjectURL(objektUrl), 10_000);
  }
}
