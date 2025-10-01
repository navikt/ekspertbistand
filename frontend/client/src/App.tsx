import { Routes, Route, Navigate } from "react-router-dom";
import SoknadPage from "./pages/SoknadPage";
import HealthPage from "./pages/HealthPage";
import SoknadSkjemaPage from "./pages/SoknadSkjemaPage";
import OppsummeringPage from "./pages/OppsummeringPage";

export default function App() {
  return (
    <Routes future={{ v7_relativeSplatPath: true }}>
      <Route path="/" element={<SoknadPage />} />
      <Route path="/skjema" element={<SoknadSkjemaPage />} />
      <Route path="/oppsummering" element={<OppsummeringPage />} />
      <Route path="/health" element={<HealthPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
