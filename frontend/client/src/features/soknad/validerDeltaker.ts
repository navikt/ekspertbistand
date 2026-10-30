import { fetchJson } from "../../utils/api";
import { EKSPERTBISTAND_API_PATH } from "../../utils/constants";
import { HttpError } from "../../utils/http";
import { draftDtoServerSchema } from "./server-schemas";
import type { DraftDto } from "./payload";

export type ValiderDeltakerRequest = {
  fnr: string;
  virksomhetsnummer: string;
};

export type ValiderDeltakerFeil =
  | { type: "ingenArbeidsforhold" }
  | { type: "forMangeOppslag" }
  | { type: "ugyldigInput"; felt: string | null; message: string }
  | { type: "oppslagFeilet" };

export type ValiderDeltakerResultat =
  { ok: true; utkast: DraftDto } | { ok: false; feil: ValiderDeltakerFeil };

/**
 * Sjekker at deltakeren har et aktivt arbeidsforhold i virksomheten, og lagrer deltaker,
 * virksomhet og navn på utkastet. Feil fra backend gjøres om til en [ValiderDeltakerFeil].
 */
export async function validerDeltaker(
  draftId: string,
  request: ValiderDeltakerRequest
): Promise<ValiderDeltakerResultat> {
  try {
    const response = await fetchJson<unknown>(
      `${EKSPERTBISTAND_API_PATH}/${encodeURIComponent(draftId)}/valider-deltaker`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
      }
    );
    return { ok: true, utkast: draftDtoServerSchema.parse(response) };
  } catch (error) {
    return { ok: false, feil: tolkFeil(error) };
  }
}

function tolkFeil(error: unknown): ValiderDeltakerFeil {
  if (!(error instanceof HttpError)) return { type: "oppslagFeilet" };
  switch (error.status) {
    case 422:
      return { type: "ingenArbeidsforhold" };
    case 429:
      return { type: "forMangeOppslag" };
    case 400:
      return { type: "ugyldigInput", felt: error.field ?? null, message: error.message };
    default:
      return { type: "oppslagFeilet" };
  }
}
