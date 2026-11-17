import { type ReactNode, useCallback, useMemo, useRef, useState } from "react";
import useSWR from "swr";
import useSWRMutation from "swr/mutation";
import { createEmptyInputs, type SoknadInputs } from "../features/soknad/schema";
import { buildDraftPayload, draftDtoToInputs, type DraftDto } from "../features/soknad/payload";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { fetchJson } from "../../utils/api";
import {
  type Lagringsfeil,
  SoknadDraftContext,
  type SoknadDraftContextValue,
} from "../context/SoknadDraftContext";
import UgyldigSkjemaPage from "../pages/UgyldigSkjemaPage";
import { HttpError } from "../../utils/http";

const mergeSnapshotIntoDraftDto = (
  snapshot: SoknadInputs,
  draftId: string,
  current: DraftDto | null | undefined,
  persistedIso?: string
): DraftDto => {
  const payload = buildDraftPayload(snapshot);
  // PATCH endrer ikke deltaker eller virksomhet. Det gjør bare valider-deltaker.
  return {
    ...current,
    ...payload,
    ansatt: current?.ansatt ?? null,
    virksomhet: {
      ...current?.virksomhet,
      ...payload.virksomhet,
      virksomhetsnummer: current?.virksomhet?.virksomhetsnummer,
      virksomhetsnavn: current?.virksomhet?.virksomhetsnavn,
    },
    id: current?.id ?? draftId,
    status: current?.status ?? "utkast",
    opprettetTidspunkt: persistedIso ?? current?.opprettetTidspunkt ?? null,
  };
};

const tilLagringsfeil = (e: unknown): Lagringsfeil =>
  e instanceof HttpError
    ? { message: e.message, status: e.status, kode: e.code ?? null, felt: e.field ?? null }
    : { message: "Prøv igjen senere.", status: null, kode: null, felt: null };

export function SoknadDraftProvider({
  draftId,
  children,
}: {
  draftId: string | null;
  children: ReactNode;
}) {
  const draftUrl = `${EKSPERTBISTAND_API_PATH}/${draftId}`;
  const {
    data: draftDto,
    error,
    isLoading,
    mutate,
  } = useSWR<DraftDto | null, Error>(draftId ? draftUrl : null);

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

  const [lagringsfeil, setLagringsfeil] = useState<Lagringsfeil | null>(null);
  const sisteLagring = useRef(0);
  const pagaendeLagring = useRef<{ snapshot: SoknadInputs | null; resultat: Promise<boolean> }>({
    snapshot: null,
    resultat: Promise.resolve(true),
  });

  const saveDraft = useCallback(
    (snapshot: SoknadInputs) => {
      if (!draftId || draftDto?.status === "innsendt") return;
      const lagring = ++sisteLagring.current;
      const optimisticPersistedIso = new Date().toISOString();
      const resultat = persistDraft(snapshot, {
        optimisticData: (current) =>
          mergeSnapshotIntoDraftDto(snapshot, draftId, current, optimisticPersistedIso),
        rollbackOnError: true,
        revalidate: false,
        populateCache: false,
      }).then(
        () => {
          if (lagring === sisteLagring.current) setLagringsfeil(null);
          return true;
        },
        (e: unknown) => {
          if (lagring === sisteLagring.current) {
            setLagringsfeil(tilLagringsfeil(e));
          }
          return false;
        }
      );
      pagaendeLagring.current = { snapshot, resultat };
    },
    [draftDto?.status, draftId, persistDraft]
  );

  const ventPaLagring = useCallback(async (): Promise<boolean> => {
    // Venter på nye lagringer som starter mens vi venter, så resultatet gjelder den siste.
    const vent = async () => {
      let lagring;
      let ok;
      do {
        lagring = pagaendeLagring.current;
        ok = await lagring.resultat;
      } while (lagring !== pagaendeLagring.current);
      return { ok, snapshot: lagring.snapshot };
    };
    const { ok, snapshot } = await vent();
    if (ok || !snapshot) return ok;
    saveDraft(snapshot);
    return (await vent()).ok;
  }, [saveDraft]);

  const clearDraft = useCallback(async () => {
    if (!draftId) return;
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
  }, [deleteDraft, draftId]);

  const emptyInputs = useMemo(() => createEmptyInputs(), []);
  const draft = draftDto ? draftDtoToInputs(draftDto) : emptyInputs;
  const status = draftDto?.status ?? null;
  const hydrated = draftId === null || (!isLoading && draftDto !== undefined);
  const lastPersistedAt = useMemo(() => {
    const persistedIso = draftDto?.innsendtTidspunkt ?? draftDto?.opprettetTidspunkt ?? null;
    return persistedIso ? new Date(persistedIso) : null;
  }, [draftDto]);

  const bekreftetFnr = draftDto?.ansatt?.fnr;
  const bekreftetVirksomhetsnummer = draftDto?.virksomhet?.virksomhetsnummer;
  const bekreftetDeltaker = useMemo(
    () =>
      bekreftetFnr && bekreftetVirksomhetsnummer
        ? { fnr: bekreftetFnr, virksomhetsnummer: bekreftetVirksomhetsnummer }
        : null,
    [bekreftetFnr, bekreftetVirksomhetsnummer]
  );
  const opprettetAv = draftDto?.opprettetAv ?? null;

  const oppdaterUtkast = useCallback(
    async (utkast: DraftDto) => {
      await mutate(utkast, { revalidate: false });
    },
    [mutate]
  );

  const saveError = lagringsfeil?.message ?? null;

  const value = useMemo<SoknadDraftContextValue>(
    () => ({
      draftId,
      draft,
      hydrated,
      status,
      saveDraft,
      ventPaLagring,
      clearDraft,
      lastPersistedAt,
      saveError,
      lagringsfeil,
      bekreftetDeltaker,
      opprettetAv,
      oppdaterUtkast,
    }),
    [
      bekreftetDeltaker,
      clearDraft,
      draft,
      draftId,
      hydrated,
      lagringsfeil,
      lastPersistedAt,
      opprettetAv,
      oppdaterUtkast,
      saveDraft,
      saveError,
      status,
      ventPaLagring,
    ]
  );

  if (error instanceof HttpError && (error.status === 400 || error.status === 404)) {
    return <UgyldigSkjemaPage message={error.message} />;
  }

  return <SoknadDraftContext.Provider value={value}>{children}</SoknadDraftContext.Provider>;
}
