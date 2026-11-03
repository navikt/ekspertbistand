import { Routes, Route, Navigate, Outlet, useParams } from "react-router";
import HealthPage from "./pages/HealthPage";
import SoknaderPage from "./pages/SoknaderPage.tsx";
import LandingPage from "./pages/LandingPage";
import SoknadPage from "./pages/SoknadPage";
import SkjemaSteg1Page from "./pages/SkjemaSteg1Page";
import SkjemaSteg2Page from "./pages/SkjemaSteg2Page";
import OppsummeringPage from "./pages/OppsummeringPage";
import KvitteringPage from "./pages/KvitteringPage";
import TilskuddsbrevPage from "./pages/TilskuddsbrevPage";
import SluttrapportPage from "./pages/SluttrapportPage";
import SokRefusjonPage from "./pages/SokRefusjonPage";
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
import TilgangKunKOMMPage from "./pages/TilgangKunKOMMPage.tsx";
import { isFeatureEnabled } from "./utils/featureFlags";
import SkjemaV2Layout from "./v2/SkjemaLayout";
import SoknadV2Page from "./v2/pages/SoknadPage";
import SkjemaV2Steg1Page from "./v2/pages/SkjemaSteg1Page";
import SkjemaV2Steg2Page from "./v2/pages/SkjemaSteg2Page";
import OppsummeringV2Page from "./v2/pages/OppsummeringPage";
import KvitteringV2Page from "./v2/pages/KvitteringPage";
import SoknaderV2Page from "./v2/pages/SoknaderPage";
import TilskuddsbrevV2Page from "./v2/pages/TilskuddsbrevPage";
import SluttrapportV2Page from "./v2/pages/SluttrapportPage";
import SokRefusjonV2Page from "./v2/pages/SokRefusjonPage";

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
  const { authenticated, error: sessionError, isLoading: isSessionLoading } = useSession();
  const { organisasjoner, error, isLoading } = useOrganisasjoner();
  if (isSessionLoading) {
    return (
      <VStack align="center" gap="space-4" padding="space-32">
        <Loader size="large" title="Sjekker innlogging" />
        <BodyShort>Sjekker innlogging …</BodyShort>
      </VStack>
    );
  }
  if (sessionError || !authenticated) {
    return <LoginRequiredPage />;
  }
  if (error) {
    return <TilgangFeilPage />;
  }
  if (!isLoading && organisasjoner.length === 0) {
    return <ManglerTilgangPage />;
  }
  if (!isLoading && organisasjoner.every((org) => org.underenheter.length === 0)) {
    return <TilgangKunKOMMPage />;
  }
  return <Outlet />;
}

function LoginGate() {
  const { authenticated, error, isLoading } = useSession();
  if (isLoading) {
    return (
      <VStack align="center" gap="space-4" padding="space-32">
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
            {isFeatureEnabled("EKSPERTBISTAND_V2") && (
              <Route path="/v2">
                <Route index element={<Navigate to="/v2/soknader" replace />} />
                <Route path="soknader" element={<SoknaderV2Page />} />
                <Route path="skjema" element={<Navigate to="/v2/skjema/start" replace />} />
                <Route path="skjema/start" element={<SoknadV2Page />} />
                <Route path="skjema/:id/kvittering" element={<KvitteringV2Page />} />
                <Route path="tilskuddsbrev/:tilsagnNummer" element={<TilskuddsbrevV2Page />} />
                {isFeatureEnabled("EKSPERTBISTAND_SAKSBEHANDLING_BETA") && (
                  <>
                    <Route path="skjema/:id/sluttrapport" element={<SluttrapportV2Page />} />
                    <Route path="skjema/:id/refusjon" element={<SokRefusjonV2Page />} />
                  </>
                )}
                <Route path="skjema/:id" element={<SkjemaV2Layout />}>
                  <Route path="steg-1" element={<SkjemaV2Steg1Page />} />
                  <Route path="steg-2" element={<SkjemaV2Steg2Page />} />
                  <Route path="oppsummering" element={<OppsummeringV2Page />} />
                  <Route path="" element={<Navigate to="steg-1" replace />} />
                </Route>
              </Route>
            )}
            <Route path="/tilskuddsbrev/:tilsagnNummer" element={<TilskuddsbrevPage />} />
            {isFeatureEnabled("EKSPERTBISTAND_SAKSBEHANDLING_BETA") && (
              <Route path="/skjema/:id/sluttrapport" element={<SluttrapportPage />} />
            )}
            {isFeatureEnabled("EKSPERTBISTAND_SAKSBEHANDLING_BETA") && (
              <Route path="/skjema/:id/refusjon" element={<SokRefusjonPage />} />
            )}
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
