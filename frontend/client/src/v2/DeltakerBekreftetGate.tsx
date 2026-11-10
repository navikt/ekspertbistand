import { Navigate, Outlet } from "react-router";
import { useSoknadDraft } from "./context/SoknadDraftContext";

export default function DeltakerBekreftetGate() {
  const { draftId, hydrated, deltakerBekreftet } = useSoknadDraft();
  if (hydrated && !deltakerBekreftet) {
    return <Navigate to={`/v2/skjema/${draftId}/steg-1`} replace />;
  }
  return <Outlet />;
}
