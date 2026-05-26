import { Page } from "@navikt/ds-react";
import { Outlet } from "react-router-dom";
import AppHeader from "./AppHeader";
import classes from "./AppLayout.module.css";

export default function AppLayout() {
  return (
    <Page className={classes.page}>
      <AppHeader />
      <Page.Block width="lg" gutters as="main">
        <Outlet />
      </Page.Block>
    </Page>
  );
}
