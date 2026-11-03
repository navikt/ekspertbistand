import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { SWRConfig } from "swr";
import { describe, expect, it } from "vitest";
import { useSoknader as useV1Soknader } from "../../hooks/useSoknader";
import { useSoknader as useV2Soknader } from "./useSoknader";

const wrapper = ({ children }: { children: ReactNode }) => (
  <SWRConfig value={{ provider: () => new Map(), shouldRetryOnError: false }}>{children}</SWRConfig>
);

describe("overview cache isolation", () => {
  it("keeps version-specific links when both overviews share the same SWR provider", async () => {
    const { result } = renderHook(() => ({ v1: useV1Soknader(), v2: useV2Soknader() }), {
      wrapper,
    });

    await waitFor(() => {
      expect(result.current.v1.loading).toBe(false);
      expect(result.current.v2.loading).toBe(false);
      expect(result.current.v1.soknader.length).toBeGreaterThan(0);
      expect(result.current.v2.soknader.length).toBeGreaterThan(0);
    });

    expect(result.current.v2.soknader.map(({ id }) => id)).toEqual(
      result.current.v1.soknader.map(({ id }) => id)
    );
    for (const application of result.current.v1.soknader) {
      expect(application.href).toMatch(/^\/skjema\//);
    }
    for (const application of result.current.v2.soknader) {
      expect(application.href).toMatch(/^\/v2\/skjema\//);
    }
  });
});
