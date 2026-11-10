import { z } from "zod";
import { fetchBlob, fetchJson } from "../../../utils/api";
import { HttpError } from "../../../utils/http";
import { EKSPERTBISTAND_API_PATH } from "../../utils/constants";

export const VEDTAKSBREV_FILNAVN = "Vedtaksbrev ekspertbistand.pdf";

const vedtaksbrevSchema = z.object({
  utfall: z.enum(["INNVILGET", "INNVILGET_REDUSERT", "AVSLATT"]),
  vedtaksdato: z.string(),
  innvilgetBelop: z.string().nullish(),
  soknadssum: z.string().nullish(),
  tiltaksperiode: z.object({ fra: z.string(), til: z.string() }).nullish(),
  refusjonsfrist: z.string().nullish(),
  deltakerNavn: z.string(),
  virksomhetsnavn: z.string(),
});

export type Vedtaksbrev = z.infer<typeof vedtaksbrevSchema>;
export type VedtaksbrevUtfall = Vedtaksbrev["utfall"];

export const vedtakUrl = (id: string) => `${EKSPERTBISTAND_API_PATH}/${id}/vedtak`;

/** Gir `null` når søknaden ikke har vedtak i ny løsning (404). */
export async function fetchVedtak(url: string): Promise<Vedtaksbrev | null> {
  try {
    return vedtaksbrevSchema.parse(await fetchJson<unknown>(url));
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) return null;
    throw error;
  }
}

export async function fetchVedtaksbrevPdf(id: string): Promise<Blob> {
  return fetchBlob(`${vedtakUrl(id)}/brev-pdf`);
}
