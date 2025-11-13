import { type FormEvent } from "react";
import { Controller, useFormContext } from "react-hook-form";
import { ArrowLeftIcon, ArrowRightIcon } from "@navikt/aksel-icons";
import { Button, HGrid, Heading, TextField, VStack, Fieldset } from "@navikt/ds-react";
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

export default function SkjemaSteg1Page() {
  const form = useFormContext<SoknadInputs>();
  const { control, register, setValue, getValues, formState } = form;
  const { errors } = formState;
  const { focusKey: errorFocusKey, bumpFocusKey } = useErrorFocus();

  const { clearDraft } = useSoknadDraft();
  const { goToSoknader, goToStep2, goToSummary, createLinkHandler } = useSkjemaNavigation();

  useAttemptedSubmitRedirect(form, { fields: STEP1_FIELDS, onValidationFailed: bumpFocusKey });

  const handleBackLink = createLinkHandler(goToSoknader);
  const handleStepTwoLink = createLinkHandler(goToStep2);
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
      <form onSubmit={handleSubmitStep1}>
        <VStack gap="8">
          <Heading level="1" size="xlarge">
            Søknadsskjema – ekspertbistand
          </Heading>

          <VStack gap="3">
            <BackLink to={SOKNADER_PATH} onClick={handleBackLink}>
              Forrige steg
            </BackLink>
            <SkjemaFormProgress
              activeStep={1}
              onStep2={handleStepTwoLink}
              onSummary={handleSummaryLink}
            />
          </VStack>

          <VStack gap="6">
            <div style={FORM_COLUMN_STYLE}>
              <Controller
                name="virksomhet.virksomhetsnummer"
                control={control}
                render={({ field, fieldState }) => (
                  <VirksomhetVelger
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
                {...register("virksomhet.kontaktperson.navn")}
              />
              <TextField
                id="virksomhet.kontaktperson.epost"
                label="E-post"
                type="email"
                error={errors.virksomhet?.kontaktperson?.epost?.message}
                {...register("virksomhet.kontaktperson.epost")}
              />
              <TextField
                id="virksomhet.kontaktperson.telefonnummer"
                label="Telefonnummer"
                inputMode="numeric"
                htmlSize={8}
                error={errors.virksomhet?.kontaktperson?.telefonnummer?.message}
                {...register("virksomhet.kontaktperson.telefonnummer")}
              />
            </Fieldset>
          </VStack>

          <Fieldset legend="Ansatt" style={FORM_COLUMN_STYLE}>
            <VStack gap="6">
              <TextField
                id="ansatt.fnr"
                label="Fødselsnummer"
                htmlSize={11}
                error={errors.ansatt?.fnr?.message}
                {...register("ansatt.fnr")}
              />
              <TextField
                id="ansatt.navn"
                label="Navn"
                error={errors.ansatt?.navn?.message}
                {...register("ansatt.navn")}
              />
            </VStack>
          </Fieldset>

          <Fieldset legend="Ekspert" style={FORM_COLUMN_STYLE}>
            <VStack gap="6">
              <TextField
                id="ekspert.navn"
                label="Navn"
                error={errors.ekspert?.navn?.message}
                {...register("ekspert.navn")}
              />
              <TextField
                id="ekspert.virksomhet"
                label="Tilknyttet virksomhet"
                error={errors.ekspert?.virksomhet?.message}
                {...register("ekspert.virksomhet")}
              />
              <TextField
                id="ekspert.kompetanse"
                label="Kompetanse / autorisasjon"
                description="f.eks. psykolog, ergoterapeut, fysioterapeut"
                error={errors.ekspert?.kompetanse?.message}
                {...register("ekspert.kompetanse")}
              />
            </VStack>
          </Fieldset>

          <FormErrorSummary
            errors={errors}
            fields={STEP1_FIELDS}
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
                onClick={goToSoknader}
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
                goToSoknader();
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
