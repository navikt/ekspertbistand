import { getAnalyticsInstance } from "@navikt/nav-dekoratoren-moduler";
import type { CustomEvents } from "../types/AnalyticCustomEvents";
import { envSwitch } from "./env";

const createProductionLogger = () => getAnalyticsInstance<CustomEvents>("ekspertbistand");
type AnalyticsLogger = ReturnType<typeof createProductionLogger>;

const mockGetAnalyticsInstance = (origin: string): AnalyticsLogger => {
  return ((eventName: unknown, eventData?: unknown) => {
    console.log(`Analytics Event Logged (Origin: ${origin}):`, eventName, eventData);
    return Promise.resolve(null as unknown as void);
  }) as unknown as AnalyticsLogger;
};

export const logger = envSwitch({
  prod: createProductionLogger,
  dev: createProductionLogger,
  other: () => mockGetAnalyticsInstance("ekspertbistand"),
});
