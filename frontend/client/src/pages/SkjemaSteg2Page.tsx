import { type FormEvent } from "react";
import { Controller, useFormContext, useWatch } from "react-hook-form";
import { ArrowLeftIcon, ArrowRightIcon } from "@navikt/aksel-icons";
import {
  BodyShort,
  Button,
  UNSAFE_Combobox,
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
import { EkspertVirksomhetVelger } from "../components/EkspertVirksomhetVelger.tsx";
import { formaterVirksomhet } from "../components/formaterVirksomhet.ts";
import { DraftActions } from "../components/DraftActions.tsx";
import { useErrorFocus } from "../hooks/useErrorFocus";
import { BackLink } from "../components/BackLink";
import { useAttemptedSubmitRedirect } from "../hooks/useAttemptedSubmitRedirect";
import { FormErrorSummary } from "../components/FormErrorSummary";
import { STEP2_FIELDS } from "../features/soknad/schema";
import { SkjemaFormProgress } from "../components/SkjemaFormProgress";
import { useSkjemaNavigation } from "../hooks/useSkjemaNavigation";
import { SistLagretInfo } from "../components/SistLagretInfo.tsx";
import { useVirksomhetAdresse } from "../hooks/useVirksomhetAdresse";

// Backend gjør disse om til statistikkoder (EkspertStatistikkoder.kt). Nye valg må legges inn der, ellers blir de ANNET.
const GODKJENT_UTDANNING_ALTERNATIVER = [
  "Ergoterapeut",
  "Fysioterapeut",
  "Karriereveileder",
  "Psykolog",
  "Sykepleier",
];

const RELEVANT_KOMPETANSE_ALTERNATIVER = [
  "Relasjoner og kommunikasjon på arbeidsplassen",
  "Tilrettelegging på arbeidsplassen",
  "Konflikthåndtering",
  "Omstillingsarbeid (endring i arbeidslivet)",
  "Arbeidsplassvurdering",
  "God kjennskap til offentlige virkemidler",
  "Karriereveiledning",
];

export default function SkjemaSteg2Page() {
  const form = useFormContext<SoknadInputs>();
  const { control, register, setValue, formState } = form;
  const { errors } = formState;
  const { focusKey: errorFocusKey, bumpFocusKey } = useErrorFocus();

  const { draftId, clearDraft, lastPersistedAt, saveError } = useSoknadDraft();
  const {
    goToSoknader,
    goToSoknaderWithSaveNotice,
    goToStep1,
    goToStep3,
    goToSummary,
    createLinkHandler,
  } = useSkjemaNavigation();
  const [fnr, navn, virksomhetsnummer, virksomhetsnavn] = useWatch({
    control,
    name: [
      "ansatt.fnr",
      "ansatt.navn",
      "virksomhet.virksomhetsnummer",
      "virksomhet.virksomhetsnavn",
    ],
  });
  const {
    adresse,
    isLoading: adresseLoading,
    error: adresseError,
  } = useVirksomhetAdresse(virksomhetsnummer);

  useAttemptedSubmitRedirect(form, { fields: STEP2_FIELDS, onValidationFailed: bumpFocusKey });

  const handleStepOneLink = createLinkHandler(goToStep1);
  const handleStepThreeLink = createLinkHandler(goToStep3);
  const handleSummaryLink = createLinkHandler(goToSummary);

  const handleSubmitStep2 = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const valid = await form.trigger(STEP2_FIELDS);
    if (!valid) {
      bumpFocusKey();
      return;
    }
    goToStep3();
  };

  return (
    <DecoratedPage>
      <form onSubmit={handleSubmitStep2} noValidate autoComplete="off">
        <VStack gap="space-32">
          <Heading level="1" size="xlarge">
            Søknadsskjema – ekspertbistand
          </Heading>

          <VStack gap="space-12">
            <BackLink to={`/skjema/${draftId}/steg-1`} onClick={handleStepOneLink}>
              Forrige steg
            </BackLink>
            <SkjemaFormProgress
              activeStep={2}
              onStep1={handleStepOneLink}
              onStep3={handleStepThreeLink}
              onSummary={handleSummaryLink}
            />
          </VStack>

          <Fieldset legend="Deltaker" style={FORM_COLUMN_STYLE}>
            <VStack gap="space-16">
              <TextField id="ansatt.fnr.laast" label="Fødselsnummer" value={fnr} readOnly />
              <TextField id="ansatt.navn.laast" label="Navn" value={navn} readOnly />
              <div>
                <TextField
                  id="virksomhet.laast"
                  label="Virksomheten som deltakeren er ansatt i"
                  description={`Org.nr. ${virksomhetsnummer}`}
                  value={virksomhetsnavn}
                  readOnly
                />
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
              </div>
            </VStack>
          </Fieldset>

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

          <Fieldset
            legend="Ekspert"
            description="Må ha offentlig godkjent utdanning eller autorisasjon. Bedriftshelsetjenesten kan bare brukes som ekspert hvis hjelpen går utover det som vanligvis inngår i avtalen."
            style={FORM_COLUMN_STYLE}
          >
            <VStack gap="space-16">
              <TextField
                id="ekspert.navn"
                label="Navn"
                description="Personen som skal utføre oppdraget."
                error={errors.ekspert?.navn?.message}
                {...register("ekspert.navn")}
              />
              <Controller
                name="ekspert.virksomhet"
                control={control}
                render={({ field, fieldState }) => (
                  <EkspertVirksomhetVelger
                    value={field.value ?? ""}
                    onChange={(organisasjon) => {
                      field.onChange(formaterVirksomhet(organisasjon?.navn, organisasjon?.orgnr));
                      setValue("ekspert.virksomhetNavn", organisasjon?.navn ?? null, {
                        shouldDirty: true,
                      });
                      setValue("ekspert.virksomhetOrgnr", organisasjon?.orgnr ?? null, {
                        shouldDirty: true,
                      });
                    }}
                    error={fieldState.error?.message}
                  />
                )}
              />
              <Controller
                name="ekspert.godkjentUtdanningEllerAutorisasjon"
                control={control}
                render={({ field, fieldState }) => (
                  <UNSAFE_Combobox
                    id="ekspert.godkjentUtdanningEllerAutorisasjon"
                    label="Offentlig godkjent utdanning eller autorisasjon"
                    description={"Velg i listen eller skriv inn egen."}
                    options={GODKJENT_UTDANNING_ALTERNATIVER}
                    isMultiSelect
                    allowNewValues
                    selectedOptions={field.value}
                    onToggleSelected={(option, isSelected) => {
                      field.onChange(
                        isSelected
                          ? [...field.value, option]
                          : field.value.filter((v) => v !== option)
                      );
                    }}
                    error={fieldState.error?.message}
                  />
                )}
              />
              <Controller
                name="ekspert.relevantKompetanse"
                control={control}
                render={({ field, fieldState }) => (
                  <UNSAFE_Combobox
                    id="ekspert.relevantKompetanse"
                    label="Relevant kompetanse for denne saken"
                    description={"Velg i listen eller skriv inn egen."}
                    options={RELEVANT_KOMPETANSE_ALTERNATIVER}
                    isMultiSelect
                    allowNewValues
                    selectedOptions={field.value}
                    onToggleSelected={(option, isSelected) => {
                      field.onChange(
                        isSelected
                          ? [...field.value, option]
                          : field.value.filter((v) => v !== option)
                      );
                    }}
                    error={fieldState.error?.message}
                  />
                )}
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
            fields={STEP2_FIELDS}
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
