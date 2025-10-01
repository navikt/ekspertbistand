import React, { useEffect } from "react";
import { Page, PageBlock } from "@navikt/ds-react";
import { injectDecoratorClientSide, setAvailableLanguages } from "@navikt/nav-dekoratoren-moduler";
import type { DecoratorLanguageOption } from "@navikt/nav-dekoratoren-moduler";

type BlockProps = React.ComponentProps<typeof PageBlock>;

type DecoratedPageProps = {
  children: React.ReactNode;
  blockProps?: BlockProps;
  languages?: DecoratorLanguageOption[];
};

export function DecoratedPage({ children, blockProps, languages }: DecoratedPageProps) {
  useDekorator();

  return (
    <Page footer={<Footer />}>
      <Header />
      <Page.Block {...blockProps}>{children}</Page.Block>
      <Env languages={languages} />
    </Page>
  );
}

function Header() {
  return <div id="decorator-header" />;
}

function Footer() {
  return <div id="decorator-footer" />;
}

function Env({ languages }: { languages?: DecoratorLanguageOption[] }) {
  useEffect(() => {
    if (!languages || import.meta.env.MODE === "test") return;
    setAvailableLanguages(languages);
  }, [languages]);
  return null;
}

function useDekorator() {
  useEffect(() => {
    if (import.meta.env.MODE === "test") return;
    // Avoid double-injecting in React StrictMode/dev or on route changes
    const win = window as unknown as { __navDecoratorInjected?: boolean };
    if (!win.__navDecoratorInjected) {
      win.__navDecoratorInjected = true;
      const env = import.meta.env.PROD ? "prod" : "dev";
      injectDecoratorClientSide({
        env,
        params: {
          context: "privatperson",
          simple: true,
        },
      });
    }
  }, []);
}

export default DecoratedPage;
