import { Page } from "@navikt/ds-react";
import { Outlet } from "react-router";
import AppHeader from "./AppHeader";

export default function AppLayout() {
  return (
    <Page>
      <AppHeader />
      <Outlet />
    </Page>
  );
}
