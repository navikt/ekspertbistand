import { useContext } from "react";
import { TilgangContext } from "./TilgangContext";

export function useTilgangContext() {
  return useContext(TilgangContext);
}

export function useInnloggetAnsatt() {
  return useTilgangContext().innloggetAnsatt;
}
