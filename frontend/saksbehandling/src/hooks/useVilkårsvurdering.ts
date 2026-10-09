import { useState } from "react";
import { useSWRConfig } from "swr";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import { SAKSBEHANDLING_VILKARSVURDERING_URL } from "../utils/constants";
import { fetchJson } from "../utils/api";
import { HttpError } from "../utils/http";
import type { VilkarId, VilkarsvurderingDTO, Vilkårstatus } from "./useVilkår";

// API-et svarer 202 før handleren har lagret vurderingen (EventManager poller hvert 100. ms).
// Revaliderer vi med en gang, kan svaret overskrive den optimistiske tilstanden med den gamle.
const REVALIDER_ETTER_MS = 500;

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
  const { mutate, cache } = useSWRConfig();
  const innloggetAnsatt = useInnloggetAnsatt();
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const url = SAKSBEHANDLING_VILKARSVURDERING_URL(sakId);

  const lagreVurdering = async ({ vilkårId, status, notat }: VilkårsvurderingInput) => {
    setIsSaving(true);
    setError(null);

    const foer = cache.get(url)?.data as VilkarsvurderingDTO[] | undefined;
    const oppdatert: VilkarsvurderingDTO = {
      vilkar: vilkårId,
      godkjent: status === "oppfylt",
      notat: notat?.trim() || null,
      vurdertAvIdent: innloggetAnsatt?.id ?? null,
      vurdertTidspunkt: new Date().toISOString(),
    };
    await mutate<VilkarsvurderingDTO[]>(
      url,
      (vurderinger) => vurderinger?.map((v) => (v.vilkar === vilkårId ? oppdatert : v)),
      { revalidate: false }
    );

    try {
      await fetchJson(url, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ vilkar: vilkårId, godkjent: status === "oppfylt", notat }),
      });
      setTimeout(() => void mutate(url), REVALIDER_ETTER_MS);
      return true;
    } catch (e) {
      await mutate(url, foer, { revalidate: false });
      void mutate(url);
      setError(feilmelding(e));
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  return { lagreVurdering, isSaving, error };
}
