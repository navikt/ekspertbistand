import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import SokRefusjonPage from "./SokRefusjonPage";
import { useSoknad } from "../hooks/useSoknad";
import { useRefusjonStatus, type RefusjonStatus } from "../hooks/useRefusjonStatus";
import { useMaksBelop } from "../hooks/useMaksBelop";
import { useKontonummerFinnes } from "../hooks/useKontonummerFinnes";
import { useVedtak } from "../hooks/useVedtak";
import { EKSPERTBISTAND_REFUSJON_PATH } from "../utils/constants";
import { HttpError } from "../../utils/http";

vi.mock("../hooks/useSoknad", () => ({ useSoknad: vi.fn() }));
vi.mock("../hooks/useRefusjonStatus", () => ({ useRefusjonStatus: vi.fn() }));
vi.mock("../hooks/useMaksBelop", () => ({ useMaksBelop: vi.fn() }));
vi.mock("../hooks/useKontonummerFinnes", () => ({ useKontonummerFinnes: vi.fn() }));
vi.mock("../hooks/useVedtak", () => ({ useVedtak: vi.fn() }));

const vedtak = {
  utfall: "INNVILGET",
  vedtaksdato: "20. oktober 2026",
  deltakerNavn: "Testperson",
  virksomhetsnavn: "Eksempel AS",
  tiltaksperiode: { fra: "1. november 2026", til: "31. januar 2027" },
} satisfies NonNullable<ReturnType<typeof useVedtak>["vedtak"]>;

function VedtakMottaker() {
  const { state } = useLocation();
  return (
    <>
      <p>V2-vedtak</p>
      <pre aria-label="Navigeringstilstand">{JSON.stringify(state)}</pre>
    </>
  );
}

const renderPage = (
  refusjon: RefusjonStatus | null,
  {
    maksBelopError = null,
    statusError = null,
    statusLoading = false,
    soknad = {
      virksomhet: { virksomhetsnummer: "123456789", virksomhetsnavn: "Eksempel AS" },
      ansatt: { fnr: "", navn: "Testperson" },
      ekspert: { navn: "Testekspert" },
      behovForBistand: { startdato: "2026-10-01" },
    },
    vedtakStatus = {},
  }: {
    maksBelopError?: Error | null;
    statusError?: Error | null;
    statusLoading?: boolean;
    soknad?: ReturnType<typeof useSoknad>["soknad"];
    vedtakStatus?: Partial<ReturnType<typeof useVedtak>>;
  } = {}
) => {
  const refreshStatus = vi.fn(async () => undefined);
  vi.mocked(useSoknad).mockReturnValue({
    soknad,
    isLoading: false,
    error: null,
  });
  vi.mocked(useRefusjonStatus).mockReturnValue({
    refusjon,
    isLoading: statusLoading,
    error: statusError,
    mutate: refreshStatus,
  });
  vi.mocked(useMaksBelop).mockReturnValue({
    maksBelopKroner: maksBelopError ? null : 24_900,
    isLoading: false,
    error: maksBelopError,
    mutate: vi.fn(),
  });
  vi.mocked(useKontonummerFinnes).mockReturnValue({
    finnes: true,
    isLoading: false,
    error: null,
  });
  vi.mocked(useVedtak).mockReturnValue({
    vedtak,
    isLoading: false,
    error: undefined,
    ...vedtakStatus,
  });

  const innhold = () => (
    <MemoryRouter initialEntries={["/v2/skjema/sak-1/refusjon"]}>
      <Routes>
        <Route path="/v2/skjema/:id/refusjon" element={<SokRefusjonPage />} />
        <Route path="/v2/skjema/:id/vedtak" element={<VedtakMottaker />} />
        <Route path="/v2/skjema/:id/kvittering" element={<p>V2-kvittering</p>} />
      </Routes>
    </MemoryRouter>
  );
  const view = render(innhold());
  return { ...view, refreshStatus, rerenderPage: () => view.rerender(innhold()) };
};

const fyllUtKrav = async (container: HTMLElement) => {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Beløp for refusjonskravet"), "1000");
  const filvelger = container.querySelector<HTMLInputElement>('input[type="file"]');
  if (!filvelger) throw new Error("Fant ikke filvelgeren for refusjonskravet");
  await user.upload(
    filvelger,
    new File(["%PDF-1.4\n%%EOF\n"], "kvittering.pdf", { type: "application/pdf" })
  );
  await user.click(
    screen.getByRole("checkbox", {
      name: "Jeg bekrefter at utgiftene som kreves refundert er betalt.",
    })
  );
  return user;
};

