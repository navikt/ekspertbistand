import { renderHook, waitFor } from "@testing-library/react";
import { SWRConfig } from "swr";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { EKSPERTBISTAND_REFUSJON_PATH } from "../utils/constants";
import { useMaksBelop } from "./useMaksBelop";

const wrapper = ({ children }: { children: ReactNode }) => (
  <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0, shouldRetryOnError: false }}>
    {children}
  </SWRConfig>
);

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("useMaksBelop", () => {
  it("henter maksbeløpet for søknaden", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ maksBelopKroner: 24_900 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { result } = renderHook(() => useMaksBelop("sak-1"), { wrapper });

    await waitFor(() => expect(result.current.maksBelopKroner).toBe(24_900));
    expect(fetchMock).toHaveBeenCalledWith(`${EKSPERTBISTAND_REFUSJON_PATH("sak-1")}/maksbelop`, {
      headers: { Accept: "application/json" },
    });
  });

  it("beholder feilmeldingen og statuskoden ved 409", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("Refusjonskrav for denne saken sendes ikke inn her", { status: 409 })
    );

    const { result } = renderHook(() => useMaksBelop("sak-1"), { wrapper });

    await waitFor(() => expect(result.current.error).toBeTruthy());
    expect(result.current.error).toMatchObject({
      message: "Refusjonskrav for denne saken sendes ikke inn her",
      status: 409,
    });
    expect(result.current.maksBelopKroner).toBeNull();
  });
});
