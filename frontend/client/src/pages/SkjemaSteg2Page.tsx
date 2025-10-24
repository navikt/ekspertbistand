import React, { useCallback, useEffect, useMemo, useRef } from "react";
import type { CSSProperties } from "react";
import { useNavigate, useLocation, Link as RouterLink } from "react-router-dom";
import { type SubmitHandler, useForm, useWatch } from "react-hook-form";
import { ArrowLeftIcon, ArrowRightIcon, FloppydiskIcon, TrashIcon } from "@navikt/aksel-icons";
import {
  Button,
  ErrorSummary,
  HGrid,
  Heading,
  TextField,
  Textarea,
  VStack,
  Fieldset,
  Box,
  DatePicker,
  useDatepicker,
  Link,
  FormProgress,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { createEmptyInputs, mergeInputs, type Inputs } from "./types";
import { useSkjemaFormState } from "./useSkjemaFormState";
import {
  validateProblemstilling,
  validateBehovForBistand,
  validateTiltakForTilrettelegging,
  validateKostnad,
  validateStartDato,
  validateNavKontakt,
} from "./validation";
import { useDraftNavigation } from "./useDraftNavigation";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { DeleteDraftModal } from "../components/DeleteDraftModal";
import { SaveDraftModal } from "../components/SaveDraftModal";

const formColumnStyle: CSSProperties = { width: "100%", maxWidth: "36rem" };

export default function SkjemaSteg2Page() {
  const navigate = useNavigate();
  const location = useLocation();
  const errorSummaryRef = useRef<HTMLDivElement>(null);
  const hasInitialisedRef = useRef(false);
  const [todayIso] = React.useState(() => new Date().toISOString());
  const todayDate = useMemo(() => new Date(todayIso), [todayIso]);

  const form = useForm<Inputs>({
    mode: "onSubmit",
    reValidateMode: "onSubmit",
    shouldFocusError: false,
    shouldUnregister: true,
    defaultValues: { behovForBistand: { startDato: todayIso } },
  });

  const { register, unregister, trigger, setValue, formState } = form;
  const { errors, isSubmitted, submitCount } = formState;
  const watchedValues = useWatch<Inputs>({ control: form.control });
  const lastSnapshotRef = useRef<string | null>(null);
  const { draftId, draft, hydrated, saveDraft, clearDraft } = useSoknadDraft();
  const { captureSnapshot, mergeValues } = useSkjemaFormState(form, location, navigate);
  const navigateWithDraft = useDraftNavigation({ captureSnapshot, saveDraft, navigate });
  const errorItems = isSubmitted
    ? [
        {
          id: "behovForBistand.problemstilling",
          message: errors.behovForBistand?.problemstilling?.message,
        },
        { id: "behovForBistand.bistand", message: errors.behovForBistand?.bistand?.message },
        { id: "behovForBistand.tiltak", message: errors.behovForBistand?.tiltak?.message },
        { id: "behovForBistand.kostnad", message: errors.behovForBistand?.kostnad?.message },
        { id: "behovForBistand.startDato", message: errors.behovForBistand?.startDato?.message },
        { id: "behovForBistand.navKontakt", message: errors.behovForBistand?.navKontakt?.message },
      ].filter((item): item is { id: string; message: string } => typeof item.message === "string")
    : [];

  useEffect(() => {
    if (!hydrated || hasInitialisedRef.current) return;
    const initialData = mergeInputs(createEmptyInputs(), draft);
    if (!initialData.behovForBistand.startDato) {
      initialData.behovForBistand.startDato = todayIso;
    }
    form.reset(initialData, { keepValues: false });
    mergeValues(initialData);
    hasInitialisedRef.current = true;
  }, [draft, form, hydrated, mergeValues, todayIso]);

  useEffect(() => {
    if (!hydrated) return;
    const snapshot = captureSnapshot();
    const serialized = JSON.stringify(snapshot);
    if (serialized === lastSnapshotRef.current) return;
    lastSnapshotRef.current = serialized;
    saveDraft(snapshot);
  }, [captureSnapshot, hydrated, saveDraft, watchedValues]);

  useEffect(() => {
    if (!hydrated) return;
    if (submitCount === 0) return;
    if (Object.keys(errors).length === 0) return;
    window.requestAnimationFrame(() => {
      errorSummaryRef.current?.focus();
    });
  }, [errors, hydrated, submitCount]);

  useEffect(() => {
    register("behovForBistand.startDato", { validate: validateStartDato });
    return () => unregister("behovForBistand.startDato");
  }, [register, unregister]);

  const { datepickerProps, inputProps } = useDatepicker({
    defaultSelected: todayDate,
    fromDate: todayDate,
    onDateChange: (date) => {
      setValue("behovForBistand.startDato", date ? date.toISOString() : null, {
        shouldDirty: true,
        shouldValidate: isSubmitted,
      });
    },
  });

  const kostnadReg = register("behovForBistand.kostnad", {
    setValueAs: (value) => (value === "" || value === null ? "" : Number(value)),
    validate: validateKostnad,
  });

  const goToStepOne = useCallback(() => {
    navigateWithDraft(`/skjema/${draftId}/steg-1`);
  }, [draftId, navigateWithDraft]);
  const goToSummary = useCallback(() => {
    navigateWithDraft(`/skjema/${draftId}/oppsummering`);
  }, [draftId, navigateWithDraft]);

  const [continueModalOpen, setContinueModalOpen] = React.useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = React.useState(false);

  const onValid = useCallback<SubmitHandler<Inputs>>(() => {
    goToSummary();
  }, [goToSummary]);

  return (
    <DecoratedPage
      blockProps={{ width: "lg", gutters: true }}
      languages={[
        { locale: "nb", url: "https://www.nav.no" },
        { locale: "en", url: "https://www.nav.no/en" },
      ]}
    >
      <form onSubmit={form.handleSubmit(onValid)}>
        <VStack gap="8">
          <Heading level="1" size="xlarge">
            Søknadsskjema – ekspertbistand
          </Heading>

          <VStack gap="3">
            <Link
              as={RouterLink}
              to={`/skjema/${draftId}/steg-1`}
              onClick={(event) => {
                event.preventDefault();
                goToStepOne();
              }}
            >
              <ArrowLeftIcon aria-hidden /> Forrige steg
            </Link>
            <FormProgress activeStep={2} totalSteps={3}>
              <FormProgress.Step
                href="#"
                onClick={(event) => {
                  event.preventDefault();
                  goToStepOne();
                }}
              >
                Deltakere
              </FormProgress.Step>
              <FormProgress.Step href="#" onClick={(event) => event.preventDefault()}>
                Behov for bistand
              </FormProgress.Step>
              <FormProgress.Step
                href="#"
                onClick={(event) => {
                  event.preventDefault();
                  goToSummary();
                }}
              >
                Oppsummering
              </FormProgress.Step>
            </FormProgress>
          </VStack>

          <Heading level="2" size="large">
            Behov for bistand
          </Heading>
          <Fieldset legend="Behov for bistand" hideLegend style={formColumnStyle}>
            <VStack gap="6">
              <Textarea
                id="behovForBistand.problemstilling"
                label="Beskriv den ansattes arbeidssituasjon, sykefravær og hvorfor dere ser behov for ekspertbistand"
                error={isSubmitted ? errors.behovForBistand?.problemstilling?.message : undefined}
                {...register("behovForBistand.problemstilling", {
                  validate: validateProblemstilling,
                })}
                aria-invalid={isSubmitted ? undefined : false}
                style={formColumnStyle}
              />
              <Textarea
                id="behovForBistand.bistand"
                label="Hva vil dere har hjelp til fra eksperten, og hvor mange timer tror dere at det vil ta?"
                description="f.eks. fleksibel arbeidstid, hjemmekontor, tilpassing av arbeidsoppgaver, hjelpemiddel, opplæring, ekstra oppfølging."
                error={isSubmitted ? errors.behovForBistand?.bistand?.message : undefined}
                {...register("behovForBistand.bistand", {
                  validate: validateBehovForBistand,
                })}
                aria-invalid={isSubmitted ? undefined : false}
                style={formColumnStyle}
              />
              <TextField
                id="behovForBistand.kostnad"
                label="Estimert kostnad for ekspertbistand"
                type="number"
                inputMode="numeric"
                max={25000}
                error={isSubmitted ? errors.behovForBistand?.kostnad?.message : undefined}
                {...kostnadReg}
                aria-invalid={isSubmitted ? undefined : false}
                style={formColumnStyle}
              />
              <Textarea
                id="behovForBistand.tiltak"
                label="Hvilken tilrettelegging har dere allerede tilbudt/prøvd ut og hvordan gikk det?"
                description="Fleksibel arbeidstid, hjemmekontor, hjelpemiddel, tilpassing av arbeidsoppgaver, opplæring, ekstra oppfølging etc."
                error={isSubmitted ? errors.behovForBistand?.tiltak?.message : undefined}
                {...register("behovForBistand.tiltak", {
                  validate: validateTiltakForTilrettelegging,
                })}
                aria-invalid={isSubmitted ? undefined : false}
                style={formColumnStyle}
              />
              <div>
                <Box paddingBlock="0" style={formColumnStyle}>
                  <DatePicker {...datepickerProps}>
                    <DatePicker.Input
                      {...inputProps}
                      id="behovForBistand.startDato"
                      label="Startdato"
                      error={isSubmitted ? errors.behovForBistand?.startDato?.message : undefined}
                      onBlur={(e) => {
                        inputProps.onBlur?.(e);
                        if (isSubmitted) void trigger("behovForBistand.startDato");
                      }}
                    />
                  </DatePicker>
                </Box>
              </div>
              <TextField
                id="behovForBistand.navKontakt"
                label="Hvem i Nav har du drøftet behovet om ekspertbistand i denne saken med?"
                error={isSubmitted ? errors.behovForBistand?.navKontakt?.message : undefined}
                {...register("behovForBistand.navKontakt", {
                  validate: validateNavKontakt,
                })}
                aria-invalid={isSubmitted ? undefined : false}
                style={formColumnStyle}
              />
            </VStack>
          </Fieldset>

          {errorItems.length > 0 && (
            <ErrorSummary
              ref={errorSummaryRef}
              heading="Du må rette disse feilene før du kan fortsette:"
            >
              {errorItems.map(({ id, message }) => (
                <ErrorSummary.Item key={id} href={`#${id}`}>
                  {message}
                </ErrorSummary.Item>
              ))}
            </ErrorSummary>
          )}

          <VStack gap="4">
            <HGrid
              gap={{ xs: "4", sm: "8 4" }}
              columns={{ xs: 1, sm: 2 }}
              width={{ sm: "fit-content" }}
            >
              <Button
                type="button"
                variant="secondary"
                icon={<ArrowLeftIcon aria-hidden />}
                iconPosition="left"
                onClick={goToStepOne}
              >
                Forrige steg
              </Button>
              <Button
                type="submit"
                variant="primary"
                icon={<ArrowRightIcon aria-hidden />}
                iconPosition="right"
              >
                Neste steg
              </Button>
            </HGrid>
            <HGrid
              gap={{ xs: "4", sm: "8 4" }}
              columns={{ xs: 1, sm: 2 }}
              width={{ sm: "fit-content" }}
            >
              <Box asChild marginBlock={{ xs: "4 0", sm: "0" }}>
                <Button
                  type="button"
                  variant="tertiary"
                  icon={<FloppydiskIcon aria-hidden />}
                  iconPosition="left"
                  onClick={() => setContinueModalOpen(true)}
                >
                  Fortsett senere
                </Button>
              </Box>
              <Button
                type="button"
                variant="tertiary"
                icon={<TrashIcon aria-hidden />}
                iconPosition="left"
                onClick={() => setDeleteModalOpen(true)}
              >
                Slett søknaden
              </Button>
            </HGrid>
          </VStack>
        </VStack>
      </form>
      <DeleteDraftModal
        open={deleteModalOpen}
        onClose={() => setDeleteModalOpen(false)}
        onConfirm={async () => {
          setDeleteModalOpen(false);
          await clearDraft();
          navigate("/");
        }}
      />
      <SaveDraftModal
        open={continueModalOpen}
        onClose={() => setContinueModalOpen(false)}
        onConfirm={() => {
          setContinueModalOpen(false);
          navigateWithDraft("/");
        }}
      />
    </DecoratedPage>
  );
}