afterEach(() => {
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe("SokRefusjonPage", () => {
  it("viser fire synlige rader med vedtakets periode i stedet for søknadens startdato", () => {
    renderPage(null);

    expect(screen.getAllByRole("term").map((term) => term.textContent)).toEqual([
      "Arbeidsgiver",
      "Ansatt",
      "Ekspert",
      "Tiltaksperiode",
    ]);
    expect(screen.getAllByRole("definition").map((value) => value.textContent)).toEqual([
      "Eksempel AS",
      "Testperson",
      "Testekspert",
      "1. november 2026 – 31. januar 2027",
    ]);
    for (const term of screen.getAllByRole("term")) {
      expect(term.nextElementSibling?.tagName).toBe("DD");
    }
    expect(useVedtak).toHaveBeenCalledWith("sak-1");
    expect(screen.queryByText("Startdato")).not.toBeInTheDocument();
    expect(screen.queryByText("Opplysninger om saken")).not.toBeInTheDocument();
  });

  it("beholder rader med plassholder når navn og vedtaksperiode mangler", () => {
    renderPage(null, {
      soknad: { virksomhet: { virksomhetsnavn: " " }, ekspert: { navn: "" } },
      vedtakStatus: { vedtak: { ...vedtak, tiltaksperiode: null } },
    });

    expect(screen.getAllByRole("term")).toHaveLength(4);
    expect(screen.getAllByRole("definition").map((value) => value.textContent)).toEqual([
      "–",
      "–",
      "–",
      "–",
    ]);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it.each([403, 500])("viser vedtaksfeil %s uten å skjule refusjonsskjemaet", (status) => {
    renderPage(null, {
      vedtakStatus: {
        vedtak: undefined,
        error: new HttpError("Vedtaksoppslaget feilet", { status }),
      },
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Kunne ikke hente tiltaksperioden. Prøv å laste siden på nytt."
    );
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent("Kunne ikke hentes");
    expect(screen.getByLabelText("Beløp for refusjonskravet")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Send inn$/ })).toBeEnabled();
  });

  it("viser en egen melding når vedtaket ikke finnes", () => {
    renderPage(null, { vedtakStatus: { vedtak: null } });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Fant ikke vedtaket for denne søknaden. Prøv å laste siden på nytt."
    );
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent("Kunne ikke hentes");
    expect(screen.getByRole("button", { name: /^Send inn$/ })).toBeEnabled();
  });

  it("beholder utfylt krav mens vedtaket lastes, hentes og revalideres med feil", async () => {
    const { container, rerenderPage } = renderPage(null, {
      vedtakStatus: { vedtak: undefined, isLoading: true },
    });
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent("Laster tiltaksperiode");
    await fyllUtKrav(container);
    const belop = screen.getByLabelText("Beløp for refusjonskravet");

    vi.mocked(useVedtak).mockReturnValue({ vedtak, isLoading: false, error: undefined });
    rerenderPage();
    expect(screen.getByLabelText("Beløp for refusjonskravet")).toBe(belop);
    expect(belop).toHaveValue("1000");
    expect(screen.getByText("kvittering.pdf")).toBeInTheDocument();
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent(
      "1. november 2026 – 31. januar 2027"
    );

    vi.mocked(useVedtak).mockReturnValue({
      vedtak,
      isLoading: false,
      error: new HttpError("Vedtaksoppslaget feilet", { status: 500 }),
    });
    rerenderPage();
    expect(screen.getByLabelText("Beløp for refusjonskravet")).toBe(belop);
    expect(belop).toHaveValue("1000");
    expect(screen.getByText("kvittering.pdf")).toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", {
        name: "Jeg bekrefter at utgiftene som kreves refundert er betalt.",
      })
    ).toBeChecked();
    expect(screen.getByRole("button", { name: /^Send inn$/ })).toBeEnabled();
    expect(screen.getAllByRole("definition").at(-1)).toHaveTextContent("Kunne ikke hentes");
  });

  it("sender eksisterende krav til vedtakssiden uten ny innsendingsbekreftelse", () => {
    renderPage({
      status: "MOTTATT",
      vurdertTidspunkt: null,
      begrunnelse: null,
      belopKroner: 1000,
      opprettet: "2026-05-20T12:00:00",
      kontonummer: null,
      vedlegg: [],
    });

    expect(screen.getByText("V2-vedtak")).toBeInTheDocument();
    expect(screen.getByLabelText("Navigeringstilstand")).toHaveTextContent("null");
    expect(screen.queryByText("V2-kvittering")).not.toBeInTheDocument();
  });

  it("åpner nytt skjema med beskjed og sender alle opplysninger på nytt", async () => {
    const fetch = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 201 }));
    const { container, refreshStatus } = renderPage({
      status: "NY_INNSENDING_KREVES",
      vurdertTidspunkt: "2026-11-26T12:00:00Z",
      begrunnelse: "Send refusjonskravet på nytt. Vedleggene må være leselige.",
      belopKroner: 2000,
      opprettet: "2026-11-20T12:00:00Z",
      kontonummer: null,
      vedlegg: [{ id: "gammel-fil", filnavn: "gammel.pdf", storrelse: 100 }],
    });
    expect(screen.getByRole("heading", { name: "Send inn nytt refusjonskrav" })).toBeVisible();
    expect(screen.getByText(/Vedleggene må være leselige/)).toBeVisible();
    expect(screen.getByLabelText("Beløp for refusjonskravet")).toHaveValue("");
    expect(screen.queryByText("gammel.pdf")).not.toBeInTheDocument();
    const user = await fyllUtKrav(container);
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));
    await screen.findByText("V2-vedtak");
    const request = fetch.mock.calls.at(-1)?.[1]?.body;
    expect(request).toBeInstanceOf(FormData);
    expect((request as FormData).get("belop")).toBe("1000");
    expect((request as FormData).getAll("filer")).toHaveLength(1);
    expect(refreshStatus).toHaveBeenCalled();
  });

  it("returns an unsubmitted refund form to the v2 vedtak page", () => {
    renderPage(null);

    expect(screen.getByRole("heading", { level: 1, name: "Søk refusjon" })).toBeInTheDocument();
    expect(
      screen.queryByLabelText("Hvilke utgifter skal tilskuddet til ekspertbistand dekke?")
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Tilbake til avtalen/ })).toHaveAttribute(
      "href",
      "/v2/skjema/sak-1/vedtak"
    );
    expect(
      screen.getByText("Du kan kreve inntil 24 900 kr. Oppgi beløp i hele kroner, f.eks. 12500.")
    ).toBeInTheDocument();
  });

  it("viser feltfeil for beløp over innvilget beløp", async () => {
    const user = userEvent.setup();
    const submit = vi.spyOn(globalThis, "fetch");
    renderPage(null);

    await user.type(screen.getByLabelText("Beløp for refusjonskravet"), "24901");
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    expect(
      await screen.findAllByText("Beløpet kan ikke være høyere enn innvilget beløp (24 900 kr).")
    ).toHaveLength(2);
    expect(submit).not.toHaveBeenCalled();
  });

  it("viser melding i stedet for skjemaet når maksbeløpet ikke kan hentes", () => {
    renderPage(null, {
      maksBelopError: new HttpError("Refusjonskrav for denne saken sendes ikke inn her", {
        status: 409,
      }),
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Refusjonskrav for denne saken sendes ikke inn her"
    );
    expect(screen.queryByRole("button", { name: /^Send inn$/ })).not.toBeInTheDocument();
  });

  it("går direkte til vedtaket etter innsending og metadataoppslag", async () => {
    const user = userEvent.setup();
    const submit = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 201 }));
    const { container, refreshStatus } = renderPage(null);

    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));
    expect(await screen.findAllByText("Du må oppgi beløp for refusjonskravet.")).toHaveLength(2);
    await user.type(screen.getByLabelText("Beløp for refusjonskravet"), "1000");
    const fileInput = container.querySelector<HTMLInputElement>('input[type="file"]');
    if (!fileInput) throw new Error("Missing refund attachment input.");
    const file = new File(["%PDF-1.4\n%%EOF\n"], "kvittering.pdf", { type: "application/pdf" });
    await user.upload(fileInput, file);
    await user.click(
      screen.getByRole("checkbox", {
        name: "Jeg bekrefter at utgiftene som kreves refundert er betalt.",
      })
    );
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    await waitFor(() =>
      expect(submit).toHaveBeenCalledWith(EKSPERTBISTAND_REFUSJON_PATH("sak-1"), {
        method: "POST",
        body: expect.any(FormData),
      })
    );
    const formData = submit.mock.calls[0][1]?.body;
    expect(formData).toBeInstanceOf(FormData);
    if (!(formData instanceof FormData)) throw new Error("Missing refund form data.");
    expect(Array.from(formData.keys())).toEqual(["belop", "filer"]);
    expect(formData.has("utgifter")).toBe(false);
    expect(formData.get("belop")).toBe("1000");
    expect(formData.getAll("filer")).toEqual([file]);
    expect(await screen.findByText("V2-vedtak")).toBeInTheDocument();
    expect(screen.getByLabelText("Navigeringstilstand")).toHaveTextContent(
      JSON.stringify({ refusjonSendtInn: true })
    );
    expect(screen.queryByText("V2-kvittering")).not.toBeInTheDocument();
    expect(refreshStatus).toHaveBeenCalledTimes(1);
  });

  it("beholder beløp, filer og bekreftelse når innsendingen feiler", async () => {
    const submit = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        new Response(JSON.stringify({ message: "Kunne ikke ta imot kravet" }), { status: 500 })
      );
    const { container, refreshStatus } = renderPage(null);
    const user = await fyllUtKrav(container);
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Kunne ikke ta imot kravet");
    expect(screen.getByLabelText("Beløp for refusjonskravet")).toHaveValue("1000");
    expect(screen.getByText("kvittering.pdf")).toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", {
        name: "Jeg bekrefter at utgiftene som kreves refundert er betalt.",
      })
    ).toBeChecked();
    expect(refreshStatus).not.toHaveBeenCalled();
    expect(screen.queryByText("V2-vedtak")).not.toBeInTheDocument();

    submit.mockResolvedValue(new Response(null, { status: 201 }));
    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));
    expect(await screen.findByText("V2-vedtak")).toBeInTheDocument();
    expect(submit).toHaveBeenCalledTimes(2);
  });

  it("venter på bekreftet mottak fra backend før navigering", async () => {
    let bekreftMottak = (_response: Response) => {};
    const mottak = new Promise<Response>((resolve) => {
      bekreftMottak = resolve;
    });
    vi.spyOn(globalThis, "fetch").mockReturnValue(mottak);
    const { container, refreshStatus } = renderPage(null);
    const user = await fyllUtKrav(container);

    try {
      await user.click(screen.getByRole("button", { name: /^Send inn$/ }));
      expect(screen.getByRole("heading", { name: "Søk refusjon" })).toBeInTheDocument();
      expect(refreshStatus).not.toHaveBeenCalled();
      expect(screen.queryByText("V2-vedtak")).not.toBeInTheDocument();
    } finally {
      bekreftMottak(new Response(null, { status: 201 }));
    }

    expect(await screen.findByText("V2-vedtak")).toBeInTheDocument();
    expect(refreshStatus).toHaveBeenCalledTimes(1);
  });

  it("viser ikke skjemaet mens refusjonsstatus lastes", () => {
    renderPage(null, { statusLoading: true });

    expect(screen.getByTitle("Laster søknad")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Send inn$/ })).not.toBeInTheDocument();
  });

  it.each([403, 404, 500])("viser statusfeil %s i stedet for et nytt skjema", (status) => {
    renderPage(null, { statusError: new HttpError("Statusoppslaget feilet", { status }) });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Kunne ikke hente status for refusjonskravet. Prøv å laste siden på nytt."
    );
    expect(screen.queryByRole("button", { name: /^Send inn$/ })).not.toBeInTheDocument();
    expect(screen.queryByText("V2-vedtak")).not.toBeInTheDocument();
  });

  it("keeps amount, paid-expense confirmation and attachment required", async () => {
    const user = userEvent.setup();
    const submit = vi.spyOn(globalThis, "fetch");
    renderPage(null);

    await user.click(screen.getByRole("button", { name: /^Send inn$/ }));

    expect(await screen.findAllByText("Du må oppgi beløp for refusjonskravet.")).toHaveLength(2);
    expect(screen.getAllByText("Du må bekrefte at utgiftene er betalt.")).toHaveLength(2);
    expect(screen.getAllByText("Du må laste opp minst én fil.")).toHaveLength(2);
    expect(submit).not.toHaveBeenCalled();
  });
});
