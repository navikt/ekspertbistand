import { z } from "zod";
import { EKSPERTBISTAND_TILSKUDDSBREV_HTML_PATH } from "../../utils/constants";
import { fetchJson } from "../../utils/api";

const tilskuddsbrevHtmlSchema = z.object({
  tilsagnNummer: z.string(),
  html: z.string(),
});

const tilskuddsbrevHtmlListSchema = z.array(tilskuddsbrevHtmlSchema);

export type TilskuddsbrevHtml = z.infer<typeof tilskuddsbrevHtmlSchema>;

export async function fetchTilskuddsbrevHtml(skjemaId: string): Promise<TilskuddsbrevHtml[]> {
  const response = await fetchJson<unknown[]>(
    `${EKSPERTBISTAND_TILSKUDDSBREV_HTML_PATH}/${skjemaId}/tilskuddsbrev-html`
  );
  return tilskuddsbrevHtmlListSchema.parse(response ?? []);
}
