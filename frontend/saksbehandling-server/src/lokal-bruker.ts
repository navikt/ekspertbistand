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

export type LokalModus = {
  /** `/oauth2/session` svarer uten Wonderwall. */
  localSessionEnabled: boolean;
  /** Cookien `lokal-bruker` velger bruker, og `/lokal/brukere` er aktiv. */
  lokalBrukerbytte: boolean;
};

/**
 * `LOKAL_BRUKERBYTTE=true` brukes av ekspertbistand-saksbehandling-mock, som går mot ekspertbistand-backend-mock
 * uten Azure. Kaster ved oppstart heller enn å kjøre uten innlogging ved feilkonfigurasjon.
 */
export function lokalModus({
  nodeEnv,
  azureEnabled,
  lokalBrukerbytteFlagg,
}: {
  nodeEnv: string | undefined;
  azureEnabled: boolean;
  lokalBrukerbytteFlagg: boolean;
}): LokalModus {
  if (lokalBrukerbytteFlagg && azureEnabled) {
    throw new Error("LOKAL_BRUKERBYTTE kan ikke kombineres med Azure. Avslutter.");
  }
  if (nodeEnv === "production" && !azureEnabled && !lokalBrukerbytteFlagg) {
    throw new Error(
      "AZURE_APP_CLIENT_ID mangler i production. Avslutter for å unngå fail-open auth."
    );
  }
  const localSessionEnabled = nodeEnv !== "production" || lokalBrukerbytteFlagg;
  return { localSessionEnabled, lokalBrukerbytte: localSessionEnabled && !azureEnabled };
}
