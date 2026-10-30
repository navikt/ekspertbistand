import { type FormEvent } from "react";
import { Controller, useFormContext, useWatch } from "react-hook-form";
import { ArrowLeftIcon, ArrowRightIcon } from "@navikt/aksel-icons";
import {
  BodyShort,
  Button,
  ErrorMessage,
  Fieldset,
  HGrid,
  Heading,
  Label,
  Loader,
  TextField,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { FORM_COLUMN_STYLE } from "../styles/forms";
import type { SoknadInputs } from "../features/soknad/schema";
import { useSoknadDraft } from "../context/SoknadDraftContext";
import { VirksomhetVelger } from "../components/VirksomhetVelger.tsx";
import { DraftActions } from "../components/DraftActions.tsx";
import { useErrorFocus } from "../hooks/useErrorFocus";
import { BackLink } from "../components/BackLink";
import { useAttemptedSubmitRedirect } from "../hooks/useAttemptedSubmitRedirect";
import { FormErrorSummary } from "../components/FormErrorSummary";
import { STEP1_FIELDS } from "../features/soknad/schema";
import { SkjemaFormProgress } from "../components/SkjemaFormProgress";
import { useSkjemaNavigation } from "../hooks/useSkjemaNavigation";
import { SOKNADER_PATH } from "../utils/constants";
import { SistLagretInfo } from "../components/SistLagretInfo.tsx";
import { useVirksomhetAdresse } from "../hooks/useVirksomhetAdresse";

export default function SkjemaSteg1Page() {
  const form = useFormContext<SoknadInputs>();
  const { control, register, setValue, getValues, formState } = form;
  const { errors } = formState;
  const { focusKey: errorFocusKey, bumpFocusKey } = useErrorFocus();

  const { clearDraft, lastPersistedAt, saveError } = useSoknadDraft();
  const {
    goToSoknader,
    goToSoknaderWithSaveNotice,
    goToStep2,
    goToStep3,
    goToSummary,
    createLinkHandler,
  } = useSkjemaNavigation();
  const virksomhetsnummer = useWatch({ control, name: "virksomhet.virksomhetsnummer" });
  const {
    adresse,
    isLoading: adresseLoading,
    error: adresseError,
  } = useVirksomhetAdresse(virksomhetsnummer);
  const hasVirksomhet = Boolean(virksomhetsnummer?.trim());

  useAttemptedSubmitRedirect(form, { fields: STEP1_FIELDS, onValidationFailed: bumpFocusKey });

  const handleBackLink = createLinkHandler(goToSoknaderWithSaveNotice);
  const handleStepTwoLink = createLinkHandler(goToStep2);
  const handleStepThreeLink = createLinkHandler(goToStep3);
  const handleSummaryLink = createLinkHandler(goToSummary);

  const handleSubmitStep1 = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const valid = await form.trigger(STEP1_FIELDS);
    if (!valid) {
      bumpFocusKey();
      return;
    }
    goToStep2();
  };

  return (
    <DecoratedPage>
      <form onSubmit={handleSubmitStep1} noValidate autoComplete="off">
        <VStack gap="space-32">
          <Heading level="1" size="xlarge">
            Søknadsskjema – ekspertbistand
          </Heading>

          <VStack gap="space-12">
            <BackLink to={SOKNADER_PATH} onClick={handleBackLink}>
              Tilbake til oversikt
            </BackLink>
            <SkjemaFormProgress
              activeStep={1}
              onStep2={handleStepTwoLink}
              onStep3={handleStepThreeLink}
              onSummary={handleSummaryLink}
            />
          </VStack>

          <VStack gap="space-32">
            <div style={FORM_COLUMN_STYLE}>
              <Controller
                name="virksomhet.virksomhetsnummer"
                control={control}
                render={({ field, fieldState }) => (
                  <VirksomhetVelger
                    label="Velg underenhet"
                    description="Velg underenheten der den ansatte arbeider, dvs. der inntekten rapporteres i A-meldingen."
                    value={field.value ?? ""}
                    onChange={(orgnr, virksomhet) => {
                      if (field.value === orgnr) return;
                      field.onChange(orgnr);
                      if (!virksomhet?.navn) return;
                      const currentName = getValues("virksomhet.virksomhetsnavn");
                      if (currentName === virksomhet.navn) return;
                      setValue("virksomhet.virksomhetsnavn", virksomhet.navn, {
                        shouldDirty: true,
                        shouldTouch: true,
                      });
                    }}
                    error={fieldState.error?.message}
                  />
                )}
              />
              {hasVirksomhet ? (
                <VStack gap="space-8" style={{ marginBlockStart: "var(--ax-space-16)" }}>
                  <Label>Beliggenhetsadresse (hentet fra brreg.no)</Label>
                  {adresseLoading ? (
                    <BodyShort size="small">
                      <Loader size="small" title="Laster beliggenhetsadresse" aria-live="polite" />{" "}
                      Laster beliggenhetsadresse ...
                    </BodyShort>
                  ) : null}
                  {!adresseLoading && !adresseError ? (
                    <BodyShort size="small">
                      {adresse ?? "Fant ikke beliggenhetsadresse."}
                    </BodyShort>
                  ) : null}
                  {adresseError ? (
                    <ErrorMessage>Kunne ikke hente beliggenhetsadresse.</ErrorMessage>
                  ) : null}
                </VStack>
              ) : null}
            </div>
            <input type="hidden" {...register("virksomhet.virksomhetsnavn")} />
          </VStack>

          <Fieldset legend="Deltaker" style={FORM_COLUMN_STYLE}>
            <TextField
              id="ansatt.fnr"
              label="Fødselsnummer"
              htmlSize={11}
              error={errors.ansatt?.fnr?.message}
              {...register("ansatt.fnr")}
            />
          </Fieldset>

          <FormErrorSummary
            errors={errors}
            fields={STEP1_FIELDS}
            heading="Du må rette disse feilene før du kan fortsette:"
            focusKey={errorFocusKey}
          />

          <VStack gap="space-16">
            <SistLagretInfo timestamp={lastPersistedAt} saveError={saveError} />
            <HGrid
              gap={{ xs: "space-32", sm: "space-16" }}
              columns={{ xs: 1, sm: 2 }}
              width={{ sm: "fit-content" }}
            >
              <Button
                type="button"
                variant="secondary"
                icon={<ArrowLeftIcon aria-hidden />}
                iconPosition="left"
                onClick={() => goToSoknaderWithSaveNotice()}
              >
                Tilbake til oversikt
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
                goToSoknaderWithSaveNotice();
              }}
              onDeleteDraft={async () => {
                await clearDraft();
                goToSoknader();
              }}
            />
          </VStack>
        </VStack>
      </form>
    </DecoratedPage>
  );
}
