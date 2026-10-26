import { act, renderHook, waitFor } from "@testing-library/react";
import { SWRConfig } from "swr";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { fetchJson } from "../utils/api";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { formatDateToIso, startOfToday } from "../utils/date";
import { useMaksSats } from "./useMaksSats";

vi.mock("../utils/api", () => ({ fetchJson: vi.fn() }));

const wrapper = ({ children }: { children: ReactNode }) => (
  <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0, shouldRetryOnError: false }}>
    {children}
  </SWRConfig>
);

beforeEach(() => vi.resetAllMocks());

describe("useMaksSats", () => {
  it("henter dagens sats når startdato mangler", async () => {
    vi.mocked(fetchJson).mockResolvedValue({ gyldigFra: "2026-01-01", belop: 24_900 });

    const { result } = renderHook(() => useMaksSats(null), { wrapper });
    await waitFor(() => expect(result.current.maksSats?.belop).toBe(24_900));
    expect(fetchJson).toHaveBeenCalledWith(
      `${EKSPERTBISTAND_API_PATH}/maks-sats?startdato=${formatDateToIso(startOfToday())}`
    );
  });

  it("bruker satsen for ny startdato og viser ikke gammel sats mens den hentes", async () => {
    let svarForNesteAar: (sats: { gyldigFra: string; belop: number }) => void = () => {};
    vi.mocked(fetchJson).mockImplementation((url) =>
      url.includes("2027-01-01")
        ? new Promise((resolve) => {
            svarForNesteAar = resolve;
          })
        : Promise.resolve({ gyldigFra: "2026-01-01", belop: 24_900 })
    );

    const { result, rerender } = renderHook(({ dato }) => useMaksSats(dato), {
      wrapper,
      initialProps: { dato: "2026-12-31" },
    });
    await waitFor(() => expect(result.current.maksSats?.belop).toBe(24_900));

    rerender({ dato: "2027-01-01" });
    expect(result.current.maksSats).toBeNull();
    expect(fetchJson).toHaveBeenCalledWith(
      `${EKSPERTBISTAND_API_PATH}/maks-sats?startdato=2027-01-01`
    );
    await act(async () => svarForNesteAar({ gyldigFra: "2027-01-01", belop: 26_000 }));
    await waitFor(() => expect(result.current.maksSats?.belop).toBe(26_000));
  });

  it("ignorerer svar for en tidligere startdato som kommer sent", async () => {
    let svarForGammelDato: (sats: { gyldigFra: string; belop: number }) => void = () => {};
    vi.mocked(fetchJson).mockImplementation((url) =>
      url.includes("2026-12-31")
        ? new Promise((resolve) => {
            svarForGammelDato = resolve;
          })
        : Promise.resolve({ gyldigFra: "2027-01-01", belop: 26_000 })
    );

    const { result, rerender } = renderHook(({ dato }) => useMaksSats(dato), {
      wrapper,
      initialProps: { dato: "2026-12-31" },
    });
    await waitFor(() => expect(fetchJson).toHaveBeenCalledTimes(1));

    rerender({ dato: "2027-01-01" });
    await waitFor(() => expect(result.current.maksSats?.belop).toBe(26_000));
    await act(async () => svarForGammelDato({ gyldigFra: "2026-01-01", belop: 24_900 }));
    expect(result.current.maksSats?.belop).toBe(26_000);
  });

  it("blokkerer satsen ved feil og lar brukeren prøve igjen", async () => {
    vi.mocked(fetchJson)
      .mockRejectedValueOnce(new Error("Utilgjengelig"))
      .mockResolvedValueOnce({ gyldigFra: "2026-01-01", belop: 24_900 });

    const { result } = renderHook(() => useMaksSats("2026-12-31"), { wrapper });
    await waitFor(() => expect(result.current.error).toBeTruthy());
    expect(result.current.maksSats).toBeNull();

    await act(async () => {
      await result.current.mutate();
    });
    await waitFor(() => expect(result.current.maksSats?.belop).toBe(24_900));
  });
});
