import { useMemo } from "react";
import useSWR from "swr";
import { SAKSBEHANDLING_VILKARSVURDERING_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type VilkarId =
  | "HAR_ARBEIDSFORHOLD"
  | "FYLLES_UT_I_SAMRAD_GODKJENT"
  | "HAR_PROVD_TILRETTELEGGING"
  | "HAR_SYKEFRAVAERSHISTORIKK"
  | "EKSPERT_HAR_KOMPETANSE";

/** Speiler `VilkarsvurderingDTO` i backend. `godkjent = null` betyr ikke vurdert. */
export type VilkarsvurderingDTO = {
  vilkar: VilkarId;
  godkjent?: boolean | null;
  notat?: string | null;
  vurdertAvIdent?: string | null;
  vurdertTidspunkt?: string | null;
};

export type Vilkårstatus = "oppfylt" | "ikke_oppfylt" | "ikke_vurdert";

export type Vilkårsvurdering = {
  status: Vilkårstatus;
  notat?: string;
  vurdertAvIdent?: string;
  vurdertTidspunkt?: string;
};

export type Vilkår = {
  id: VilkarId;
  tittel: string;
  beskrivelse: string;
  vurdering: Vilkårsvurdering;
};

export const VILKAR_TEKSTER: Record<VilkarId, { tittel: string; beskrivelse: string }> = {
  HAR_ARBEIDSFORHOLD: {
    tittel: "Arbeidsforhold",
    beskrivelse: "Deltaker må ha et arbeidsforhold hos arbeidsgiver i Aa-reg",
  },
  FYLLES_UT_I_SAMRAD_GODKJENT: {
    tittel: "Deltaker er enig",
    beskrivelse: "Arbeidsgiver har oppgitt at deltaker gitt samtykke til at søknaden sendtes.",
  },
  HAR_PROVD_TILRETTELEGGING: {
    tittel: "Prøvd tilrettelegging",
    beskrivelse: "Arbeidsgiver har beskrevet hvilke tiltak de prøvd eller vurdert.",
  },
  HAR_SYKEFRAVAERSHISTORIKK: {
    tittel: "Sykefraværshistorikk",
    beskrivelse: "Må ha legemeldt sykefravær som er hyppig eller gjentakerende.",
  },
  EKSPERT_HAR_KOMPETANSE: {
    tittel: "Ekspertens kompetanse og uavhengighet",
    beskrivelse: "Må ha offentlig godkjent utdanning og relevant arbeidsrelatert kompentanse.",
  },
};

const tilStatus = (godkjent: boolean | null | undefined): Vilkårstatus =>
  godkjent === true ? "oppfylt" : godkjent === false ? "ikke_oppfylt" : "ikke_vurdert";

export const tilVilkår = (dto: VilkarsvurderingDTO): Vilkår => ({
  id: dto.vilkar,
  ...VILKAR_TEKSTER[dto.vilkar],
  vurdering: {
    status: tilStatus(dto.godkjent),
    notat: dto.notat ?? undefined,
    vurdertAvIdent: dto.vurdertAvIdent ?? undefined,
    vurdertTidspunkt: dto.vurdertTidspunkt ?? undefined,
  },
});

export function useVilkår(sakId: string | undefined) {
  const { data, error, isLoading } = useSWR<VilkarsvurderingDTO[], HttpError>(
    sakId ? SAKSBEHANDLING_VILKARSVURDERING_URL(sakId) : null,
    {
      revalidateOnFocus: false,
    }
  );

  const vilkår = useMemo(
    () => (data ?? []).filter((dto) => dto.vilkar in VILKAR_TEKSTER).map(tilVilkår),
    [data]
  );

  return { vilkår, error, isLoading };
}
