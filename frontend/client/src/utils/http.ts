export async function parseErrorMessage(response: Response): Promise<string | null> {
  try {
    const payload = await response.json();
    if (typeof payload?.message === "string") {
      return payload.message;
    }
  } catch {
    // Ignore JSON parse failures; caller can fallback to generic error.
  }
  return null;
}
