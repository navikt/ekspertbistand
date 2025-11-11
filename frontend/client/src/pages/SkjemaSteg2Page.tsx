import { useCallback, useEffect, useMemo, useRef, type FormEvent } from "react";
import { useFormContext, useWatch } from "react-hook-form";
import { ArrowLeftIcon, ArrowRightIcon } from "@navikt/aksel-icons";
import {
  Button,
  HGrid,
  Heading,
  TextField,
  Textarea,
  VStack,
  Fieldset,
  Box,
  DatePicker,
  useDatepicker,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import type { SoknadInputs } from "../features/soknad/schema";
import { STEP2_FIELDS } from "../features/soknad/schema";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { DraftActions } from "../components/DraftActions.tsx";
import { FORM_COLUMN_STYLE } from "../styles/forms";
import { BackLink } from "../components/BackLink";
import { useAttemptedSubmitRedirect } from "../hooks/useAttemptedSubmitRedirect";
import { FormErrorSummary } from "../components/FormErrorSummary";
import { useErrorFocus } from "../hooks/useErrorFocus";
import { SkjemaFormProgress } from "../components/SkjemaFormProgress";
import { useSkjemaNavigation } from "../hooks/useSkjemaNavigation";
import { formatDateToIso, parseIsoDate, startOfToday } from "../utils/dates";

export default function SkjemaSteg2Page() {
  const todayDate = useMemo(() => startOfToday(), []);

  const form = useFormContext<SoknadInputs>();
  const { register, setValue, formState } = form;
  const { errors } = formState;
  const { focusKey: errorFocusKey, bumpFocusKey } = useErrorFocus();
  const startdato = useWatch({ name: "behovForBistand.startdato" }) as
    | SoknadInputs["behovForBistand"]["startdato"]
    | undefined;
  const syncingDateRef = useRef(false);
  const { draftId, clearDraft } = useSoknadDraft();
  const { goToApplications, goToStep1, goToSummary, createLinkHandler } = useSkjemaNavigation();
  const handleStepOneLink = createLinkHandler(goToStep1);
  const handleSummaryLink = createLinkHandler(goToSummary);

  useEffect(() => {
    register("behovForBistand.startdato");
  }, [register]);

  useAttemptedSubmitRedirect(form, { fields: STEP2_FIELDS, onValidationFailed: bumpFocusKey });

  const parsedStartdato = useMemo(() => parseIsoDate(startdato), [startdato]);

  const handleDateChange = useCallback(
    (date?: Date) => {
      if (syncingDateRef.current) {
        syncingDateRef.current = false;
        return;
      }
      setValue("behovForBistand.startdato", date ? formatDateToIso(date) : null, {
        shouldDirty: true,
        shouldValidate: Boolean(errors.behovForBistand?.startdato),
      });
    },
    [errors.behovForBistand?.startdato, setValue]
  );

  const { datepickerProps, inputProps, setSelected, selectedDay } = useDatepicker({
    defaultSelected: parsedStartdato,
    defaultMonth: parsedStartdato ?? todayDate,
    fromDate: todayDate,
    onDateChange: handleDateChange,
  });

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
  });

  const handleSubmitStep2 = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const valid = await form.trigger(STEP2_FIELDS);
    if (!valid) {
      bumpFocusKey();
      return;
    }
    goToSummary();
  };

  return (
    <DecoratedPage>
      <form onSubmit={handleSubmitStep2}>
        <VStack gap="8">
          <Heading level="1" size="xlarge">
            Søknadsskjema – ekspertbistand
          </Heading>

          <VStack gap="3">
            <BackLink to={`/skjema/${draftId}/steg-1`} onClick={handleStepOneLink}>
              Forrige steg
            </BackLink>
            <SkjemaFormProgress
              activeStep={2}
              onStep1={handleStepOneLink}
              onSummary={handleSummaryLink}
            />
          </VStack>

          <Heading level="2" size="large">
            Behov for bistand
          </Heading>
          <Fieldset legend="Behov for bistand" hideLegend style={FORM_COLUMN_STYLE}>
            <VStack gap="6">
              <Textarea
                id="behovForBistand.begrunnelse"
                label="Beskriv den ansattes arbeidssituasjon, sykefravær og hvorfor dere ser behov for ekspertbistand"
                error={errors.behovForBistand?.begrunnelse?.message}
                {...register("behovForBistand.begrunnelse")}
                aria-invalid={errors.behovForBistand?.begrunnelse ? true : undefined}
                style={FORM_COLUMN_STYLE}
              />
              <Textarea
                id="behovForBistand.behov"
                label="Hva vil dere har hjelp til fra eksperten, og hvor mange timer tror dere at det vil ta?"
                description="F.eks. kartlegging, arbeidsplassvurdering. Tilskuddet gis ikke til behandling."
                error={errors.behovForBistand?.behov?.message}
                {...register("behovForBistand.behov")}
                aria-invalid={errors.behovForBistand?.behov ? true : undefined}
                style={FORM_COLUMN_STYLE}
              />
              <TextField
                id="behovForBistand.estimertKostnad"
                label="Estimert kostnad for ekspertbistand"
                type="number"
                inputMode="numeric"
                max={25000}
                error={errors.behovForBistand?.estimertKostnad?.message}
                {...kostnadReg}
                aria-invalid={errors.behovForBistand?.estimertKostnad ? true : undefined}
                style={FORM_COLUMN_STYLE}
              />
              <Textarea
                id="behovForBistand.tilrettelegging"
                label="Hvilken tilrettelegging har dere allerede tilbudt/prøvd ut og hvordan gikk det?"
                description="Fleksibel arbeidstid, hjemmekontor, hjelpemiddel, tilpassing av arbeidsoppgaver, opplæring, ekstra oppfølging etc."
                error={errors.behovForBistand?.tilrettelegging?.message}
                {...register("behovForBistand.tilrettelegging")}
                aria-invalid={errors.behovForBistand?.tilrettelegging ? true : undefined}
                style={FORM_COLUMN_STYLE}
              />
              <div>
                <Box paddingBlock="0" style={FORM_COLUMN_STYLE}>
                  <DatePicker {...datepickerProps}>
                    <DatePicker.Input
                      {...inputProps}
                      id="behovForBistand.startdato"
                      label="Startdato"
                      description="Tiltaket må være godkjent for dere kan begynne."
                      error={errors.behovForBistand?.startdato?.message}
                      onBlur={(e) => {
                        inputProps.onBlur?.(e);
                        if (errors.behovForBistand?.startdato) {
                          void form.trigger("behovForBistand.startdato");
                        }
                      }}
                    />
                  </DatePicker>
                </Box>
              </div>
              <TextField
                id="nav.kontaktperson"
                label="Hvem i Nav har du drøftet behovet om ekspertbistand i denne saken med?"
                error={errors.nav?.kontaktperson?.message}
                {...register("nav.kontaktperson")}
                aria-invalid={errors.nav?.kontaktperson ? true : undefined}
                style={FORM_COLUMN_STYLE}
              />
            </VStack>
          </Fieldset>

          <FormErrorSummary
            errors={errors}
            fields={STEP2_FIELDS}
            heading="Du må rette disse feilene før du kan fortsette:"
            focusKey={errorFocusKey}
          />

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
                onClick={goToStep1}
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
                goToApplications();
              }}
              onDeleteDraft={async () => {
                await clearDraft();
                goToApplications();
              }}
            />
          </VStack>
        </VStack>
      </form>
    </DecoratedPage>
  );
}
