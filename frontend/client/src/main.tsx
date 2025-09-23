import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "@navikt/ds-css/darkside";
import { Theme } from "@navikt/ds-react/Theme";
import "./index.css";
import App from "./App.tsx";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <Theme theme="auto">
        <App />
      </Theme>
    </BrowserRouter>
  </StrictMode>
);
