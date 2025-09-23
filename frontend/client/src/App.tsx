import { Routes, Route, Navigate } from "react-router-dom";
import SoknadPage from "./pages/SoknadPage";
import HealthPage from "./pages/HealthPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<SoknadPage />} />
      <Route path="/health" element={<HealthPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
