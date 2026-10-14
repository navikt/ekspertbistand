import { ActionMenu, BodyShort, Box, Button, HStack } from "@navikt/ds-react";
import { useState } from "react";
import { useNavigate } from "react-router";
import type { SakDetaljer } from "../hooks/useSak";
import { kanTildeleMegSomSaksbehandler, rolleForOppgave } from "../hooks/useSaker";
import { useTildeling } from "../hooks/useTildeling";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import { OVERSIKT_PATH } from "../utils/constants";
import HeaderKnapp from "./HeaderKnapp";

type Props = {
  sak: SakDetaljer;
  /** Null når en ny handling starter, slik at gammel feil forsvinner. */
  onFeil: (feil: Error | null) => void;
};

export default function Tildeling({ sak, onFeil }: Props) {
  const innloggetAnsatt = useInnloggetAnsatt();
  const navigate = useNavigate();
  const [menyÅpen, setMenyÅpen] = useState(false);
  const saksbehandler = useTildeling(sak.sakId, { onError: onFeil });
  const beslutter = useTildeling(sak.sakId, { rolle: "beslutter", onError: onFeil });

  const rolle = rolleForOppgave(sak.oppgavetype);
  if (!rolle) return null;

  const erBeslutter = rolle === "beslutter";
  const { tildelMeg, frigjoer, isSaving } = erBeslutter ? beslutter : saksbehandler;
  const ident = erBeslutter ? sak.beslutterIdent : sak.saksbehandlerIdent;
  const navn = erBeslutter ? sak.beslutterNavn : sak.saksbehandlerNavn;
  const kanTildeleMeg = erBeslutter
    ? sak.kanTildeleMegSomBeslutter
    : kanTildeleMegSomSaksbehandler(sak);
  const erMeg = !!innloggetAnsatt && ident === innloggetAnsatt.id;

  const utfoer = (handling: () => Promise<boolean>) => {
    onFeil(null);
    return handling();
  };

  if (!ident) {
    if (!kanTildeleMeg) return null;
    return (
      <Box paddingInline="space-0 space-16" asChild>
        <HStack align="center">
          <Button
            size="small"
            variant="secondary"
            loading={isSaving}
            onClick={() => void utfoer(tildelMeg)}
          >
            Tildel meg
          </Button>
        </HStack>
      </Box>
    );
  }

  const tekst = <BodyShort>{`${erBeslutter ? "Beslutter" : "Saksbehandler"} ${navn}`}</BodyShort>;
  const visFrigjoer = erMeg && !erBeslutter;
  const visOverta = !erMeg && kanTildeleMeg;

  if (!visFrigjoer && !visOverta) {
    return (
      <Box paddingInline="space-0 space-16" asChild>
        <HStack align="center">{tekst}</HStack>
      </Box>
    );
  }

  const frigjoerOgGåTilOversikten = async () => {
    if (await utfoer(frigjoer)) navigate(OVERSIKT_PATH);
  };

  return (
    <HStack gap="space-16" align="stretch">
      <HStack align="center">{tekst}</HStack>
      <ActionMenu open={menyÅpen} onOpenChange={setMenyÅpen}>
        <ActionMenu.Trigger>
          <HeaderKnapp tekst="Meny" åpen={menyÅpen} loading={isSaving} />
        </ActionMenu.Trigger>
        <ActionMenu.Content>
          <ActionMenu.Group label="Legg behandlingen tilbake">
            {visOverta && (
              <ActionMenu.Item onSelect={() => void utfoer(tildelMeg)}>
                {erBeslutter ? "Overta som beslutter" : "Overta som saksbehandler"}
              </ActionMenu.Item>
            )}
            {visFrigjoer && (
              <ActionMenu.Item onSelect={() => void frigjoerOgGåTilOversikten()}>
                Frigjør oppgave
              </ActionMenu.Item>
            )}
          </ActionMenu.Group>
        </ActionMenu.Content>
      </ActionMenu>
    </HStack>
  );
}
