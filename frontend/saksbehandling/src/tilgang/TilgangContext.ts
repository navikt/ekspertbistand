import { createContext } from "react";
import type { InnloggetAnsatt } from "../mock/ansatt";

export type TilgangContextType = {
  innloggetAnsatt: InnloggetAnsatt | undefined;
  isLoading: boolean;
  isUnauthorized: boolean;
};

export const initialTilgangState: TilgangContextType = {
  innloggetAnsatt: undefined,
  isLoading: true,
  isUnauthorized: false,
};

export const TilgangContext = createContext<TilgangContextType>(initialTilgangState);
