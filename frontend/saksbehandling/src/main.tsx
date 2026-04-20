import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { SWRConfig } from "swr";
import "@navikt/ds-css/dist/index.css";
import "./index.css";
import App from "./App.tsx";
import { AppThemeProvider } from "./components/AppThemeProvider";
import { TilgangProvider } from "./tilgang/TilgangProvider";
import { fetchJson } from "./utils/api";
import { isMockEnabled } from "./utils/constants";

async function startMockServiceWorker() {
  if (!isMockEnabled()) return;
  const { worker } = await import("./mocks/browser");
  await worker.start({
    onUnhandledRequest: "bypass",
    serviceWorker: { url: "/mockServiceWorker.js" },
  });
}

async function bootstrap() {
  await startMockServiceWorker();

  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <BrowserRouter>
        <SWRConfig
          value={{
            fetcher: fetchJson,
            revalidateOnFocus: false,
          }}
        >
          <TilgangProvider>
            <AppThemeProvider>
              <App />
            </AppThemeProvider>
          </TilgangProvider>
        </SWRConfig>
      </BrowserRouter>
    </StrictMode>
  );
}

void bootstrap();
