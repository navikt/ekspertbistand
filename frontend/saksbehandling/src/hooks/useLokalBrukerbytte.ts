import useSWR from "swr";
import {
  isLokalBrukerbytteMulig,
  LOKAL_BRUKER_COOKIE,
  LOKALE_BRUKERE_URL,
} from "../utils/constants";

export type LokalBruker = { navIdent: string; navn: string };
type LokaleBrukere = { valgt: string | null; brukere: LokalBruker[] };

async function hentLokaleBrukere(url: string): Promise<LokaleBrukere | null> {
  const response = await fetch(url, { credentials: "include" });
  const erJson = response.headers.get("content-type")?.includes("application/json");
  return response.ok && erJson ? ((await response.json()) as LokaleBrukere) : null;
}

/** Brukerbytte mot LocalApplication og ekspertbistand-backend-mock. BFF-en svarer bare uten Azure. */
export function useLokalBrukerbytte() {
  const { data } = useSWR(
    isLokalBrukerbytteMulig() ? LOKALE_BRUKERE_URL : null,
    hentLokaleBrukere,
    { shouldRetryOnError: false }
  );

  const byttBruker = (navIdent: string) => {
    document.cookie = `${LOKAL_BRUKER_COOKIE}=${encodeURIComponent(navIdent)}; path=/; SameSite=Strict`;
    window.location.reload();
  };

  return { lokaleBrukere: data ?? null, byttBruker };
}
