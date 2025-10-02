import React, { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  type SubmitHandler,
  type SubmitErrorHandler,
  type FieldError,
  useForm,
} from "react-hook-form";
import type { JSONSchemaType } from "ajv";
import { ajvResolver } from "@hookform/resolvers/ajv";
import soknadSchema from "shared/schemas/soknad.schema.json";
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
  virksomhet: {
    virksomhetsnummer: string;
    kontaktperson: {
      navn: string;
      epost: string;
      telefon: string;
    };
  };
  ansatt: {
    fodselsnummer: string;
    navn: string;
  };
  ekspert: {
    navn: string;
    virksomhet: string;
    kompetanse: string;
    problemstilling: string;
  };
  tiltak: {
    forTilrettelegging: string;
  };
  bestilling: {
    kostnad: number | string;
    startDato: string | null;
  };
  nav: {
    kontakt: string;
  };
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
    resolver: ajvResolver(soknadSchema as JSONSchemaType<Inputs>, { validateSchema: false }),
    defaultValues: {
      bestilling: {
        startDato: today.toISOString(),
      },
    },
  });

  const { datepickerProps, inputProps } = useDatepicker({
    defaultSelected: today,
    fromDate: today,
    onDateChange: (date) => {
      setValue("bestilling.startDato", date ? date.toISOString() : null, {
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true,
      });
    },
  });

  const startDatoReg = register("bestilling.startDato");
  const startDatoValue = watch("bestilling.startDato");

  const flattenErrors = (obj: unknown, prefix = ""): Array<{ name: string; message: string }> => {
    if (!obj || typeof obj !== "object") return [];
    const out: Array<{ name: string; message: string }> = [];
    for (const [key, val] of Object.entries(obj as Record<string, unknown>)) {
      const path = prefix ? `${prefix}.${key}` : key;
      if (!val || typeof val !== "object") continue;
      const msg = (val as { message?: unknown }).message;
      if (msg) {
        out.push({ name: path, message: String(msg) });
        continue;
      }
      out.push(...flattenErrors(val, path));
    }
    return out;
  };

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
                id="virksomhet.virksomhetsnummer"
                label="Virksomhetsnummer"
                htmlSize={9}
                inputMode="numeric"
                error={errors.virksomhet?.virksomhetsnummer?.message}
                {...register("virksomhet.virksomhetsnummer")}
              />
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
                id="virksomhet.kontaktperson.telefon"
                label="Telefonnummer"
                inputMode="numeric"
                htmlSize={8}
                error={errors.virksomhet?.kontaktperson?.telefon?.message}
                {...register("virksomhet.kontaktperson.telefon")}
              />
            </VStack>
          </Fieldset>

          <Heading level="2" size="large">
            Ansatt
          </Heading>
          <Fieldset legend="Ansatt" hideLegend>
            <VStack gap="6">
              <TextField
                id="ansatt.fodselsnummer"
                label="Fødselsnummer"
                htmlSize={11}
                error={errors.ansatt?.fodselsnummer?.message}
                {...register("ansatt.fodselsnummer")}
              />
              <TextField
                id="ansatt.navn"
                label="Navn"
                error={errors.ansatt?.navn?.message}
                {...register("ansatt.navn")}
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
                label="Kompetanse"
                placeholder="f.eks. psykolog, ergoterapeut, fysioterapeut"
                error={errors.ekspert?.kompetanse?.message}
                {...register("ekspert.kompetanse")}
              />
              <Textarea
                id="ekspert.problemstilling"
                label="Beskriv problemstillingen som eksperten skal bistå med"
                placeholder="Sykefraværshistorikk, arbeidsgiver ..."
                error={errors.ekspert?.problemstilling?.message}
                {...register("ekspert.problemstilling")}
              />
              <Textarea
                id="tiltak.forTilrettelegging"
                label="Hvilke tiltak for tilrettelegging har dere allerede gjort, vurdert eller forsøkt?"
                placeholder="f.eks. fleksibel arbeidstid, hjemmekontor, tilpassing av arbeidsoppgaver, hjelpemiddel, opplæring, ekstra oppfølging."
                error={errors.tiltak?.forTilrettelegging?.message}
                {...register("tiltak.forTilrettelegging")}
              />
              <TextField
                id="bestilling.kostnad"
                label="Anslå kostnad for ekspertbistand"
                type="number"
                inputMode="numeric"
                max={25000}
                error={(errors.bestilling?.kostnad as FieldError | undefined)?.message}
                {...register("bestilling.kostnad", { valueAsNumber: true })}
              />
              <div>
                <Box paddingBlock="0">
                  <DatePicker {...datepickerProps}>
                    <DatePicker.Input
                      {...inputProps}
                      id="bestilling.startDato"
                      label="Fra hvilken dato skal ekspertbistanden benyttes?"
                      error={errors.bestilling?.startDato?.message}
                      onBlur={(e) => {
                        inputProps.onBlur?.(e);
                        trigger("bestilling.startDato");
                      }}
                    />
                  </DatePicker>
                  <input
                    type="hidden"
                    name={startDatoReg.name}
                    ref={startDatoReg.ref}
                    value={startDatoValue ? String(startDatoValue) : ""}
                    readOnly
                  />
                </Box>
              </div>
              <TextField
                id="nav.kontakt"
                label="Hvem i Nav har du drøftet behovet for ekspertbistand i denne saken med?"
                error={errors.nav?.kontakt?.message}
                {...register("nav.kontakt", {
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
              {flattenErrors(errors).map(({ name, message }) => (
                <ErrorSummary.Item key={name} href={`#${name}`}>
                  {message}
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
