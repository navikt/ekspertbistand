import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { SWRConfig } from "swr";
import App from "../App";
import { opprettUtkast as opprettUtkastMedDeltaker } from "../v2/features/soknad/validerDeltaker";
import { fetchJson } from "../utils/api";
import { EKSPERTBISTAND_API_PATH } from "../v2/utils/constants";
import { server } from "./server";

export const MOCK_BRUKER_FNR = "01819010001";
export const DELTAKER_FNR = "01819010192";
export const ANNEN_DELTAKER_FNR = "01819010273";
export const ORGNR = "123456780";

export const opprettBekreftetUtkast = async (fnr = DELTAKER_FNR): Promise<string> => {
  const resultat = await opprettUtkastMedDeltaker({ fnr, virksomhetsnummer: ORGNR });
  if (!resultat.ok) throw new Error("Klarte ikke å opprette utkast i testoppsettet");
  return resultat.utkast.id!;
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

const omEnUke = () => {
  const dato = new Date();
  dato.setDate(dato.getDate() + 7);
  return dato.toISOString().slice(0, 10);
};

/** Oppretter et utkast der alle feltene er fylt ut, så det kan sendes inn. */
export const opprettKomplettUtkast = async (): Promise<string> => {
  const id = await opprettBekreftetUtkast();
  await fetchJson(`${EKSPERTBISTAND_API_PATH}/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      virksomhet: {
        kontaktperson: {
          navn: "Lise Kontakt",
          epost: "lise@example.no",
          telefonnummer: "99112233",
        },
      },
      ekspert: {
        navn: "Dr. Ekspert",
        virksomhet: "Ekspert & Co AS",
        virksomhetNavn: "Ekspert & Co AS",
        virksomhetOrgnr: "910825226",
        godkjentUtdanningEllerAutorisasjon: ["Psykolog"],
        relevantKompetanse: ["Tilrettelegging på arbeidsplassen"],
      },
      behovForBistand: {
        begrunnelse: "Behov for tilrettelegging.",
        behov: "Kartlegging av arbeidssituasjonen.",
        timer: "20",
        estimertKostnad: "20000",
        tilrettelegging: "Tilrettelegging av arbeidsoppgaver.",
        startdato: omEnUke(),
      },
      nav: { kontaktperson: "Nav Kontakt" },
    }),
  });
  return id;
};
