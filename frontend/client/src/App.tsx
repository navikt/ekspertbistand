import { Routes, Route, Navigate, Outlet, useParams } from "react-router-dom";
import SoknadPage from "./pages/SoknadPage";
import HealthPage from "./pages/HealthPage";
import SkjemaSteg1Page from "./pages/SkjemaSteg1Page";
import SkjemaSteg2Page from "./pages/SkjemaSteg2Page";
import OppsummeringPage from "./pages/OppsummeringPage";
import { SoknadDraftProvider } from "./context/SoknadDraftContext";
import { SkjemaFormProvider } from "./pages/SkjemaFormProvider";
import { ScrollToTop } from "./components/ScrollToTop";

function SkjemaDraftRoute() {
  const { id } = useParams<{ id: string }>();
  if (!id) {
    return <Navigate to="/" replace />;
  }
  return (
    <SoknadDraftProvider draftId={id}>
      <SkjemaFormProvider>
        <Outlet />
      </SkjemaFormProvider>
    </SoknadDraftProvider>
  );
}

export default function App() {
  return (
    <>
      <ScrollToTop />
      <Routes future={{ v7_relativeSplatPath: true }}>
        <Route path="/" element={<SoknadPage />} />
        <Route path="/skjema" element={<Navigate to="/" replace />} />
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
