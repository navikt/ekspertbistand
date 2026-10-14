/**
 * Bytte av innlogget bruker ved kjøring mot LocalApplication uten Azure.
 * Identene må finnes i `lokaleAnsatte` i backend/src/test/kotlin/no/nav/ekspertbistand/LocalApplication.kt.
 */
export const LOKAL_BRUKER_COOKIE = "lokal-bruker";

export type LokalBruker = { navIdent: string; navn: string };

export const lokaleBrukere: ReadonlyArray<LokalBruker> = [
  { navIdent: "A123456", navn: "Ola Beggedeler" },
  { navIdent: "B654321", navn: "Kari Beggedeler" },
  { navIdent: "P123456", navn: "Per Baresaksbehandler" },
  { navIdent: "L654321", navn: "Lisa Barebeslutter" },
];

export function lesLokalBruker(cookieHeader: string | undefined): string | undefined {
  if (!cookieHeader) return undefined;
  for (const del of cookieHeader.split(";")) {
    const [navn, ...verdi] = del.trim().split("=");
    if (navn !== LOKAL_BRUKER_COOKIE) continue;
    const navIdent = decodeURIComponent(verdi.join("="));
    return lokaleBrukere.some((b) => b.navIdent === navIdent) ? navIdent : undefined;
  }
  return undefined;
}

export function lokaltSubjectToken(token: string, navIdent: string | undefined): string {
  return navIdent ? `${token}-${navIdent}` : token;
}
