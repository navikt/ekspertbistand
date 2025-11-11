import React, { useEffect } from "react";
import { Page, type PageBlockProps } from "@navikt/ds-react";
import { injectDecoratorClientSide } from "@navikt/nav-dekoratoren-moduler";
import { EKSPERTBISTAND_URL } from "../utils/constants";
import { envSwitch } from "../utils/env";

type DecoratorEnv = Exclude<Parameters<typeof injectDecoratorClientSide>[0]["env"], "localhost">;

type DecoratedPageProps = {
  children: React.ReactNode;
  blockProps?: PageBlockProps;
};

export function DecoratedPage({ children, blockProps }: DecoratedPageProps) {
  useDekorator();

  const mergedBlockProps: PageBlockProps = {
    width: "lg",
    gutters: true,
    ...blockProps,
    className: "page-content",
  };

  return (
    <Page footer={<Footer />}>
      <Header />
      <Page.Block {...mergedBlockProps}>{children}</Page.Block>
    </Page>
  );
}

function Header() {
  return <div id="decorator-header" />;
}

function Footer() {
  return <div id="decorator-footer" />;
}

function useDekorator() {
  useEffect(() => {
    if (import.meta.env.MODE === "test") return;
    // Avoid double-injecting in React StrictMode/dev or on route changes
    const win = window as unknown as { __navDecoratorInjected?: boolean };
    if (!win.__navDecoratorInjected) {
      win.__navDecoratorInjected = true;
      const env = envSwitch<DecoratorEnv>({
        prod: () => "prod",
        dev: () => "dev",
        local: () => "dev",
        other: () => "dev",
      });
      injectDecoratorClientSide({
        env,
        params: {
          context: "arbeidsgiver",
          redirectToApp: true,
          logoutUrl: EKSPERTBISTAND_URL + "/oauth2/logout",
        },
      });
    }
  }, []);
}

export default DecoratedPage;
