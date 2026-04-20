import { createContext } from "react";
import type { InnloggetAnsatt } from "../mock/ansatt";
import { mockInnloggetAnsatt } from "../mock/ansatt";

export type TilgangContextType = {
  innloggetAnsatt: InnloggetAnsatt;
  isLoading: boolean;
  isUnauthorized: boolean;
  setValgtEnhet: (nummer: string) => Promise<void>;
};

export const initialTilgangState: TilgangContextType = {
  innloggetAnsatt: mockInnloggetAnsatt,
  isLoading: false,
  isUnauthorized: false,
  async setValgtEnhet() {},
};

export const TilgangContext = createContext<TilgangContextType>(initialTilgangState);
