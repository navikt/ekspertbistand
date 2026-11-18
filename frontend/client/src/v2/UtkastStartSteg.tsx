import { Loader, VStack } from "@navikt/ds-react";
import { Navigate } from "react-router";
import { useSoknadDraft } from "./context/SoknadDraftContext";

/** Åpner utkastet på steg 2 når steg 1 er fylt ut, ellers på steg 1. */
export default function UtkastStartSteg() {
  const { hydrated, bekreftetDeltaker } = useSoknadDraft();
  if (!hydrated) {
    return (
      <VStack align="center" padding="space-32">
        <Loader size="large" title="Henter utkastet" />
      </VStack>
    );
  }
  return <Navigate to={bekreftetDeltaker ? "steg-2" : "steg-1"} replace />;
}
