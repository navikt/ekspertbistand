import useSWRImmutable from "swr/immutable";
import type { Organisasjon } from "@navikt/virksomhetsvelger";
import { EKSPERTBISTAND_ORGANISASJONER_PATH } from "../utils/constants";

type OrganisasjonResponse = { hierarki?: Organisasjon[] };

export const useOrganisasjoner = () => {
  const { data, error, isLoading } = useSWRImmutable<OrganisasjonResponse>(
    EKSPERTBISTAND_ORGANISASJONER_PATH
  );

  return {
    organisasjoner: data?.hierarki ?? [],
    error,
    isLoading,
  };
};
