import useSWR from "swr";
import { fetchJson } from "../utils/api";
import { draftDtoServerSchema } from "../features/soknad/server-schemas";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";

export type Soknad = ReturnType<typeof draftDtoServerSchema.parse>;

export function useSoknad(id: string | undefined) {
  const { data, error, isLoading } = useSWR(id ? `${EKSPERTBISTAND_API_PATH}/${id}` : null, (url) =>
    fetchJson<unknown>(url).then((raw) => draftDtoServerSchema.parse(raw))
  );

  return { soknad: data ?? null, isLoading, error: error ?? null };
}
