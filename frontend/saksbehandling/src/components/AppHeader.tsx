import { MenuGridIcon, ThemeIcon } from "@navikt/aksel-icons";
import { ActionMenu, HStack, InternalHeader } from "@navikt/ds-react";
import { NavLink } from "react-router-dom";
import { useTilgangContext } from "../tilgang/useTilgang";
import { GOSYS_URL, LOGOUT_URL, MODIA_URL, OVERSIKT_PATH } from "../utils/constants";
import { useAppTheme } from "./AppThemeProvider";

export default function AppHeader() {
  const { theme, toggleTheme } = useAppTheme();
  const { innloggetAnsatt, setValgtEnhet } = useTilgangContext();
  const nextTheme = theme === "light" ? "dark" : "light";
  const valgtEnhet = innloggetAnsatt.gjeldendeEnhet;

  return (
    <InternalHeader>
      <InternalHeader.Title as={NavLink} to={OVERSIKT_PATH}>
        Ekspertbistand saksbehandling
      </InternalHeader.Title>
      <HStack justify="end" align="center" gap="space-2" wrap={false} style={{ flex: 1 }}>
        <ActionMenu>
          <ActionMenu.Trigger>
            <InternalHeader.Button>
              <MenuGridIcon title="Meny" fontSize="1.5rem" />
            </InternalHeader.Button>
          </ActionMenu.Trigger>
          <ActionMenu.Content>
            <ActionMenu.Group label="Systemer og oppslagsverk">
              <ActionMenu.Item as="a" href={GOSYS_URL} target="gosys">
                Gosys
              </ActionMenu.Item>
              <ActionMenu.Item as="a" href={`${MODIA_URL}/landingpage`} target="modia">
                Modia
              </ActionMenu.Item>
            </ActionMenu.Group>
            <ActionMenu.Divider />
            <ActionMenu.Group label="Utseende">
              <ActionMenu.Item
                icon={<ThemeIcon aria-hidden />}
                onSelect={toggleTheme}
              >{`Endre til ${themeLabel(nextTheme)}`}</ActionMenu.Item>
            </ActionMenu.Group>
          </ActionMenu.Content>
        </ActionMenu>
        <ActionMenu>
          <ActionMenu.Trigger>
            <InternalHeader.UserButton name={innloggetAnsatt.navn} description={valgtEnhet.navn} />
          </ActionMenu.Trigger>
          <ActionMenu.Content>
            <ActionMenu.Group label="Andre enheter">
              {innloggetAnsatt.enheter
                .filter(({ nummer }) => nummer !== valgtEnhet.nummer)
                .map((enhet) => (
                  <ActionMenu.Item key={enhet.id} onSelect={() => setValgtEnhet(enhet.nummer)}>
                    {enhet.nummer} - {enhet.navn}
                  </ActionMenu.Item>
                ))}
            </ActionMenu.Group>
            <ActionMenu.Divider />
            <ActionMenu.Item as="a" href={LOGOUT_URL}>
              Logg ut
            </ActionMenu.Item>
          </ActionMenu.Content>
        </ActionMenu>
      </HStack>
    </InternalHeader>
  );
}

function themeLabel(theme: "light" | "dark") {
  return theme === "dark" ? "mørkt tema" : "lyst tema";
}
