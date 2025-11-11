import { parseErrorMessage } from "./http";

async function handleResponse<T>(response: Response): Promise<T | null> {
  if (!response.ok) {
    const message = await parseErrorMessage(response);
    throw new Error(message ?? `Kunne ikke hente data (${response.status}).`);
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
