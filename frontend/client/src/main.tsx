import "./observability/faro";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "@navikt/ds-css/darkside";
import { Theme } from "@navikt/ds-react/Theme";
import "./index.css";
import App from "./App.tsx";
import { detectEnv } from "./utils/env";

const routerBasename = detectEnv() === "dev" ? "/ekspertbistand" : "/";
import { FaroErrorBoundary } from "@grafana/faro-react";
import { Alert, Button } from "@navikt/ds-react";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter basename={routerBasename}>
      <Theme theme="auto">
        <FaroErrorBoundary
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
        </FaroErrorBoundary>
      </Theme>
    </BrowserRouter>
  </StrictMode>
);
