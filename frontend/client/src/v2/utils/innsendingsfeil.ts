export async function lesInnsendingsfeil(response: Response): Promise<string> {
  const fallback = `Feil ved innsending (${response.status})`;
  const contentType = response.headers.get("Content-Type") ?? "";
  const body = (await response.text()).trim();
  if (!body) return fallback;

  if (contentType.includes("application/json")) {
    try {
      const parsed = JSON.parse(body) as { message?: unknown } | null;
      return typeof parsed?.message === "string" ? parsed.message : fallback;
    } catch {
      return fallback;
    }
  }

  // Bare ren tekst fra Ktor; HTML-feilsider fra ingress og proxy skal ikke vises.
  return contentType.startsWith("text/plain") ? body : fallback;
}
