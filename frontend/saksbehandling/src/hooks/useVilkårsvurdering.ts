import { useState } from "react";
import { useSWRConfig } from "swr";
import { SAKSBEHANDLING_SAK_URL, SAKSBEHANDLING_VILKAR_URL } from "../utils/constants";
import { fetchJson } from "../utils/api";
import type { SakDetaljer, Vilkår, Vilkårstatus } from "./useSak";

export type VilkårsvurderingInput = {
  vilkårId: string;
  status: Exclude<Vilkårstatus, "ikke_vurdert">;
  kommentar?: string;
};

export function useVilkårsvurdering(sakId: string) {
  const { mutate } = useSWRConfig();
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const lagreVurdering = async ({ vilkårId, status, kommentar }: VilkårsvurderingInput) => {
    setIsSaving(true);
    setError(null);

    try {
      const oppdatert = await fetchJson<Vilkår>(SAKSBEHANDLING_VILKAR_URL(sakId, vilkårId), {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status, kommentar }),
      });

      if (!oppdatert) {
        throw new Error("Fikk ikke oppdatert vilkår fra serveren.");
      }

      await mutate<SakDetaljer>(
        SAKSBEHANDLING_SAK_URL(sakId),
        (sak) =>
          sak && {
            ...sak,
            vilkår: sak.vilkår.map((v) => (v.id === vilkårId ? oppdatert : v)),
          },
        { revalidate: false }
      );

      return true;
    } catch (e) {
      setError(e instanceof Error ? e : new Error("Kunne ikke lagre vurderingen."));
      return false;
    } finally {
      setIsSaving(false);
    }
  };

  return { lagreVurdering, isSaving, error };
}
