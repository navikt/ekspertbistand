import { Routes, Route, Navigate, Outlet, useParams } from "react-router-dom";
import HealthPage from "./pages/HealthPage";
import SoknaderPage from "./pages/SoknaderPage.tsx";
import LandingPage from "./pages/LandingPage";
import SoknadPage from "./pages/SoknadPage";
import SkjemaSteg1Page from "./pages/SkjemaSteg1Page";
import SkjemaSteg2Page from "./pages/SkjemaSteg2Page";
import OppsummeringPage from "./pages/OppsummeringPage";
import KvitteringPage from "./pages/KvitteringPage";
import { useSoknadDraft } from "./context/SoknadDraftContext";
import { SoknadDraftProvider } from "./providers/SoknadDraftProvider";
import { SkjemaFormProvider } from "./providers/SkjemaFormProvider.tsx";
import { ScrollToTop } from "./components/ScrollToTop";
import { SOKNADER_PATH } from "./utils/constants";
import { useOrganisasjoner } from "./hooks/useOrganisasjoner";
import ManglerTilgangPage from "./pages/ManglerTilgangPage";
import TilgangFeilPage from "./pages/TilgangFeilPage";
import { useSession } from "./hooks/useSession";
import { BodyShort, Loader, VStack } from "@navikt/ds-react";
import LoginRequiredPage from "./pages/LoginRequiredPage";

function SkjemaDraftRoute() {
  const { id } = useParams<{ id: string }>();
  if (!id) {
    return <Navigate to={SOKNADER_PATH} replace />;
  }
  return (
    <SoknadDraftProvider draftId={id}>
      <SkjemaDraftOutlet />
    </SoknadDraftProvider>
  );
}

function SkjemaDraftOutlet() {
  const { draftId, status, hydrated } = useSoknadDraft();

  if (hydrated && status === "innsendt") {
    return <Navigate to={`/skjema/${draftId}/kvittering`} replace />;
  }

  return (
    <SkjemaFormProvider>
      <Outlet />
    </SkjemaFormProvider>
  );
}

function OrganisasjonerGate() {
  const { organisasjoner, error, isLoading } = useOrganisasjoner();
  if (error) {
    return <TilgangFeilPage />;
  }
  if (!isLoading && organisasjoner.length === 0) {
    return <ManglerTilgangPage />;
  }
  return <Outlet />;
}

function LoginGate() {
  const { authenticated, error, isLoading } = useSession();
  if (isLoading) {
    return (
      <VStack align="center" gap="space-4" style={{ padding: "2rem" }}>
        <Loader size="large" title="Sjekker innlogging" />
        <BodyShort>Sjekker innlogging …</BodyShort>
      </VStack>
    );
  }
  if (error || !authenticated) {
    return <LoginRequiredPage />;
  }
  return <Outlet />;
}

export default function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/ekspertbistand" element={<LandingPage />} />
        <Route element={<LoginGate />}>
          <Route element={<OrganisasjonerGate />}>
          <Route path={SOKNADER_PATH} element={<SoknaderPage />} />
          <Route path="/skjema" element={<Navigate to="/skjema/start" replace />} />
            <Route path="/skjema/start" element={<SoknadPage />} />
            <Route path="/skjema/:id/kvittering" element={<KvitteringPage />} />
            <Route path="/skjema/:id" element={<SkjemaDraftRoute />}>
              <Route path="steg-1" element={<SkjemaSteg1Page />} />
              <Route path="steg-2" element={<SkjemaSteg2Page />} />
              <Route path="oppsummering" element={<OppsummeringPage />} />
              <Route path="" element={<Navigate to="steg-1" replace />} />
            </Route>
            <Route path="/health" element={<HealthPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
