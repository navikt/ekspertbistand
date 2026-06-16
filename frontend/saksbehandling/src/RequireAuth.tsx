import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useTilgangContext } from "./tilgang/useTilgang";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isUnauthorized } = useTilgangContext();
  return isUnauthorized ? <Navigate to="/uautorisert" replace /> : <>{children}</>;
}
