import { LOGIN_URL } from "./constants";
import { HttpError, parseErrorMessage } from "./http";

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

    const message = isUnauthorized
      ? "Du er logget ut. Vennligst logg inn igjen."
      : await parseErrorMessage(response);

    throw new HttpError(message ?? `Kunne ikke hente data (${response.status}).`, {
      status: response.status,
      statusText: response.statusText,
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
