import { useState } from "react";
import { useSWRConfig } from "swr";
import { SAKSBEHANDLING_VILKARSVURDERING_URL } from "../utils/constants";
import { fetchJson } from "../utils/api";
import { HttpError } from "../utils/http";
import type { VilkarId, VilkarsvurderingDTO, Vilkårstatus } from "./useVilkår";

export type VilkårsvurderingInput = {
  vilkårId: VilkarId;
  status: Exclude<Vilkårstatus, "ikke_vurdert">;
  notat?: string;
};

function feilmelding(e: unknown): Error {
  if (e instanceof HttpError) {
    switch (e.status) {
      case 403:
        return new Error(e.begrunnelse ?? "Du har ikke tilgang til å vurdere vilkår i denne saken.");
      case 409:
        return new Error("Saken er ikke under behandling, og vilkårene kan ikke endres.");
    }
  }
  return e instanceof Error ? e : new Error("Kunne ikke lagre vurderingen.");
}

export function useVilkårsvurdering(sakId: string) {
  const { mutate } = useSWRConfig();
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const lagreVurdering = async ({ vilkårId, status, notat }: VilkårsvurderingInput) => {
    setIsSaving(true);
    setError(null);

    try {
      const oppdatert = await fetchJson<VilkarsvurderingDTO>(
        SAKSBEHANDLING_VILKARSVURDERING_URL(sakId),
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ vilkar: vilkårId, godkjent: status === "oppfylt", notat }),
        }
      );

      if (!oppdatert) {
        throw new Error("Fikk ikke oppdatert vilkår fra serveren.");
      }

      await mutate<VilkarsvurderingDTO[]>(
        SAKSBEHANDLING_VILKARSVURDERING_URL(sakId),
        (vurderinger) => vurderinger?.map((v) => (v.vilkar === vilkårId ? oppdatert : v)),
        { revalidate: false }
      );

      return true;
    } catch (e) {
      setError(feilmelding(e));
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  return { lagreVurdering, isSaving, error };
}
