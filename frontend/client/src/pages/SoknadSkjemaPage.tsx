import React, { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { type SubmitHandler, type SubmitErrorHandler, useForm } from "react-hook-form";
import { ArrowLeftIcon, ArrowRightIcon } from "@navikt/aksel-icons";
import {
  Button,
  ErrorSummary,
  HGrid,
  Heading,
  TextField,
  Textarea,
  VStack,
  BodyLong,
  Fieldset,
  Box,
  DatePicker,
  useDatepicker,
  Link,
  FormProgress,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";

type Inputs = {
  kontaktpersonNavn: string;
  kontaktpersonEpost: string;
  kontaktpersonTelefon: string;

  ansattFodselsnummer: string;
  ansattNavn: string;

  ekspertNavn: string;
  ekspertVirksomhet: string;
  ekspertKompetanse: string;
  ekspertProblemstilling: string;
  tiltakForTilrettelegging: string;
  kostnad: number | string;
  startDato: Date | null;
  navKontakt: string;
};

export default function SoknadSkjemaPage() {
  const navigate = useNavigate();
  const errorSummaryRef = React.useRef<HTMLDivElement>(null);

  const today = new Date();

  const {
    register,
    handleSubmit,
    trigger,
    setValue,
    watch,
    formState: { errors },
  } = useForm<Inputs>({
    reValidateMode: "onBlur",
    shouldFocusError: false,
    defaultValues: {
      startDato: today,
    },
  });

  const { datepickerProps, inputProps } = useDatepicker({
    defaultSelected: today,
    fromDate: today,
    onDateChange: (date) => {
      setValue("startDato", date ?? null, {
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true,
      });
    },
  });

  const startDatoReg = register("startDato", {
    required: "Du må velge en dato.",
  });
  const startDatoValue = watch("startDato");

  const onValidSubmit: SubmitHandler<Inputs> = (data) => {
    console.info("Søknadsskjema data", data);
    navigate("/oppsummering");
  };

  const onInvalidSubmit: SubmitErrorHandler<Inputs> = () => {
    errorSummaryRef.current?.focus();
  };

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: "auto" });
  }, []);

  return (
    <DecoratedPage
      blockProps={{ width: "lg", gutters: true }}
      languages={[
        { locale: "nb", url: "https://www.nav.no" },
        { locale: "en", url: "https://www.nav.no/en" },
      ]}
    >
      <form onSubmit={handleSubmit(onValidSubmit, onInvalidSubmit)}>
        <VStack gap="8">
          <Heading level="1" size="xlarge">
            Søknadsskjema – ekspertbistand
          </Heading>

          <VStack gap="3">
            <Link
              href="#"
              onClick={(e) => {
                e.preventDefault();
                navigate("/");
              }}
            >
              <ArrowLeftIcon aria-hidden /> Forrige steg
            </Link>
            <FormProgress activeStep={1} totalSteps={2}>
              <FormProgress.Step href="#" onClick={(e) => e.preventDefault()}>
                Skjema
              </FormProgress.Step>
              <FormProgress.Step href="#" onClick={(e) => e.preventDefault()}>
                Oppsummering
              </FormProgress.Step>
            </FormProgress>
          </VStack>

          <Heading level="2" size="large">
            Kontaktperson i virksomheten
          </Heading>
          <Fieldset legend="Kontaktperson i virksomheten" hideLegend>
            <VStack gap="6">
              <TextField
                id="kontaktpersonNavn"
                label="Navn"
                error={errors.kontaktpersonNavn?.message}
                {...register("kontaktpersonNavn", {
                  required: "Du må fylle ut navn.",
                })}
              />
              <TextField
                id="kontaktpersonEpost"
                label="E-post"
                type="email"
                error={errors.kontaktpersonEpost?.message}
                {...register("kontaktpersonEpost", {
                  required: "Du må fylle ut e-post.",
                  pattern: {
                    value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                    message: "Ugyldig e-postadresse.",
                  },
                })}
              />
              <TextField
                id="kontaktpersonTelefon"
                label="Telefonnummer"
                inputMode="numeric"
                htmlSize={8}
                error={errors.kontaktpersonTelefon?.message}
                {...register("kontaktpersonTelefon", {
                  required: "Du må fylle ut telefonnummer.",
                  pattern: {
                    value: /^\d{8}$/,
                    message: "Telefonnummer må være 8 siffer.",
                  },
                })}
              />
            </VStack>
          </Fieldset>

          <Heading level="2" size="large">
            Ansatt
          </Heading>
          <Fieldset legend="Ansatt" hideLegend>
            <VStack gap="6">
              <TextField
                id="ansattFodselsnummer"
                label="Fødselsnummer"
                htmlSize={11}
                error={errors.ansattFodselsnummer?.message}
                {...register("ansattFodselsnummer", {
                  required: "Du må fylle ut fødselsnummer.",
                  pattern: {
                    value: /^\d{11}$/,
                    message: "Fødselsnummer må være 11 siffer.",
                  },
                })}
              />
              <TextField
                id="ansattNavn"
                label="Navn"
                error={errors.ansattNavn?.message}
                {...register("ansattNavn", {
                  required: "Du må fylle ut navn.",
                })}
              />
            </VStack>
          </Fieldset>

          <VStack gap="3">
            <Heading level="2" size="large">
              Ekspert
            </Heading>
            <BodyLong>Må ha offentlig godkjenning eller autorisasjon</BodyLong>
          </VStack>
          <Fieldset legend="Ekspert" hideLegend>
            <VStack gap="6">
              <TextField
                id="ekspertNavn"
                label="Navn"
                error={errors.ekspertNavn?.message}
                {...register("ekspertNavn", {
                  required: "Du må fylle ut navn på ekspert.",
                })}
              />
              <TextField
                id="ekspertVirksomhet"
                label="Tilknyttet virksomhet"
                error={errors.ekspertVirksomhet?.message}
                {...register("ekspertVirksomhet", {
                  required: "Du må fylle ut tilknyttet virksomhet.",
                })}
              />
              <TextField
                id="ekspertKompetanse"
                label="Kompetanse"
                placeholder="f.eks. psykolog, ergoterapeut, fysioterapeut"
                error={errors.ekspertKompetanse?.message}
                {...register("ekspertKompetanse", {
                  required: "Du må beskrive ekspertens kompetanse.",
                })}
              />
              <Textarea
                id="ekspertProblemstilling"
                label="Beskriv problemstillingen som eksperten skal bistå med"
                placeholder="Sykefraværshistorikk, arbeidsgiver ..."
                error={errors.ekspertProblemstilling?.message}
                {...register("ekspertProblemstilling", {
                  required: "Du må beskrive problemstillingen.",
                })}
              />
              <Textarea
                id="tiltakForTilrettelegging"
                label="Hvilke tiltak for tilrettelegging har dere allerede gjort, vurdert eller forsøkt?"
                placeholder="f.eks. fleksibel arbeidstid, hjemmekontor, tilpassing av arbeidsoppgaver, hjelpemiddel, opplæring, ekstra oppfølging."
                error={errors.tiltakForTilrettelegging?.message}
                {...register("tiltakForTilrettelegging", {
                  required: "Du må beskrive tiltak for tilrettelegging.",
                })}
              />
              <TextField
                id="kostnad"
                label="Anslå kostnad for ekspertbistand"
                type="number"
                inputMode="numeric"
                max={25000}
                error={errors.kostnad?.message}
                {...register("kostnad", {
                  required: "Du må anslå kostnad.",
                  valueAsNumber: true,
                  min: { value: 0, message: "Kostnad kan ikke være negativ." },
                  max: { value: 25000, message: "Maksimalt beløp er 25 000." },
                })}
              />
              <div>
                <Box paddingBlock="0">
                  <DatePicker {...datepickerProps}>
                    <DatePicker.Input
                      {...inputProps}
                      id="startDato"
                      label="Fra hvilken dato skal ekspertbistanden benyttes?"
                      error={errors.startDato?.message}
                      onBlur={(e) => {
                        inputProps.onBlur?.(e);
                        trigger("startDato");
                      }}
                    />
                  </DatePicker>
                  <input
                    type="hidden"
                    name={startDatoReg.name}
                    ref={startDatoReg.ref}
                    value={startDatoValue ? (startDatoValue as Date).toISOString() : ""}
                    readOnly
                  />
                </Box>
              </div>
              <TextField
                id="navKontakt"
                label="Hvem i Nav har du drøftet behovet for ekspertbistand i denne saken med?"
                error={errors.navKontakt?.message}
                {...register("navKontakt", {
                  required: "Du må fylle ut hvem i Nav du har drøftet med.",
                })}
              />
            </VStack>
          </Fieldset>

          {Object.values(errors).length > 0 && (
            <ErrorSummary
              ref={errorSummaryRef}
              heading="Du må rette disse feilene før du kan fortsette:"
            >
              {Object.entries(errors).map(([key, error]) => (
                <ErrorSummary.Item key={key} href={`#${key}`}>
                  {error?.message as string}
                </ErrorSummary.Item>
              ))}
            </ErrorSummary>
          )}

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

          <HGrid
            gap={{ xs: "4", sm: "8 4" }}
            columns={{ xs: 1, sm: 2 }}
            width={{ sm: "fit-content" }}
          >
            <Button type="button" variant="tertiary">
              Fortsett senere
            </Button>
            <Button type="button" variant="tertiary">
              Slett søknaden
            </Button>
          </HGrid>
        </VStack>
      </form>
    </DecoratedPage>
  );
}
