import { Navigate, Outlet, useParams } from "react-router";
import { SOKNADER_PATH } from "./utils/constants";
import { useSoknadDraft } from "./context/SoknadDraftContext";
import { SoknadDraftProvider } from "./providers/SoknadDraftProvider";
import { SkjemaFormProvider } from "./providers/SkjemaFormProvider";

export default function SkjemaLayout() {
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
    return <Navigate to={`/v2/skjema/${draftId}/kvittering`} replace />;
  }

  return (
    <SkjemaFormProvider>
      <Outlet />
    </SkjemaFormProvider>
  );
}
