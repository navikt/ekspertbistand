import { Alert, BodyShort, Button, Loader, VStack } from "@navikt/ds-react";
import { useEffect } from "react";
import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { RequireAuth } from "./RequireAuth";
import AppLayout from "./components/AppLayout";
import { useSession } from "./hooks/useSession";
import OversiktPage from "./pages/OversiktPage";
import UautorisertPage from "./pages/UautorisertPage";
import { LOGIN_URL, OVERSIKT_PATH } from "./utils/constants";

const centeredStateStyle = {
  minHeight: "100vh",
  justifyContent: "center",
} as const;

function LoginGate() {
  const { authenticated, error, isLoading } = useSession();

  useEffect(() => {
    if (!error && !isLoading && !authenticated) {
      window.location.replace(LOGIN_URL);
    }
  }, [authenticated, error, isLoading]);

  if (isLoading) {
    return (
      <VStack align="center" gap="space-4" style={centeredStateStyle}>
        <Loader size="large" title="Sjekker innlogging" />
        <BodyShort>Sjekker innlogging …</BodyShort>
      </VStack>
    );
  }

  if (error) {
    return (
      <VStack align="center" gap="space-6" style={centeredStateStyle}>
        <Alert variant="error" fullWidth={false}>
          Kunne ikke sjekke innlogging. Start BFF-en lokalt på port 4000, eller åpne appen via
          serveren i stedet for Vite direkte.
        </Alert>
        <Button as="a" href={LOGIN_URL} variant="secondary">
          Prøv innlogging
        </Button>
      </VStack>
    );
  }

  if (!authenticated) {
    return null;
  }

  return <Outlet />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to={OVERSIKT_PATH} replace />} />
      <Route element={<LoginGate />}>
        <Route element={<AppLayout />}>
          <Route path="/uautorisert" element={<UautorisertPage />} />
          <Route
            path={OVERSIKT_PATH}
            element={
              <RequireAuth>
                <OversiktPage />
              </RequireAuth>
            }
          />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to={OVERSIKT_PATH} replace />} />
    </Routes>
  );
}
