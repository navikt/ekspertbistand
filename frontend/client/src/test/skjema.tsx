import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { SWRConfig } from "swr";
import App from "../App";
import { draftDtoServerSchema } from "../features/soknad/server-schemas";
import { validerDeltaker } from "../features/soknad/validerDeltaker";
import { fetchJson } from "../utils/api";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { server } from "./server";

export const MOCK_BRUKER_FNR = "01819010001";
export const DELTAKER_FNR = "01819010192";
export const ANNEN_DELTAKER_FNR = "01819010273";
export const ORGNR = "123456780";

export const opprettUtkast = async (): Promise<string> => {
  const utkast = draftDtoServerSchema.parse(
    await fetchJson(EKSPERTBISTAND_API_PATH, { method: "POST" })
  );
  return utkast.id!;
};

export const opprettBekreftetUtkast = async (fnr = DELTAKER_FNR): Promise<string> => {
  const id = await opprettUtkast();
  const resultat = await validerDeltaker(id, { fnr, virksomhetsnummer: ORGNR });
  if (!resultat.ok) throw new Error("Klarte ikke å bekrefte deltaker i testoppsettet");
  return id;
};

/** Teller kall til valider-deltaker. Lytteren fjernes når testen er ferdig. */
export const tellValiderDeltakerKall = () => {
  const teller = { antall: 0 };
  const lytter = ({ request }: { request: Request }) => {
    if (request.method === "POST" && request.url.endsWith("/valider-deltaker")) teller.antall++;
  };
  server.events.on("request:start", lytter);
  return {
    teller,
    stopp: () => server.events.removeListener("request:start", lytter),
  };
};

export const renderSkjema = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <SWRConfig value={{ fetcher: fetchJson, provider: () => new Map(), dedupingInterval: 0 }}>
        <App />
      </SWRConfig>
    </MemoryRouter>
  );
