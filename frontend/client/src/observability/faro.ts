import { getWebInstrumentations, initializeFaro } from "@grafana/faro-web-sdk";
import { detectEnv } from "../utils/env";

const env = detectEnv();

// Collector URL for NAIS telemetry (prod/dev)
const collectorUrl =
  env === "prod"
    ? "https://telemetry.nav.no/collect"
    : env === "dev"
      ? "https://telemetry.dev.nav.no/collect"
      : undefined;

// Initialize Faro as early as possible to capture errors
if (collectorUrl) {
  initializeFaro({
    url: collectorUrl,
    app: {
      name: "ekspertbistand-frontend",
    },
    instrumentations: [
      ...getWebInstrumentations({
        captureConsole: true,
        // errors and unhandled rejections are captured by default
      }),
    ],
  });
}
