import { useCallback, useState } from "react";
import { ArrowLeftIcon, PaperplaneIcon } from "@navikt/aksel-icons";
import { Alert, BodyLong, Box, Button, Heading, HGrid, VStack } from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { soknadSchema } from "../features/soknad/schema";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { DraftActions } from "../components/DraftActions.tsx";
import { buildSkjemaPayload } from "../features/soknad/payload";
import { SoknadSummary } from "../components/SoknadSummary";
import { EKSPERTBISTAND_API_PATH, LOGIN_URL } from "../utils/constants";
import { BackLink } from "../components/BackLink";
import useSWRMutation from "swr/mutation";
import { fetchJson } from "../utils/api";
import { useSkjemaNavigation } from "../hooks/useSkjemaNavigation";
import { SkjemaFormProgress } from "../components/SkjemaFormProgress";
import { useNavigate } from "react-router-dom";
import { resolveApiError, type ApiErrorInfo } from "../utils/http";
import { SistLagretInfo } from "../components/SistLagretInfo.tsx";

export default function OppsummeringPage() {
  const navigate = useNavigate();
  const { draftId, draft: formData, clearDraft, lastPersistedAt } = useSoknadDraft();
  const { goToSoknader, goToStep1, goToStep2, createLinkHandler } = useSkjemaNavigation();
  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);
  const { trigger: submitDraft, isMutating: submitting } = useSWRMutation<
    null,
    Error,
    [string, string],
    RequestInit
  >(["submit-draft", draftId], ([, id], { arg }) =>
    fetchJson<null>(`${EKSPERTBISTAND_API_PATH}/${id}`, arg)
  );

  const handleStep1Link = createLinkHandler(goToStep1);
  const handleStep2Link = createLinkHandler(goToStep2);

  const handleSubmit = useCallback(async () => {
    const result = soknadSchema.safeParse(formData);
    if (!result.success) {
      setSubmitError({
        message:
          "Du må fylle ut alle feltene før du sender inn. Gå tilbake til stegene og fyll inn manglende opplysninger.",
        requiresLogin: false,
      });
      window.scrollTo({ top: 0, behavior: "smooth" });
      return;
    }
    setSubmitError(null);
    try {
      const payload = buildSkjemaPayload(draftId, formData);
      await submitDraft({
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      navigate(`/skjema/${draftId}/kvittering`, {
        replace: true,
        state: { submissionSuccess: true },
      });
    } catch (error) {
      setSubmitError(resolveApiError(error, "Kunne ikke sende søknaden akkurat nå."));
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  }, [draftId, formData, navigate, submitDraft]);

  return (
    <DecoratedPage>
      <VStack gap="8" data-aksel-template="form-summarypage-v3">
        <VStack gap="3">
          <Heading level="1" size="xlarge">
            Oppsummering av søknad om ekspertbistand
          </Heading>
        </VStack>

        <div>
          <BackLink to={`/skjema/${draftId}/steg-2`} onClick={handleStep2Link}>
            Forrige steg
          </BackLink>
          <Box paddingBlock="6 5">
            <Heading level="2" size="large">
              Oppsummering
            </Heading>
          </Box>
          <SkjemaFormProgress activeStep={3} onStep1={handleStep1Link} onStep2={handleStep2Link} />
        </div>

        <BodyLong>
          Nå kan du se over at alt er riktig før du sender inn søknaden. Ved behov kan du endre
          opplysningene.
        </BodyLong>

        {submitError && (
          <Alert variant="error" role="alert">
            <VStack gap="3">
              <BodyLong>{submitError.message}</BodyLong>
              {submitError.requiresLogin ? (
                <Button as="a" href={LOGIN_URL} size="small" variant="primary">
                  Logg inn
                </Button>
              ) : null}
            </VStack>
          </Alert>
        )}

        <SoknadSummary
          data={formData}
          editable
          onEditStep1={handleStep1Link}
          onEditStep2={handleStep2Link}
        />

        <VStack gap="4">
          <SistLagretInfo timestamp={lastPersistedAt} />
          <HGrid
            gap={{ xs: "4", sm: "8 4" }}
            columns={{ xs: 1, sm: 2 }}
            width={{ sm: "fit-content" }}
          >
            <Button
              variant="secondary"
              icon={<ArrowLeftIcon aria-hidden />}
              iconPosition="left"
              onClick={goToStep2}
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
              goToSoknader();
            }}
            onDeleteDraft={async () => {
              await clearDraft();
              goToSoknader();
            }}
          />
        </VStack>
      </VStack>
    </DecoratedPage>
  );
}
