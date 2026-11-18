import { useMemo } from "react";
import useSWR from "swr";
import { SAKSBEHANDLING_VILKARSVURDERING_URL } from "../utils/constants";
import { HttpError } from "../utils/http";

export type VilkarId =
  | "DELTAKER_HAR_ARBEIDSFORHOLD"
  | "FYLLES_UT_I_SAMRAD_GODKJENT"
  | "ARBEIDSGIVER_HAR_PROVD_TILRETTELEGGING"
  | "DELTAKER_HAR_SYKEFRAVAERSHISTORIKK"
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
  /** Forhåndsutfylt av systemet og ikke lagret av saksbehandler. */
  automatisk: boolean;
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
  DELTAKER_HAR_ARBEIDSFORHOLD: {
    tittel: "Arbeidsforhold",
    beskrivelse:
      "Deltaker må ha et arbeidsforhold i virksomheten som står i søknaden. Kontroller i Aa-registeret at arbeidsforholdet er registrert på riktig virksomhet.",
  },
  FYLLES_UT_I_SAMRAD_GODKJENT: {
    tittel: "Deltaker er enig",
    beskrivelse:
      "Deltaker er enig i at tilskudd til ekspertbistand er hensiktsmessig. Arbeidsgiver må bekrefte dette for at søknaden skal kunne sendes inn.",
  },
  ARBEIDSGIVER_HAR_PROVD_TILRETTELEGGING: {
    tittel: "Prøvd tilrettelegging",
    beskrivelse:
      "Arbeidsgiver har beskrevet hvilke relevante tilretteleggingstiltak som er prøvd eller vurdert i tråd med tilretteleggingsplikten etter arbeidsmiljøloven § 4-6 første ledd.",
  },
  DELTAKER_HAR_SYKEFRAVAERSHISTORIKK: {
    tittel: "Sykefraværshistorikk",
    beskrivelse:
      "Må ha hatt legemeldt sykefravær mer enn én gang. Vurder om fraværet fremstår som hyppig eller gjentakende. Ved kun to sykefraværsperioder bør minst én være av en viss varighet. Vurderingen er skjønnsmessig.",
  },
  EKSPERT_HAR_KOMPETANSE: {
    tittel: "Ekspertens kompetanse og uavhengighet",
    beskrivelse: "Må ha offentlig godkjent utdanning og relevant arbeidsrelatert kompetanse.",
  },
};

/**
 * Opprettes som oppfylt uten ident og tidspunkt. Speiler `automatiskOppfylteVilkar` i backend.
 * Se `specifications/automatisk_vilkarsvurdering.md`.
 */
export const AUTOMATISK_OPPFYLTE_VILKAR: ReadonlySet<VilkarId> = new Set<VilkarId>([
  "DELTAKER_HAR_ARBEIDSFORHOLD",
  "FYLLES_UT_I_SAMRAD_GODKJENT",
]);

// Manuell lagring setter alltid ident og tidspunkt, så urørte standardverdier mangler begge.
const erAutomatisk = (dto: VilkarsvurderingDTO) =>
  AUTOMATISK_OPPFYLTE_VILKAR.has(dto.vilkar) &&
  dto.godkjent === true &&
  !dto.vurdertAvIdent &&
  !dto.vurdertTidspunkt;

const tilStatus = (godkjent: boolean | null | undefined): Vilkårstatus =>
  godkjent === true ? "oppfylt" : godkjent === false ? "ikke_oppfylt" : "ikke_vurdert";

export const tilVilkår = (dto: VilkarsvurderingDTO): Vilkår => ({
  id: dto.vilkar,
  ...VILKAR_TEKSTER[dto.vilkar],
  vurdering: {
    status: tilStatus(dto.godkjent),
    automatisk: erAutomatisk(dto),
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
