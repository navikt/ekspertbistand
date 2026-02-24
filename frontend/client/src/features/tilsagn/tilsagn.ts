import { z } from "zod";
import { EKSPERTBISTAND_TILSKUDDSBREV_HTML_PATH } from "../../utils/constants";
import { fetchJson } from "../../utils/api";

const tilskuddsbrevHtmlSchema = z.object({
  tilsagnNummer: z.string(),
  html: z.string(),
});

const tilskuddsbrevHtmlListSchema = z.array(tilskuddsbrevHtmlSchema);

export type TilskuddsbrevHtml = z.infer<typeof tilskuddsbrevHtmlSchema>;

export async function fetchTilskuddsbrevHtmlForSkjema(
  skjemaId: string
): Promise<TilskuddsbrevHtml[]> {
  const response = await fetchJson<unknown[]>(
    `${EKSPERTBISTAND_TILSKUDDSBREV_HTML_PATH}/soknad/${skjemaId}/tilskuddsbrev-html`
  );
  return tilskuddsbrevHtmlListSchema.parse(response ?? []);
}
export async function fetchTilskuddsbrevHtmlForTilsagnNummer(
  tilsagnNummer: string
): Promise<TilskuddsbrevHtml | null> {
  const response = await fetchJson<unknown>(
    `${EKSPERTBISTAND_TILSKUDDSBREV_HTML_PATH}/tilskuddsbrev/${tilsagnNummer}/tilskuddsbrev-html`
  );
  if (response == null) {
    return null;
  }

  // Backend returns [] when the letter is missing or inaccessible.
  if (Array.isArray(response) && response.length === 0) {
    return null;
  }

  return tilskuddsbrevHtmlSchema.parse(response);
}
