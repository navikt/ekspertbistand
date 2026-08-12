import { detectEnv } from "./env";

/**
 * Feature flags — legg til nye flagg her.
 * Sett `enabledIn` til de miljøene der funksjonaliteten skal være aktiv.
 */
const FLAGS = {
  EKSPERTBISTAND_SAKSBEHANDLING_BETA: { enabledIn: ["local", "dev"] },
} satisfies Record<string, { enabledIn: Array<"local" | "dev" | "prod" | "other"> }>;

export type FeatureFlag = keyof typeof FLAGS;

export const isFeatureEnabled = (flag: FeatureFlag): boolean => {
  const env = detectEnv();
  return (FLAGS[flag].enabledIn as string[]).includes(env);
};
