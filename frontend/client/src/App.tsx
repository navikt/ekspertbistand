import { Routes, Route, Navigate, Outlet, useParams } from "react-router-dom";
import HealthPage from "./pages/HealthPage";
import SoknaderPage from "./pages/SoknaderPage.tsx";
import LandingPage from "./pages/LandingPage";
import SoknadPage from "./pages/SoknadPage";
import SkjemaSteg1Page from "./pages/SkjemaSteg1Page";
import SkjemaSteg2Page from "./pages/SkjemaSteg2Page";
import OppsummeringPage from "./pages/OppsummeringPage";
import KvitteringPage from "./pages/KvitteringPage";
import { SoknadDraftProvider, useSoknadDraft } from "./context/SoknadDraftContext";
import { SkjemaFormProvider } from "./providers/SkjemaFormProvider.tsx";
import { ScrollToTop } from "./components/ScrollToTop";
import { APPLICATIONS_PATH } from "./utils/constants";

function SkjemaDraftRoute() {
  const { id } = useParams<{ id: string }>();
  if (!id) {
    return <Navigate to={APPLICATIONS_PATH} replace />;
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

export default function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path={APPLICATIONS_PATH} element={<SoknaderPage />} />
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
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
