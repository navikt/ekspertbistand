import { useCallback, useRef, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { useFormContext } from "react-hook-form";
import { ArrowLeftIcon, PaperplaneIcon } from "@navikt/aksel-icons";
import {
  BodyLong,
  BodyShort,
  Box,
  Button,
  ErrorSummary,
  FormProgress,
  FormSummary,
  Heading,
  HGrid,
  Link,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import type { Inputs } from "./types";
import { STEP1_FIELDS, STEP2_FIELDS } from "./types";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { validateInputs, type ValidationError } from "./validation";
import { FormSummaryAnswer } from "@navikt/ds-react/FormSummary";
import { DEFAULT_LANGUAGE_LINKS, withPreventDefault } from "./utils";
import { DraftActions } from "./DraftActions";
import { focusErrorSummary } from "./useErrorSummaryFocus";

const numberFormatter = new Intl.NumberFormat("nb-NO");

const formatValue = (value: unknown): string => {
  if (typeof value === "number") return Number.isFinite(value) ? value.toString() : "—";
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : "—";
  }
  return "—";
};

const formatCurrency = (value: Inputs["behovForBistand"]["kostnad"]): string => {
  if (typeof value === "number" && Number.isFinite(value)) {
    return `${numberFormatter.format(value)} kr`;
  }
  return formatValue(value);
};

const formatDate = (value: Inputs["behovForBistand"]["startDato"]): string => {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : parsed.toLocaleDateString("nb-NO");
};

export default function OppsummeringPage() {
  const navigate = useNavigate();
  const { draftId, draft: formData, saveDraft, clearDraft, lastPersistedAt } = useSoknadDraft();
  const form = useFormContext<Inputs>();
  const { virksomhet, ansatt, ekspert, behovForBistand } = formData;
  const errorSummaryRef = useRef<HTMLDivElement>(null);
  const [submitErrors, setSubmitErrors] = useState<ValidationError[]>([]);

  const navigateTo = useCallback(
    (path: string) => {
      setSubmitErrors([]);
      saveDraft(formData);
      navigate(path);
    },
    [formData, navigate, saveDraft]
  );
  const step1Path = `/skjema/${draftId}/steg-1`;
  const step2Path = `/skjema/${draftId}/steg-2`;
  const goToStep1 = withPreventDefault(() => navigateTo(step1Path));
  const goToStep2 = withPreventDefault(() => navigateTo(step2Path));

  const handleSubmit = useCallback(async () => {
    // Validate registered fields across both steps
    const valid = await form.trigger([...STEP1_FIELDS, ...STEP2_FIELDS], { shouldFocus: false });
    if (!valid) {
      const hasStep1Error = STEP1_FIELDS.some((name) => form.getFieldState(name).invalid);
      const target = hasStep1Error ? step1Path : step2Path;
      saveDraft(formData);
      navigate(target, { state: { attemptedSubmit: true } });
      return;
    }

    // Keep existing domain-level validation and error summary on summary page
    const errors = validateInputs(formData);
    setSubmitErrors(errors);
    if (errors.length > 0) {
      focusErrorSummary(errorSummaryRef);
      return;
    }
    saveDraft(formData);
    console.log(formData);
  }, [form, formData, navigate, saveDraft, step1Path, step2Path]);

  return (
    <DecoratedPage
      blockProps={{ as: "main", width: "lg", gutters: true }}
      languages={DEFAULT_LANGUAGE_LINKS}
    >
      <VStack gap="8" data-aksel-template="form-summarypage-v3">
        <VStack gap="3">
          <Heading level="1" size="xlarge">
            Oppsummering av søknad om ekspertbistand
          </Heading>
        </VStack>

        <div>
          <Link as={RouterLink} to={step2Path} onClick={goToStep2}>
            <ArrowLeftIcon aria-hidden /> Forrige steg
          </Link>
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

        {submitErrors.length > 0 && (
          <ErrorSummary
            ref={errorSummaryRef}
            heading="Du må rette disse feilene før du kan sende inn søknaden:"
          >
            {submitErrors.map(({ id, message }) => (
              <ErrorSummary.Item key={id} href={`#${id}`}>
                {message}
              </ErrorSummary.Item>
            ))}
          </ErrorSummary>
        )}

        <FormSummary>
          <FormSummary.Header>
            <FormSummary.Heading level="2">Deltakere</FormSummary.Heading>
          </FormSummary.Header>
          <FormSummary.Answers>
            <FormSummary.Answer>
              <FormSummary.Label>Navn på virksomhet</FormSummary.Label>
              <FormSummary.Value>{formatValue(virksomhet.navn)}</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Organisasjonsnummer</FormSummary.Label>
              <FormSummary.Value>{formatValue(virksomhet.virksomhetsnummer)}</FormSummary.Value>
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
                      {formatValue(virksomhet.kontaktperson.telefon)}
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
                    <FormSummary.Value>{formatValue(ansatt.fodselsnummer)}</FormSummary.Value>
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
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={goToStep1} />
          </FormSummary.Footer>
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
              <FormSummary.Value>{formatValue(behovForBistand.problemstilling)}</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>
                Hva vil dere har hjelp til fra eksperten, og hvor mange timer tror dere at det vil
                ta?
              </FormSummary.Label>
              <FormSummary.Value>{formatValue(behovForBistand.bistand)}</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>
                Hvilke tiltak for tilrettelegging har dere allerede gjort, vurdert eller forsøkt?
              </FormSummary.Label>
              <FormSummary.Value>{formatValue(behovForBistand.tiltak)}</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Estimert kostnad for ekspertbistand</FormSummary.Label>
              <FormSummary.Value>{formatCurrency(behovForBistand.kostnad)}</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Startdato</FormSummary.Label>
              <FormSummary.Value>{formatDate(behovForBistand.startDato)}</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>
                Hvem i Nav har du drøftet behovet om ekspertbistand i denne saken med?
              </FormSummary.Label>
              <FormSummary.Value>{formatValue(behovForBistand.navKontakt)}</FormSummary.Value>
            </FormSummary.Answer>
          </FormSummary.Answers>
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={goToStep2} />
          </FormSummary.Footer>
        </FormSummary>

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
              onClick={handleSubmit}
            >
              Send søknad
            </Button>
          </HGrid>
          <DraftActions
            onContinueLater={() => {
              saveDraft(formData);
              navigate("/");
            }}
            onDeleteDraft={async () => {
              await clearDraft();
              navigate("/");
            }}
          />
        </VStack>
      </VStack>
    </DecoratedPage>
  );
}
