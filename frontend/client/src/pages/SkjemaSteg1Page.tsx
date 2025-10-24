import React, { useCallback, useEffect, useMemo, useRef } from "react";
import { useNavigate, Link as RouterLink, useLocation } from "react-router-dom";
import { Controller, type SubmitHandler, useForm, useWatch } from "react-hook-form";
import { ArrowLeftIcon, ArrowRightIcon } from "@navikt/aksel-icons";
import {
  Button,
  ErrorSummary,
  HGrid,
  Heading,
  TextField,
  VStack,
  Fieldset,
  Link,
  FormProgress,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { DEFAULT_LANGUAGE_LINKS, FORM_COLUMN_STYLE, withPreventDefault } from "./utils";
import { useDraftAutosave } from "./useDraftAutosave";
import { useErrorSummaryFocus } from "./useErrorSummaryFocus";
import { createEmptyInputs, mergeInputs, type Inputs } from "./types";
import { useSkjemaFormState } from "./useSkjemaFormState";
import { useDraftNavigation } from "./useDraftNavigation";
import {
  validateVirksomhetsnummer,
  validateKontaktpersonNavn,
  validateKontaktpersonEpost,
  validateKontaktpersonTelefon,
  validateAnsattFodselsnummer,
  validateAnsattNavn,
  validateEkspertNavn,
  validateEkspertVirksomhet,
  validateEkspertKompetanse,
} from "./validation";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { VirksomhetPicker } from "../components/VirksomhetPicker";
import { DraftActions } from "./DraftActions";

export default function SkjemaSteg1Page() {
  const navigate = useNavigate();
  const location = useLocation();
  const errorSummaryRef = useRef<HTMLDivElement>(null);
  const hasInitialisedRef = useRef(false);

  const form = useForm<Inputs>({
    mode: "onSubmit",
    reValidateMode: "onSubmit",
    shouldFocusError: false,
    shouldUnregister: true,
  });
  const { control, register, setValue, getValues, formState } = form;
  const watchedValues = useWatch<Inputs>({ control: form.control });
  const { errors, submitCount } = formState;
  const autosaveDeps = useMemo(() => [watchedValues], [watchedValues]);
  const errorItems = [
    { id: "virksomhet.virksomhetsnummer", message: errors.virksomhet?.virksomhetsnummer?.message },
    {
      id: "virksomhet.kontaktperson.navn",
      message: errors.virksomhet?.kontaktperson?.navn?.message,
    },
    {
      id: "virksomhet.kontaktperson.epost",
      message: errors.virksomhet?.kontaktperson?.epost?.message,
    },
    {
      id: "virksomhet.kontaktperson.telefon",
      message: errors.virksomhet?.kontaktperson?.telefon?.message,
    },
    { id: "ansatt.fodselsnummer", message: errors.ansatt?.fodselsnummer?.message },
    { id: "ansatt.navn", message: errors.ansatt?.navn?.message },
    { id: "ekspert.navn", message: errors.ekspert?.navn?.message },
    { id: "ekspert.virksomhet", message: errors.ekspert?.virksomhet?.message },
    { id: "ekspert.kompetanse", message: errors.ekspert?.kompetanse?.message },
  ].filter((item): item is { id: string; message: string } => typeof item.message === "string");

  const { draftId, draft, hydrated, saveDraft, clearDraft } = useSoknadDraft();
  const { captureSnapshot, mergeValues } = useSkjemaFormState(form, location, navigate);
  const navigateWithDraft = useDraftNavigation({ captureSnapshot, saveDraft, navigate });

  useEffect(() => {
    if (!hydrated || hasInitialisedRef.current) return;
    const initialData = mergeInputs(createEmptyInputs(), draft);
    form.reset(initialData, { keepValues: false });
    mergeValues(initialData);
    hasInitialisedRef.current = true;
  }, [draft, form, hydrated, mergeValues]);

  useDraftAutosave({
    captureSnapshot,
    saveDraft,
    hydrated,
    dependencies: autosaveDeps,
  });

  const shouldFocusErrorSummary = hydrated && submitCount > 0 && Object.keys(errors).length > 0;
  const errorFocusDeps = useMemo(() => [errors], [errors]);
  useErrorSummaryFocus({
    ref: errorSummaryRef,
    isActive: shouldFocusErrorSummary,
    dependencies: errorFocusDeps,
  });

  const goToStepTwo = useCallback(() => {
    navigateWithDraft(`/skjema/${draftId}/steg-2`);
  }, [draftId, navigateWithDraft]);

  const goToSummary = useCallback(() => {
    navigateWithDraft(`/skjema/${draftId}/oppsummering`);
  }, [draftId, navigateWithDraft]);

  const onValid: SubmitHandler<Inputs> = () => {
    goToStepTwo();
  };
  const goToStepTwoLink = withPreventDefault(goToStepTwo);
  const goToSummaryLink = withPreventDefault(goToSummary);

  return (
    <DecoratedPage blockProps={{ width: "lg", gutters: true }} languages={DEFAULT_LANGUAGE_LINKS}>
      <form onSubmit={form.handleSubmit(onValid)}>
        <VStack gap="8">
          <Heading level="1" size="xlarge">
            Søknadsskjema – ekspertbistand
          </Heading>

          <VStack gap="3">
            <Link as={RouterLink} to="/">
              <ArrowLeftIcon aria-hidden /> Forrige steg
            </Link>
            <FormProgress activeStep={1} totalSteps={3}>
              <FormProgress.Step href="#" onClick={(event) => event.preventDefault()}>
                Deltakere
              </FormProgress.Step>
              <FormProgress.Step href="#" onClick={goToStepTwoLink}>
                Behov for bistand
              </FormProgress.Step>
              <FormProgress.Step href="#" onClick={goToSummaryLink}>
                Oppsummering
              </FormProgress.Step>
            </FormProgress>
          </VStack>

          <VStack gap="6">
            <div style={FORM_COLUMN_STYLE}>
              <Controller
                name="virksomhet.virksomhetsnummer"
                control={control}
                rules={{ validate: validateVirksomhetsnummer }}
                render={({ field, fieldState }) => (
                  <VirksomhetPicker
                    label="Velg underenhet"
                    value={field.value ?? ""}
                    onChange={(orgnr, virksomhet) => {
                      if (field.value === orgnr) return;
                      field.onChange(orgnr);
                      if (!virksomhet?.navn) return;
                      const currentName = getValues("virksomhet.navn");
                      if (currentName === virksomhet.navn) return;
                      setValue("virksomhet.navn", virksomhet.navn, {
                        shouldDirty: true,
                        shouldTouch: true,
                      });
                    }}
                    error={fieldState.error?.message}
                  />
                )}
              />
            </div>
            <input type="hidden" {...register("virksomhet.navn")} />

            <Fieldset legend="Kontaktperson i virksomheten" style={FORM_COLUMN_STYLE}>
              <TextField
                id="virksomhet.kontaktperson.navn"
                label="Navn"
                error={errors.virksomhet?.kontaktperson?.navn?.message}
                {...register("virksomhet.kontaktperson.navn", {
                  validate: validateKontaktpersonNavn,
                })}
              />
              <TextField
                id="virksomhet.kontaktperson.epost"
                label="E-post"
                type="email"
                error={errors.virksomhet?.kontaktperson?.epost?.message}
                {...register("virksomhet.kontaktperson.epost", {
                  validate: validateKontaktpersonEpost,
                })}
              />
              <TextField
                id="virksomhet.kontaktperson.telefon"
                label="Telefonnummer"
                inputMode="numeric"
                htmlSize={8}
                error={errors.virksomhet?.kontaktperson?.telefon?.message}
                {...register("virksomhet.kontaktperson.telefon", {
                  validate: validateKontaktpersonTelefon,
                })}
              />
            </Fieldset>
          </VStack>

          <Fieldset legend="Ansatt" style={FORM_COLUMN_STYLE}>
            <VStack gap="6">
              <TextField
                id="ansatt.fodselsnummer"
                label="Fødselsnummer"
                htmlSize={11}
                error={errors.ansatt?.fodselsnummer?.message}
                {...register("ansatt.fodselsnummer", {
                  validate: validateAnsattFodselsnummer,
                })}
              />
              <TextField
                id="ansatt.navn"
                label="Navn"
                error={errors.ansatt?.navn?.message}
                {...register("ansatt.navn", {
                  validate: validateAnsattNavn,
                })}
              />
            </VStack>
          </Fieldset>

          <Fieldset legend="Ekspert" style={FORM_COLUMN_STYLE}>
            <VStack gap="6">
              <TextField
                id="ekspert.navn"
                label="Navn"
                error={errors.ekspert?.navn?.message}
                {...register("ekspert.navn", {
                  validate: validateEkspertNavn,
                })}
              />
              <TextField
                id="ekspert.virksomhet"
                label="Tilknyttet virksomhet"
                error={errors.ekspert?.virksomhet?.message}
                {...register("ekspert.virksomhet", {
                  validate: validateEkspertVirksomhet,
                })}
              />
              <TextField
                id="ekspert.kompetanse"
                label="Kompetanse / autorisasjon"
                description="f.eks. psykolog, ergoterapeut, fysioterapeut"
                error={errors.ekspert?.kompetanse?.message}
                {...register("ekspert.kompetanse", {
                  validate: validateEkspertKompetanse,
                })}
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
                onClick={() => navigate("/")}
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
            <DraftActions
              onContinueLater={() => {
                navigateWithDraft("/");
              }}
              onDeleteDraft={async () => {
                await clearDraft();
                navigate("/");
              }}
            />
          </VStack>
        </VStack>
      </form>
    </DecoratedPage>
  );
}
