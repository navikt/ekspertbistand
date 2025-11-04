import { getWebInstrumentations, initializeFaro } from "@grafana/faro-web-sdk";
import { TELEMETRY_COLLECTOR_URL } from "../utils/constants";

// Initialize Faro as early as possible to capture errors
if (TELEMETRY_COLLECTOR_URL) {
  initializeFaro({
    url: TELEMETRY_COLLECTOR_URL,
    app: {
      name: "ekspertbistand-frontend",
    },
    instrumentations: [
      ...getWebInstrumentations({
        captureConsole: true,
      }),
    ],
  });
}
