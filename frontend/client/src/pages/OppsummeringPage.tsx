import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useFormContext } from "react-hook-form";
import { ArrowLeftIcon, PaperplaneIcon } from "@navikt/aksel-icons";
import {
  Alert,
  BodyLong,
  BodyShort,
  Box,
  Button,
  ErrorSummary,
  FormProgress,
  Heading,
  HGrid,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import type { Inputs } from "./types";
import { STEP1_FIELDS, STEP2_FIELDS } from "./types";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { validateInputs, type ValidationError } from "./validation";
import { DraftActions } from "../components/DraftActions.tsx";
import { FocusedErrorSummary } from "../components/FocusedErrorSummary";
import { buildSkjemaPayload } from "../utils/soknadPayload";
import { SoknadSummary } from "../components/SoknadSummary";
import { withPreventDefault } from "./utils.ts";
import { APPLICATIONS_PATH, EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { parseErrorMessage } from "../utils/http";
import { useErrorFocus } from "../hooks/useErrorFocus";
import { useDraftNavigation } from "../hooks/useDraftNavigation";
import { BackLink } from "../components/BackLink";

export default function OppsummeringPage() {
  const navigate = useNavigate();
  const { draftId, draft: formData, clearDraft, lastPersistedAt } = useSoknadDraft();
  const form = useFormContext<Inputs>();
  const [submitErrors, setSubmitErrors] = useState<ValidationError[]>([]);
  const { focusKey: errorFocusKey, bumpFocusKey } = useErrorFocus();
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const navigateWithDraft = useDraftNavigation();

  const navigateTo = useCallback(
    (path: string) => {
      setSubmitErrors([]);
      navigateWithDraft(path, formData);
    },
    [formData, navigateWithDraft]
  );
  const step1Path = `/skjema/${draftId}/steg-1`;
  const step2Path = `/skjema/${draftId}/steg-2`;
  const goToStep1 = withPreventDefault(() => navigateTo(step1Path));
  const goToStep2 = withPreventDefault(() => navigateTo(step2Path));

  const handleSubmit = useCallback(async () => {
    const valid = await form.trigger([...STEP1_FIELDS, ...STEP2_FIELDS], { shouldFocus: false });
    if (!valid) {
      const hasStep1Error = STEP1_FIELDS.some((name) => form.getFieldState(name).invalid);
      const target = hasStep1Error ? step1Path : step2Path;
      navigateWithDraft(target, formData, { state: { attemptedSubmit: true } });
      return;
    }

    const errors = validateInputs(formData);
    setSubmitErrors(errors);
    if (errors.length > 0) {
      bumpFocusKey();
      return;
    }
    setSubmitError(null);
    setSubmitting(true);
    try {
      const payload = buildSkjemaPayload(draftId, formData);
      const response = await fetch(`${EKSPERTBISTAND_API_PATH}/${draftId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        const message = await parseErrorMessage(response);
        throw new Error(message ?? `Kunne ikke sende søknaden (${response.status}).`);
      }
      await clearDraft();
      navigate(`/skjema/${draftId}/kvittering`, {
        replace: true,
        state: { submissionSuccess: true },
      });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Kunne ikke sende søknaden akkurat nå.";
      setSubmitError(message);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } finally {
      setSubmitting(false);
    }
  }, [
    bumpFocusKey,
    clearDraft,
    draftId,
    form,
    formData,
    navigate,
    navigateWithDraft,
    step1Path,
    step2Path,
  ]);

  return (
    <DecoratedPage blockProps={{ width: "lg", gutters: true }}>
      <VStack gap="8" data-aksel-template="form-summarypage-v3">
        <VStack gap="3">
          <Heading level="1" size="xlarge">
            Oppsummering av søknad om ekspertbistand
          </Heading>
        </VStack>

        <div>
          <BackLink to={step2Path} onClick={goToStep2}>
            Forrige steg
          </BackLink>
          <Box paddingBlock="6 5">
            <Heading level="2" size="large">
              Oppsummering
            </Heading>
          </Box>

          <FormProgress activeStep={3} totalSteps={3}>
            <FormProgress.Step href="#" onClick={goToStep1}>
              Deltakere
            </FormProgress.Step>
            <FormProgress.Step href="#" onClick={goToStep2}>
              Behov for bistand
            </FormProgress.Step>
            <FormProgress.Step href="#" onClick={(event) => event.preventDefault()}>
              Oppsummering
            </FormProgress.Step>
          </FormProgress>
        </div>

        <BodyLong>
          Nå kan du se over at alt er riktig før du sender inn søknaden. Ved behov kan du endre
          opplysningene.
        </BodyLong>

        {submitError && (
          <Alert variant="error" role="alert">
            {submitError}
          </Alert>
        )}

        {submitErrors.length > 0 && (
          <FocusedErrorSummary
            isActive={submitErrors.length > 0}
            focusKey={errorFocusKey}
            heading="Du må rette disse feilene før du kan sende inn søknaden:"
          >
            {submitErrors.map(({ id, message }) => (
              <ErrorSummary.Item key={id} href={`#${id}`}>
                {message}
              </ErrorSummary.Item>
            ))}
          </FocusedErrorSummary>
        )}

        <SoknadSummary data={formData} editable onEditStep1={goToStep1} onEditStep2={goToStep2} />

        <VStack gap="4">
          {lastPersistedAt && (
            <BodyShort size="small" textColor="subtle">
              Sist lagret: {lastPersistedAt.toLocaleString("nb-NO")}
            </BodyShort>
          )}
          <HGrid
            gap={{ xs: "4", sm: "8 4" }}
            columns={{ xs: 1, sm: 2 }}
            width={{ sm: "fit-content" }}
          >
            <Button
              variant="secondary"
              icon={<ArrowLeftIcon aria-hidden />}
              iconPosition="left"
              onClick={() => navigateTo(step2Path)}
            >
              Forrige steg
            </Button>
            <Button
              variant="primary"
              icon={<PaperplaneIcon aria-hidden />}
              iconPosition="right"
              type="button"
              loading={submitting}
              onClick={handleSubmit}
            >
              Send søknad
            </Button>
          </HGrid>
          <DraftActions
            onContinueLater={() => {
              navigateWithDraft(APPLICATIONS_PATH, formData);
            }}
            onDeleteDraft={async () => {
              await clearDraft();
              navigate(APPLICATIONS_PATH);
            }}
          />
        </VStack>
      </VStack>
    </DecoratedPage>
  );
}
