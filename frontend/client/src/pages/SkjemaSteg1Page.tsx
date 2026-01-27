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

  const { clearDraft, lastPersistedAt } = useSoknadDraft();
  const { goToSoknader, goToStep2, goToSummary, createLinkHandler } = useSkjemaNavigation();
  const virksomhetsnummer = useWatch({ control, name: "virksomhet.virksomhetsnummer" });
  const {
    adresse,
    isLoading: adresseLoading,
    error: adresseError,
  } = useVirksomhetAdresse(virksomhetsnummer);
  const hasVirksomhet = Boolean(virksomhetsnummer?.trim());

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
        <VStack gap="space-32">
          <Heading level="1" size="xlarge">
            Søknadsskjema – ekspertbistand
          </Heading>

          <VStack gap="space-12">
            <BackLink to={SOKNADER_PATH} onClick={handleBackLink}>
              Forrige steg
            </BackLink>
            <SkjemaFormProgress
              activeStep={1}
              onStep2={handleStepTwoLink}
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
                <VStack gap="space-8" style={{ marginTop: "1rem" }}>
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

            <Fieldset
              legend="Kontaktperson i virksomheten"
              description="Den som følger opp den ansatte under ekspertbistanden, vanligvis nærmeste leder."
              style={FORM_COLUMN_STYLE}
            >
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
            <VStack gap="space-32">
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

          <Fieldset
            legend="Ekspert"
            description="Må ha offentlig godkjent utdanning eller autorisasjon. Bedriftshelsetjenesten kan bare brukes som ekspert hvis hjelpen går utover det som vanligvis inngår i avtalen."
            style={FORM_COLUMN_STYLE}
          >
            <VStack gap="space-32">
              <TextField
                id="ekspert.navn"
                label="Navn"
                description="Personen som skal utføre oppdraget"
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
                description="Psykolog, ergoterapeut, fysioterapeut, sykepleier etc."
                error={errors.ekspert?.kompetanse?.message}
                {...register("ekspert.kompetanse")}
              />
            </VStack>
          </Fieldset>

          <Fieldset legend="Nav" style={FORM_COLUMN_STYLE} hideLegend>
            <TextField
              id="nav.kontaktperson"
              label="Hvem i Nav har du drøftet behovet om ekspertbistand i denne saken med?"
              error={errors.nav?.kontaktperson?.message}
              {...register("nav.kontaktperson")}
            />
          </Fieldset>

          <FormErrorSummary
            errors={errors}
            fields={STEP1_FIELDS}
            heading="Du må rette disse feilene før du kan fortsette:"
            focusKey={errorFocusKey}
          />

          <VStack gap="space-16">
            <SistLagretInfo timestamp={lastPersistedAt} />
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
