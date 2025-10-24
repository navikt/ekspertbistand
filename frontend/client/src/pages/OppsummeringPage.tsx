import React, { useCallback, useMemo, useRef, useState } from "react";
import { Link as RouterLink, useLocation, useNavigate } from "react-router-dom";
import { ArrowLeftIcon, FloppydiskIcon, PaperplaneIcon, TrashIcon } from "@navikt/aksel-icons";
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
import { mergeInputs, type Inputs } from "./types";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { validateInputs, type ValidationError } from "./validation";
import { FormSummaryAnswer } from "@navikt/ds-react/FormSummary";
import { DeleteDraftModal } from "../components/DeleteDraftModal";
import { SaveDraftModal } from "../components/SaveDraftModal";

type SummaryError = ValidationError;

export default function OppsummeringPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { draftId, draft, saveDraft, clearDraft, lastPersistedAt } = useSoknadDraft();
  const formData = useMemo(() => {
    const state = (location.state as { formData?: Partial<Inputs> } | null)?.formData;
    if (state) return mergeInputs(undefined, state);
    return draft;
  }, [draft, location.state]);
  const errorSummaryRef = useRef<HTMLDivElement>(null);
  const [submitErrors, setSubmitErrors] = useState<SummaryError[]>([]);

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
      return `${new Intl.NumberFormat("nb-NO").format(value)} kr`;
    }
    return formatValue(value);
  };

  const formatDate = (value: Inputs["behovForBistand"]["startDato"]): string => {
    if (!value) return "—";
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? "—" : parsed.toLocaleDateString("nb-NO");
  };

  const handleNavigate = useCallback(
    (path: string) => (event: React.MouseEvent<HTMLElement>) => {
      event.preventDefault();
      setSubmitErrors([]);
      saveDraft(formData);
      navigate(path, { state: { formData } });
    },
    [navigate, formData, saveDraft]
  );

  const handleSubmit = useCallback(() => {
    const errors = validateInputs(formData);
    setSubmitErrors(errors);
    if (errors.length > 0) {
      requestAnimationFrame(() => {
        errorSummaryRef.current?.focus();
      });
      return;
    }
    saveDraft(formData);
    console.log(formData);
  }, [formData, saveDraft]);

  const [continueModalOpen, setContinueModalOpen] = useState(false);

  const handleContinueLater = useCallback(() => {
    setContinueModalOpen(true);
  }, []);

  const closeContinueModal = useCallback(() => setContinueModalOpen(false), []);

  const confirmContinueLater = useCallback(() => {
    setContinueModalOpen(false);
    saveDraft(formData);
    navigate("/");
  }, [formData, navigate, saveDraft]);

  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const handleDeleteDraft = useCallback(() => {
    setDeleteModalOpen(true);
  }, []);

  const closeDeleteModal = useCallback(() => setDeleteModalOpen(false), []);

  const confirmDeleteDraft = useCallback(async () => {
    setDeleteModalOpen(false);
    await clearDraft();
    navigate("/");
  }, [clearDraft, navigate]);

  return (
    <DecoratedPage
      blockProps={{ as: "main", width: "lg", gutters: true }}
      languages={[
        { locale: "nb", url: "https://www.nav.no" },
        { locale: "en", url: "https://www.nav.no/en" },
      ]}
    >
      <VStack gap="8" data-aksel-template="form-summarypage-v3">
        <VStack gap="3">
          <Heading level="1" size="xlarge">
            Oppsummering av søknad om ekspertbistand
          </Heading>
        </VStack>

        <div>
          <Link
            as={RouterLink}
            to={`/skjema/${draftId}/steg-2`}
            onClick={handleNavigate(`/skjema/${draftId}/steg-2`)}
          >
            <ArrowLeftIcon aria-hidden /> Forrige steg
          </Link>
          <Box paddingBlock="6 5">
            <Heading level="2" size="large">
              Oppsummering
            </Heading>
          </Box>

          <FormProgress activeStep={3} totalSteps={3}>
            <FormProgress.Step href="#" onClick={handleNavigate(`/skjema/${draftId}/steg-1`)}>
              Deltakere
            </FormProgress.Step>
            <FormProgress.Step href="#" onClick={handleNavigate(`/skjema/${draftId}/steg-2`)}>
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
              <FormSummary.Value>{formatValue(formData.virksomhet.navn)}</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Organisasjonsnummer</FormSummary.Label>
              <FormSummary.Value>
                {formatValue(formData.virksomhet.virksomhetsnummer)}
              </FormSummary.Value>
            </FormSummary.Answer>
            <FormSummaryAnswer>
              <FormSummary.Label>Kontaktperson i virksomheten</FormSummary.Label>
              <FormSummary.Value>
                <FormSummary.Answers>
                  <FormSummary.Answer>
                    <FormSummary.Label>Navn</FormSummary.Label>
                    <FormSummary.Value>
                      {formatValue(formData.virksomhet.kontaktperson.navn)}
                    </FormSummary.Value>
                  </FormSummary.Answer>
                  <FormSummary.Answer>
                    <FormSummary.Label>E-post</FormSummary.Label>
                    <FormSummary.Value>
                      {formatValue(formData.virksomhet.kontaktperson.epost)}
                    </FormSummary.Value>
                  </FormSummary.Answer>
                  <FormSummary.Answer>
                    <FormSummary.Label>Telefonnummer</FormSummary.Label>
                    <FormSummary.Value>
                      {formatValue(formData.virksomhet.kontaktperson.telefon)}
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
                    <FormSummary.Value>{formatValue(formData.ansatt.navn)}</FormSummary.Value>
                  </FormSummary.Answer>
                  <FormSummary.Answer>
                    <FormSummary.Label>Fødselsnummer</FormSummary.Label>
                    <FormSummary.Value>
                      {formatValue(formData.ansatt.fodselsnummer)}
                    </FormSummary.Value>
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
                    <FormSummary.Value>{formatValue(formData.ekspert.navn)}</FormSummary.Value>
                  </FormSummary.Answer>
                  <FormSummary.Answer>
                    <FormSummary.Label>Tilknyttet virksomhet</FormSummary.Label>
                    <FormSummary.Value>
                      {formatValue(formData.ekspert.virksomhet)}
                    </FormSummary.Value>
                  </FormSummary.Answer>
                  <FormSummary.Answer>
                    <FormSummary.Label>Kompetanse / autorisasjon</FormSummary.Label>
                    <FormSummary.Value>
                      {formatValue(formData.ekspert.kompetanse)}
                    </FormSummary.Value>
                  </FormSummary.Answer>
                </FormSummary.Answers>
              </FormSummary.Value>
            </FormSummaryAnswer>
          </FormSummary.Answers>
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={handleNavigate(`/skjema/${draftId}/steg-1`)} />
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
              <FormSummary.Value>
                {formatValue(formData.behovForBistand.problemstilling)}
              </FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>
                Hva vil dere har hjelp til fra eksperten, og hvor mange timer tror dere at det vil
                ta?
              </FormSummary.Label>
              <FormSummary.Value>{formatValue(formData.behovForBistand.bistand)}</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>
                Hvilke tiltak for tilrettelegging har dere allerede gjort, vurdert eller forsøkt?
              </FormSummary.Label>
              <FormSummary.Value>{formatValue(formData.behovForBistand.tiltak)}</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Estimert kostnad for ekspertbistand</FormSummary.Label>
              <FormSummary.Value>
                {formatCurrency(formData.behovForBistand.kostnad)}
              </FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Startdato</FormSummary.Label>
              <FormSummary.Value>
                {formatDate(formData.behovForBistand.startDato)}
              </FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>
                Hvem i Nav har du drøftet behovet om ekspertbistand i denne saken med?
              </FormSummary.Label>
              <FormSummary.Value>
                {formatValue(formData.behovForBistand.navKontakt)}
              </FormSummary.Value>
            </FormSummary.Answer>
          </FormSummary.Answers>
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={handleNavigate(`/skjema/${draftId}/steg-2`)} />
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
              onClick={handleNavigate(`/skjema/${draftId}/steg-2`)}
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

            <Box asChild marginBlock={{ xs: "4 0", sm: "0" }}>
              <Button
                variant="tertiary"
                icon={<FloppydiskIcon aria-hidden />}
                iconPosition="left"
                type="button"
                onClick={handleContinueLater}
              >
                Fortsett senere
              </Button>
            </Box>
            <Button
              variant="tertiary"
              icon={<TrashIcon aria-hidden />}
              iconPosition="left"
              type="button"
              onClick={handleDeleteDraft}
            >
              Slett søknaden
            </Button>
          </HGrid>
        </VStack>
      </VStack>
      <DeleteDraftModal
        open={deleteModalOpen}
        onClose={closeDeleteModal}
        onConfirm={confirmDeleteDraft}
      />
      <SaveDraftModal
        open={continueModalOpen}
        onClose={closeContinueModal}
        onConfirm={confirmContinueLater}
      />
    </DecoratedPage>
  );
}
