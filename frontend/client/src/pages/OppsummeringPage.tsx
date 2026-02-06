import { useCallback, useState } from "react";
import type React from "react";
import { ArrowLeftIcon, PaperplaneIcon } from "@navikt/aksel-icons";
import {
  Alert,
  BodyLong,
  Box,
  Button,
  FormSummary,
  Heading,
  HGrid,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { soknadSchema } from "../features/soknad/schema";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { DraftActions } from "../components/DraftActions.tsx";
import { buildSkjemaPayload } from "../features/soknad/payload";
import { EKSPERTBISTAND_API_PATH, LOGIN_URL } from "../utils/constants";
import { BackLink } from "../components/BackLink";
import useSWRMutation from "swr/mutation";
import { fetchJson } from "../utils/api";
import { useSkjemaNavigation } from "../hooks/useSkjemaNavigation";
import { SkjemaFormProgress } from "../components/SkjemaFormProgress";
import { useNavigate } from "react-router-dom";
import { resolveApiError, type ApiErrorInfo } from "../utils/http";
import { SistLagretInfo } from "../components/SistLagretInfo.tsx";
import {
  formatCurrency,
  formatDate,
  formatTimer,
  formatValue,
} from "../components/summaryFormatters";
import type { SoknadInputs } from "../features/soknad/schema";
import { FormSummaryAnswer } from "@navikt/ds-react/FormSummary";
import { useVirksomhetAdresse } from "../hooks/useVirksomhetAdresse";

type SoknadSummaryProps = {
  data: SoknadInputs;
  editable?: boolean;
  onEditStep1?: React.MouseEventHandler<HTMLAnchorElement>;
  onEditStep2?: React.MouseEventHandler<HTMLAnchorElement>;
};

function SoknadSummary({ data, editable = false, onEditStep1, onEditStep2 }: SoknadSummaryProps) {
  const { virksomhet, ansatt, ekspert, behovForBistand, nav } = data;
  const {
    adresse,
    isLoading: adresseLoading,
    error: adresseError,
  } = useVirksomhetAdresse(virksomhet.virksomhetsnummer);

  return (
    <>
      <FormSummary>
        <FormSummary.Header>
          <FormSummary.Heading level="2">Deltakere</FormSummary.Heading>
        </FormSummary.Header>
        <FormSummary.Answers>
          <FormSummary.Answer>
            <FormSummary.Label>Navn på virksomhet</FormSummary.Label>
            <FormSummary.Value>{formatValue(virksomhet.virksomhetsnavn)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Organisasjonsnummer</FormSummary.Label>
            <FormSummary.Value>{formatValue(virksomhet.virksomhetsnummer)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Beliggenhetsadresse</FormSummary.Label>
            <FormSummary.Value>
              {adresseError
                ? "Kunne ikke hente beliggenhetsadresse."
                : adresseLoading
                  ? "Laster ..."
                  : formatValue(adresse)}
            </FormSummary.Value>
          </FormSummary.Answer>
          <FormSummaryAnswer>
            <FormSummary.Label>Kontaktperson i virksomheten</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(virksomhet.kontaktperson.navn)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>E-post</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(virksomhet.kontaktperson.epost)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Telefonnummer</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(virksomhet.kontaktperson.telefonnummer)}
                  </FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummaryAnswer>

          <FormSummaryAnswer>
            <FormSummary.Label>Ansatt</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ansatt.navn)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Fødselsnummer</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ansatt.fnr)}</FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummaryAnswer>

          <FormSummaryAnswer>
            <FormSummary.Label>Ekspert</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ekspert.navn)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Tilknyttet virksomhet</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ekspert.virksomhet)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Kompetanse / autorisasjon</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ekspert.kompetanse)}</FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummaryAnswer>
        </FormSummary.Answers>
        {editable && onEditStep1 && (
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={onEditStep1} />
          </FormSummary.Footer>
        )}
      </FormSummary>

      <FormSummary>
        <FormSummary.Header>
          <FormSummary.Heading level="2">Behov for bistand</FormSummary.Heading>
        </FormSummary.Header>
        <FormSummary.Answers>
          <FormSummary.Answer>
            <FormSummary.Label>
              Beskriv den ansattes arbeidssituasjon, sykefravær og hvorfor dere ser behov for
              ekspertbistand
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(behovForBistand.begrunnelse)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Hva vil dere ha hjelp til fra eksperten?</FormSummary.Label>
            <FormSummary.Value>{formatValue(behovForBistand.behov)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>
              Hvilke tiltak for tilrettelegging har dere allerede gjort, vurdert eller forsøkt?
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(behovForBistand.tilrettelegging)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Hvor mange timer skal eksperten hjelpe dere?</FormSummary.Label>
            <FormSummary.Value>{formatTimer(behovForBistand.timer)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Estimert kostnad for ekspertbistand</FormSummary.Label>
            <FormSummary.Value>{formatCurrency(behovForBistand.estimertKostnad)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Startdato</FormSummary.Label>
            <FormSummary.Value>{formatDate(behovForBistand.startdato)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>
              Hvem i Nav har du drøftet behovet om ekspertbistand i denne saken med?
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(nav.kontaktperson)}</FormSummary.Value>
          </FormSummary.Answer>
        </FormSummary.Answers>
        {editable && onEditStep2 && (
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={onEditStep2} />
          </FormSummary.Footer>
        )}
      </FormSummary>
    </>
  );
}

export default function OppsummeringPage() {
  const navigate = useNavigate();
  const { draftId, draft: formData, clearDraft, lastPersistedAt } = useSoknadDraft();
  const { goToSoknader, goToSoknaderWithSaveNotice, goToStep1, goToStep2, createLinkHandler } =
    useSkjemaNavigation();
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
      <VStack gap="space-32" data-aksel-template="form-summarypage-v5">
        <VStack>
          <Heading level="1" size="xlarge">
            Oppsummering av søknad om ekspertbistand
          </Heading>
        </VStack>

        <div>
          <BackLink to={`/skjema/${draftId}/steg-2`} onClick={handleStep2Link}>
            Forrige steg
          </BackLink>
          <Box paddingBlock="space-32 space-16">
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
            <VStack gap="space-16">
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

        <VStack gap="space-16">
          <SistLagretInfo timestamp={lastPersistedAt} />
          <HGrid
            gap={{ xs: "space-32", sm: "space-16" }}
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
              goToSoknaderWithSaveNotice();
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
