import useSWR from "swr";
import { SAKSBEHANDLING_VILKAR_LISTE_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type Vilkårstatus = "oppfylt" | "ikke_oppfylt" | "ikke_vurdert";

export type Vilkårsvurdering = {
  automatisk: boolean;
  status: Vilkårstatus;
  kommentar?: string;
  vurdertAv?: string;
  vurdertTidspunkt?: string;
};

export type Vilkår = {
  id: string;
  tittel: string;
  beskrivelse: string;
  vurdering: Vilkårsvurdering;
};

export function useVilkår(sakId: string | undefined) {
  const { data, error, isLoading } = useSWR<Vilkår[], HttpError>(
    sakId ? SAKSBEHANDLING_VILKAR_LISTE_URL(sakId) : null,
    {
      revalidateOnFocus: false,
    }
  );

  return { vilkår: data ?? [], error, isLoading };
}
