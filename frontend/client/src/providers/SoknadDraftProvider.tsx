import { type ReactNode, useCallback, useMemo } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";
import { createEmptyInputs, type SoknadInputs } from "../features/soknad/schema";
import { buildDraftPayload, draftDtoToInputs, type DraftDto } from "../features/soknad/payload";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { fetchJson } from "../utils/api";
import { SoknadDraftContext, type SoknadDraftContextValue } from "../context/SoknadDraftContext";
import UgyldigSkjemaPage from "../pages/UgyldigSkjemaPage";
import { HttpError } from "../utils/http";

const mergeSnapshotIntoDraftDto = (
  snapshot: SoknadInputs,
  draftId: string,
  current: DraftDto | null | undefined,
  persistedIso?: string
): DraftDto => {
  const payload = buildDraftPayload(snapshot);
  return {
    ...current,
    ...payload,
    id: current?.id ?? draftId,
    status: current?.status ?? "utkast",
    opprettetTidspunkt: persistedIso ?? current?.opprettetTidspunkt ?? null,
  };
};

export function SoknadDraftProvider({
  draftId,
  children,
}: {
  draftId: string;
  children: ReactNode;
}) {
  const draftUrl = `${EKSPERTBISTAND_API_PATH}/${draftId}`;
  const { data: draftDto, error, isLoading } = useSWR<DraftDto | null, Error>(draftUrl);

  const { trigger: persistDraft } = useSWRMutation<DraftDto | null, Error, string, SoknadInputs>(
    draftUrl,
    async (url, { arg }) =>
      fetchJson<DraftDto | null>(url, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(buildDraftPayload(arg)),
      })
  );

  const { trigger: deleteDraft } = useSWRMutation<DraftDto | null, Error, string, void>(
    draftUrl,
    (url) =>
      fetchJson<DraftDto | null>(url, {
        method: "DELETE",
      })
  );

  const saveDraft = useCallback(
    (snapshot: SoknadInputs) => {
      if (draftDto?.status === "innsendt") return;
      const optimisticPersistedIso = new Date().toISOString();
      void persistDraft(snapshot, {
        optimisticData: (current) =>
          mergeSnapshotIntoDraftDto(snapshot, draftId, current, optimisticPersistedIso),
        rollbackOnError: true,
        revalidate: false,
        populateCache: false,
      }).catch(() => undefined);
    },
    [draftDto?.status, draftId, persistDraft]
  );

  const clearDraft = useCallback(async () => {
    try {
      await deleteDraft(undefined, {
        optimisticData: () => null,
        rollbackOnError: true,
        revalidate: false,
        populateCache: true,
      });
    } catch {
      // ignore – delete best-effort only
    }
  }, [deleteDraft]);

  const emptyInputs = useMemo(() => createEmptyInputs(), []);
  const draft = draftDto ? draftDtoToInputs(draftDto) : emptyInputs;
  const status = draftDto?.status ?? null;
  const hydrated = !isLoading && draftDto !== undefined;
  const lastPersistedAt = useMemo(() => {
    const persistedIso = draftDto?.innsendtTidspunkt ?? draftDto?.opprettetTidspunkt ?? null;
    return persistedIso ? new Date(persistedIso) : null;
  }, [draftDto]);

  const value = useMemo<SoknadDraftContextValue>(
    () => ({
      draftId,
      draft,
      hydrated,
      status,
      saveDraft,
      clearDraft,
      lastPersistedAt,
    }),
    [clearDraft, draft, draftId, hydrated, lastPersistedAt, saveDraft, status]
  );

  if (error instanceof HttpError && (error.status === 400 || error.status === 404)) {
    return <UgyldigSkjemaPage message={error.message} />;
  }

  return <SoknadDraftContext.Provider value={value}>{children}</SoknadDraftContext.Provider>;
}
