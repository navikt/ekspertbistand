import { createContext } from "react";
import type { InnloggetAnsatt } from "../mock/ansatt";

export type TilgangContextType = {
  innloggetAnsatt: InnloggetAnsatt | undefined;
  isLoading: boolean;
  isUnauthorized: boolean;
  setValgtEnhet: (nummer: string) => Promise<void>;
};

export const initialTilgangState: TilgangContextType = {
  innloggetAnsatt: undefined,
  isLoading: true,
  isUnauthorized: false,
  async setValgtEnhet() {},
};

export const TilgangContext = createContext<TilgangContextType>(initialTilgangState);
