import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { FormEventHandler } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { type SubmitHandler, useFormContext, useWatch } from "react-hook-form";
import { ArrowLeftIcon, ArrowRightIcon } from "@navikt/aksel-icons";
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
  FormProgress,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import type { Inputs } from "./types";
import { STEP2_FIELDS } from "./types";
import {
  validateBegrunnelse,
  validateBehov,
  validateTilrettelegging,
  validateEstimertKostnad,
  validateStartdato,
  validateNavKontaktperson,
} from "./validation";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { DraftActions } from "../components/DraftActions.tsx";
import { FORM_COLUMN_STYLE, withPreventDefault } from "./utils";
import { FocusedErrorSummary } from "../components/FocusedErrorSummary";
import { useErrorFocus } from "../hooks/useErrorFocus";
import { useDraftNavigation } from "../hooks/useDraftNavigation";
import { BackLink } from "../components/BackLink";
import { APPLICATIONS_PATH } from "../utils/constants";

export default function SkjemaSteg2Page() {
  const navigate = useNavigate();
  const location = useLocation();
  const todayDate = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return today;
  }, []);

  const form = useFormContext<Inputs>();
  const { register, trigger, setValue, formState } = form;
  const { errors } = formState;
  const locationState = (location.state as { attemptedSubmit?: boolean } | null) ?? null;
  const attemptedSubmitFromLocation = locationState?.attemptedSubmit ?? false;
  const [attemptedSubmit, setAttemptedSubmit] = useState(attemptedSubmitFromLocation);
  const { focusKey: errorFocusKey, bumpFocusKey } = useErrorFocus(() =>
    attemptedSubmitFromLocation ? 1 : 0
  );
  const startdato = useWatch({ name: "behovForBistand.startdato" }) as
    | Inputs["behovForBistand"]["startdato"]
    | undefined;
  const syncingDateRef = useRef(false);
  const { draftId, hydrated, clearDraft } = useSoknadDraft();
  const { getValues } = form;
  const navigateWithDraft = useDraftNavigation();
  const errorItems = attemptedSubmit
    ? [
        {
          id: "behovForBistand.begrunnelse",
          message: errors.behovForBistand?.begrunnelse?.message,
        },
        {
          id: "behovForBistand.behov",
          message: errors.behovForBistand?.behov?.message,
        },
        {
          id: "behovForBistand.tilrettelegging",
          message: errors.behovForBistand?.tilrettelegging?.message,
        },
        {
          id: "behovForBistand.estimertKostnad",
          message: errors.behovForBistand?.estimertKostnad?.message,
        },
        {
          id: "behovForBistand.startdato",
          message: errors.behovForBistand?.startdato?.message,
        },
        { id: "nav.kontaktperson", message: errors.nav?.kontaktperson?.message },
      ].filter((item): item is { id: string; message: string } => typeof item.message === "string")
    : [];

  const shouldFocusErrorSummary = hydrated && attemptedSubmit && Object.keys(errors).length > 0;

  useEffect(() => {
    register("behovForBistand.startdato", { validate: validateStartdato });
  }, [register]);

  useEffect(() => {
    if (!attemptedSubmitFromLocation) return;
    navigate(location.pathname, { replace: true, state: null });
  }, [attemptedSubmitFromLocation, location.pathname, navigate]);

  const toDateString = useCallback((date: Date) => {
    const utcDate = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
    return utcDate.toISOString().slice(0, 10);
  }, []);

  const parsedStartdato = useMemo(() => {
    if (!startdato) return undefined;
    const [year, month, day] = startdato.split("-").map((part) => Number.parseInt(part, 10));
    if ([year, month, day].some((value) => Number.isNaN(value))) return undefined;
    return new Date(year, month - 1, day);
  }, [startdato]);

  const { datepickerProps, inputProps, setSelected, selectedDay } = useDatepicker(
    {
      defaultSelected: parsedStartdato,
      defaultMonth: parsedStartdato ?? todayDate,
      fromDate: todayDate,
      onDateChange: (date) => {
        if (syncingDateRef.current) {
          syncingDateRef.current = false;
          return;
        }
        setValue("behovForBistand.startdato", date ? toDateString(date) : null, {
          shouldDirty: true,
          shouldValidate: attemptedSubmit,
        });
      },
    },
    [attemptedSubmit, parsedStartdato, setValue, toDateString, todayDate]
  );

  useEffect(() => {
    if (!parsedStartdato) {
      if (selectedDay) {
        syncingDateRef.current = true;
        setSelected(undefined);
      }
      return;
    }
    const currentSelectedTime = selectedDay?.getTime();
    if (currentSelectedTime === parsedStartdato.getTime()) {
      return;
    }
    syncingDateRef.current = true;
    setSelected(parsedStartdato);
  }, [parsedStartdato, selectedDay, setSelected]);

  const kostnadReg = register("behovForBistand.estimertKostnad", {
    setValueAs: (value) => (value === "" || value === null ? "" : Number(value)),
    validate: validateEstimertKostnad,
  });

  const goToStepOne = useCallback(() => {
    navigateWithDraft(`/skjema/${draftId}/steg-1`, () => getValues());
  }, [draftId, getValues, navigateWithDraft]);
  const goToSummary = useCallback(() => {
    navigateWithDraft(`/skjema/${draftId}/oppsummering`, () => getValues());
  }, [draftId, getValues, navigateWithDraft]);

  const onValid: SubmitHandler<Inputs> = () => {
    goToSummary();
  };
  const handleSubmitStep2: FormEventHandler<HTMLFormElement> = async (e) => {
    e.preventDefault();
    const valid = await form.trigger(STEP2_FIELDS, { shouldFocus: false });
    if (valid) {
      setAttemptedSubmit(false);
      onValid(form.getValues());
    } else {
      setAttemptedSubmit(true);
      bumpFocusKey();
    }
  };
  const goToStepOneLink = withPreventDefault(goToStepOne);
  const goToSummaryLink = withPreventDefault(goToSummary);

  return (
    <DecoratedPage blockProps={{ width: "lg", gutters: true }}>
      <form onSubmit={handleSubmitStep2}>
        <VStack gap="8">
          <Heading level="1" size="xlarge">
            Søknadsskjema – ekspertbistand
          </Heading>

          <VStack gap="3">
            <BackLink to={`/skjema/${draftId}/steg-1`} onClick={goToStepOneLink}>
              Forrige steg
            </BackLink>
            <FormProgress activeStep={2} totalSteps={3}>
              <FormProgress.Step href="#" onClick={goToStepOneLink}>
                Deltakere
              </FormProgress.Step>
              <FormProgress.Step href="#" onClick={(event) => event.preventDefault()}>
                Behov for bistand
              </FormProgress.Step>
              <FormProgress.Step href="#" onClick={goToSummaryLink}>
                Oppsummering
              </FormProgress.Step>
            </FormProgress>
          </VStack>

          <Heading level="2" size="large">
            Behov for bistand
          </Heading>
          <Fieldset legend="Behov for bistand" hideLegend style={FORM_COLUMN_STYLE}>
            <VStack gap="6">
              <Textarea
                id="behovForBistand.begrunnelse"
                label="Beskriv den ansattes arbeidssituasjon, sykefravær og hvorfor dere ser behov for ekspertbistand"
                error={attemptedSubmit ? errors.behovForBistand?.begrunnelse?.message : undefined}
                {...register("behovForBistand.begrunnelse", {
                  validate: validateBegrunnelse,
                })}
                aria-invalid={attemptedSubmit ? undefined : false}
                style={FORM_COLUMN_STYLE}
              />
              <Textarea
                id="behovForBistand.behov"
                label="Hva vil dere har hjelp til fra eksperten, og hvor mange timer tror dere at det vil ta?"
                description="f.eks. fleksibel arbeidstid, hjemmekontor, tilpassing av arbeidsoppgaver, hjelpemiddel, opplæring, ekstra oppfølging."
                error={attemptedSubmit ? errors.behovForBistand?.behov?.message : undefined}
                {...register("behovForBistand.behov", {
                  validate: validateBehov,
                })}
                aria-invalid={attemptedSubmit ? undefined : false}
                style={FORM_COLUMN_STYLE}
              />
              <TextField
                id="behovForBistand.estimertKostnad"
                label="Estimert kostnad for ekspertbistand"
                type="number"
                inputMode="numeric"
                max={25000}
                error={
                  attemptedSubmit ? errors.behovForBistand?.estimertKostnad?.message : undefined
                }
                {...kostnadReg}
                aria-invalid={attemptedSubmit ? undefined : false}
                style={FORM_COLUMN_STYLE}
              />
              <Textarea
                id="behovForBistand.tilrettelegging"
                label="Hvilken tilrettelegging har dere allerede tilbudt/prøvd ut og hvordan gikk det?"
                description="Fleksibel arbeidstid, hjemmekontor, hjelpemiddel, tilpassing av arbeidsoppgaver, opplæring, ekstra oppfølging etc."
                error={
                  attemptedSubmit ? errors.behovForBistand?.tilrettelegging?.message : undefined
                }
                {...register("behovForBistand.tilrettelegging", {
                  validate: validateTilrettelegging,
                })}
                aria-invalid={attemptedSubmit ? undefined : false}
                style={FORM_COLUMN_STYLE}
              />
              <div>
                <Box paddingBlock="0" style={FORM_COLUMN_STYLE}>
                  <DatePicker {...datepickerProps}>
                    <DatePicker.Input
                      {...inputProps}
                      id="behovForBistand.startdato"
                      label="Startdato"
                      error={
                        attemptedSubmit ? errors.behovForBistand?.startdato?.message : undefined
                      }
                      onBlur={(e) => {
                        inputProps.onBlur?.(e);
                        if (attemptedSubmit) void trigger("behovForBistand.startdato");
                      }}
                    />
                  </DatePicker>
                </Box>
              </div>
              <TextField
                id="nav.kontaktperson"
                label="Hvem i Nav har du drøftet behovet om ekspertbistand i denne saken med?"
                error={attemptedSubmit ? errors.nav?.kontaktperson?.message : undefined}
                {...register("nav.kontaktperson", {
                  validate: validateNavKontaktperson,
                })}
                aria-invalid={attemptedSubmit ? undefined : false}
                style={FORM_COLUMN_STYLE}
              />
            </VStack>
          </Fieldset>

          {errorItems.length > 0 && (
            <FocusedErrorSummary
              isActive={shouldFocusErrorSummary}
              focusKey={errorFocusKey}
              heading="Du må rette disse feilene før du kan fortsette:"
            >
              {errorItems.map(({ id, message }) => (
                <ErrorSummary.Item key={id} href={`#${id}`}>
                  {message}
                </ErrorSummary.Item>
              ))}
            </FocusedErrorSummary>
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
            <DraftActions
              onContinueLater={() => {
                navigateWithDraft(APPLICATIONS_PATH, () => getValues());
              }}
              onDeleteDraft={async () => {
                await clearDraft();
                navigate(APPLICATIONS_PATH);
              }}
            />
          </VStack>
        </VStack>
      </form>
    </DecoratedPage>
  );
}
