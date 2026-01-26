import "./observability/faro";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "@navikt/ds-css/dist/index.css";
import { Theme } from "@navikt/ds-react/Theme";
import "./index.css";
import App from "./App.tsx";
import { FaroErrorBoundary } from "@grafana/faro-react";
import { Alert, Button } from "@navikt/ds-react";
import { APP_BASE_PATH, TELEMETRY_COLLECTOR_URL } from "./utils/constants";
import { SimpleErrorBoundary } from "./components/SimpleErrorBoundary";
import { SWRConfig } from "swr";
import { fetchJson } from "./utils/api";

const routerBasename = APP_BASE_PATH || "/";

async function startMockServiceWorker() {
  if (import.meta.env.DEV) {
    const { worker } = await import("./mocks/browser");
    await worker.start({ onUnhandledRequest: "bypass" });
  }
}

async function bootstrap() {
  await startMockServiceWorker();

  const ErrorBoundaryComponent = TELEMETRY_COLLECTOR_URL ? FaroErrorBoundary : SimpleErrorBoundary;

  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <BrowserRouter basename={routerBasename}>
        <Theme theme="light">
          <SWRConfig
            value={{
              fetcher: fetchJson,
              revalidateOnFocus: false,
            }}
          >
            <ErrorBoundaryComponent
              fallback={
                <div style={{ padding: "1rem" }}>
                  <Alert variant="error" inline={false} fullWidth>
                    <strong>Oops! Noe gikk galt.</strong>
                    <div>Vi har registrert feilen. Prøv å laste siden på nytt.</div>
                    <div style={{ marginTop: "0.75rem" }}>
                      <Button size="small" onClick={() => window.location.reload()}>
                        Last inn på nytt
                      </Button>
                    </div>
                  </Alert>
                </div>
              }
            >
              <App />
            </ErrorBoundaryComponent>
          </SWRConfig>
        </Theme>
      </BrowserRouter>
    </StrictMode>
  );
}

void bootstrap();
