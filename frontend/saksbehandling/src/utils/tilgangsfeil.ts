import type { HttpError } from "./http";

export const EGNE_ANSATTE_TEKST =
  "Saken gjelder en Nav-ansatt eller en annen skjermet person. Du må ha tilgang til egne ansatte (0000-GA-Egne_ansatte) for å åpne den. Snakk med lederen din hvis du skal behandle saken.";

export const IKKE_TILGANG_TEKST = "Du har ikke tilgang til denne saken.";

/** Forklaringen ved 403. `IKKE_TILGANG` er det nøytrale svaret for kode 6 og har ingen begrunnelse å vise. */
export function tilgangAvvistTekst(e: HttpError, standard: string): string {
  switch (e.kode) {
    case "AVVIST_SKJERMING":
      return EGNE_ANSATTE_TEKST;
    case "IKKE_TILGANG":
      return IKKE_TILGANG_TEKST;
    default:
      return e.begrunnelse ?? standard;
  }
}

export function feilIdTekst(e: HttpError): string | null {
  return e.feilId ? `Feil-ID: ${e.feilId}. Oppgi den hvis du kontakter brukerstøtte.` : null;
}

export function medFeilId(tekst: string, e: HttpError): string {
  const feilId = feilIdTekst(e);
  return feilId ? `${tekst} ${feilId}` : tekst;
}
