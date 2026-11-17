import { type FormEvent, useEffect, useRef, useState } from "react";
import { Controller, useFormContext, useWatch } from "react-hook-form";
import { useLocation, useNavigate } from "react-router";
import { useSWRConfig } from "swr";
import { ArrowLeftIcon, ArrowRightIcon } from "@navikt/aksel-icons";
import {
  BodyShort,
  Button,
  ErrorMessage,
  ErrorSummary,
  Fieldset,
  HGrid,
  Heading,
  Label,
  Link,
  Loader,
  LocalAlert,
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
import { type StepSegment, useSkjemaNavigation } from "../hooks/useSkjemaNavigation";
import { EKSPERTBISTAND_API_PATH, SOKNADER_PATH, TILGANGSSTYRING_URL } from "../utils/constants";
import { SistLagretInfo } from "../components/SistLagretInfo.tsx";
import { useVirksomhetAdresse } from "../hooks/useVirksomhetAdresse";
import {
  opprettUtkast,
  validerDeltaker,
  type ValiderDeltakerFeil,
} from "../features/soknad/validerDeltaker";
import { BekreftEgenDeltakelseModal } from "../components/BekreftEgenDeltakelseModal";

const VIRKSOMHETSVELGER_ID = "virksomhet.virksomhetsnummer";

type Oppslagsfeil = Exclude<ValiderDeltakerFeil, { type: "ugyldigInput" }>;

/**
 * Dialogen om egen deltakelse. Finnes utkastet, vises den før oppslaget. Ellers vises den etter at
 * POST har opprettet utkastet, fordi frontend først da vet hvem som er logget inn ([nyttUtkastId]).
 */
type EgenDeltakelse = { segment: StepSegment; nyttUtkastId?: string };

type NavigasjonState = { fokuserFnr?: boolean } | null;

export default function SkjemaSteg1Page() {
  const form = useFormContext<SoknadInputs>();
  const { control, register, setValue, getValues, setError, setFocus, formState } = form;
  const { errors } = formState;
  const { focusKey: errorFocusKey, bumpFocusKey } = useErrorFocus();

  const {
    draftId,
    hydrated,
    clearDraft,
    lastPersistedAt,
    saveError,
    bekreftetDeltaker,
    opprettetAv,
    oppdaterUtkast,
  } = useSoknadDraft();
  const { goToSoknader, goToSoknaderWithSaveNotice, goToSegment, createLinkHandler } =
    useSkjemaNavigation();
  const navigate = useNavigate();
  const location = useLocation();
  const { mutate } = useSWRConfig();
  const nyttUtkast = draftId === null;
  const tilOversikt = nyttUtkast ? goToSoknader : goToSoknaderWithSaveNotice;
  const virksomhetsnummer = useWatch({ control, name: "virksomhet.virksomhetsnummer" });
  const {
    adresse,
    isLoading: adresseLoading,
    error: adresseError,
  } = useVirksomhetAdresse(virksomhetsnummer);
  const hasVirksomhet = Boolean(virksomhetsnummer?.trim());

  const [validerer, setValiderer] = useState(false);
  const [oppslagsfeil, setOppslagsfeil] = useState<Oppslagsfeil | null>(null);
  const [egenDeltakelse, setEgenDeltakelse] = useState<EgenDeltakelse | null>(null);
  const oppslagsfeilRef = useRef<HTMLDivElement | null>(null);
  const skalFokusereFnr = (location.state as NavigasjonState)?.fokuserFnr === true;

  useAttemptedSubmitRedirect(form, { fields: STEP1_FIELDS, onValidationFailed: bumpFocusKey });

  useEffect(() => {
    if (oppslagsfeil) oppslagsfeilRef.current?.focus();
  }, [oppslagsfeil]);

  useEffect(() => {
    if (skalFokusereFnr && hydrated) setFocus("ansatt.fnr");
  }, [hydrated, setFocus, skalFokusereFnr]);

  const gjorOppslag = async (segment: StepSegment) => {
    const fnr = getValues("ansatt.fnr").replace(/\D/g, "");
    const orgnr = getValues("virksomhet.virksomhetsnummer").trim();
    const request = { fnr, virksomhetsnummer: orgnr };
    setOppslagsfeil(null);
    setValiderer(true);
    const resultat = draftId
      ? await validerDeltaker(draftId, request)
      : await opprettUtkast(request);
    setValiderer(false);

    if (resultat.ok) {
      const { utkast } = resultat;
      if (draftId) {
        await oppdaterUtkast(utkast);
        setValue("ansatt.navn", utkast.ansatt?.navn ?? "");
        setValue("virksomhet.virksomhetsnavn", utkast.virksomhet?.virksomhetsnavn ?? "");
        goToSegment(segment);
        return;
      }
      if (!utkast.id) {
        setOppslagsfeil({ type: "oppslagFeilet" });
        return;
      }
      await mutate(`${EKSPERTBISTAND_API_PATH}/${utkast.id}`, utkast, { revalidate: false });
      if (utkast.opprettetAv === fnr) {
        setEgenDeltakelse({ segment, nyttUtkastId: utkast.id });
        return;
      }
      goToSegment(segment, utkast.id);
      return;
    }

    const { feil } = resultat;
    if (feil.type === "ugyldigInput") {
      if (feil.felt === "virksomhetsnummer") {
        setError("virksomhet.virksomhetsnummer", {
          type: "server",
          message: "Virksomhetsnummeret er ugyldig. Velg virksomheten på nytt.",
        });
      } else {
        setError("ansatt.fnr", { type: "server", message: "Fødselsnummeret er ugyldig." });
      }
      bumpFocusKey();
      return;
    }
    setOppslagsfeil(feil);
  };

  const gaVidere = async (segment: StepSegment) => {
    if (validerer) return;
    const valid = await form.trigger(STEP1_FIELDS);
    if (!valid) {
      setOppslagsfeil(null);
      bumpFocusKey();
      return;
    }
    const fnr = getValues("ansatt.fnr").replace(/\D/g, "");
    const orgnr = getValues("virksomhet.virksomhetsnummer").trim();
    if (bekreftetDeltaker?.fnr === fnr && bekreftetDeltaker.virksomhetsnummer === orgnr) {
      goToSegment(segment);
      return;
    }
    if (opprettetAv && fnr === opprettetAv) {
      setEgenDeltakelse({ segment });
      return;
    }
    await gjorOppslag(segment);
  };

  const handleBekreftEgenDeltakelse = () => {
    const valg = egenDeltakelse;
    setEgenDeltakelse(null);
    if (!valg) return;
    if (valg.nyttUtkastId) {
      goToSegment(valg.segment, valg.nyttUtkastId);
      return;
    }
    void gjorOppslag(valg.segment);
  };

  const handleAvbrytEgenDeltakelse = () => {
    const valg = egenDeltakelse;
    setEgenDeltakelse(null);
    if (valg?.nyttUtkastId) {
      // Utkastet er opprettet. Arbeidsgiver endrer fødselsnummeret på utkastet, med valider-deltaker.
      navigate(`/skjema/${valg.nyttUtkastId}/steg-1`, {
        replace: true,
        state: { fokuserFnr: true },
      });
      return;
    }
    setFocus("ansatt.fnr");
  };

  const handleBackLink = createLinkHandler(tilOversikt);
  const handleStepTwoLink = createLinkHandler(() => void gaVidere("steg-2"));
  const handleStepThreeLink = createLinkHandler(() => void gaVidere("steg-3"));
  const handleSummaryLink = createLinkHandler(() => void gaVidere("oppsummering"));

  const handleSubmitStep1 = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await gaVidere("steg-2");
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
                    id={VIRKSOMHETSVELGER_ID}
                    ref={field.ref}
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

          {oppslagsfeil?.type === "ingenArbeidsforhold" ? (
            <VStack gap="space-16">
              <ErrorSummary
                heading="For å gå videre må du rette opp følgende:"
                ref={oppslagsfeilRef}
              >
                <ErrorSummary.Item href={`#${VIRKSOMHETSVELGER_ID}`}>
                  Velg virksomheten som deltaker er ansatt i, det vil si virksomheten dere
                  rapporterer deltakerens lønn på i A-meldingen.
                </ErrorSummary.Item>
              </ErrorSummary>
              <BodyShort>
                Hvis du ikke kan velge riktig virksomhet her, må du{" "}
                <Link href={TILGANGSSTYRING_URL} target="_blank" rel="noreferrer">
                  be om tilgang til enkelttjenesten «Tilskudd til ekspertbistand»
                </Link>{" "}
                for riktig virksomhet i Altinn.
              </BodyShort>
            </VStack>
          ) : null}
          {oppslagsfeil?.type === "forMangeOppslag" ? (
            <LocalAlert status="warning" ref={oppslagsfeilRef} tabIndex={-1}>
              <LocalAlert.Content>
                Du har slått opp for mange ulike fødselsnummer på kort tid. Vent 5 minutter og prøv
                igjen.
              </LocalAlert.Content>
            </LocalAlert>
          ) : null}
          {oppslagsfeil?.type === "oppslagFeilet" ? (
            <LocalAlert status="error" ref={oppslagsfeilRef} tabIndex={-1}>
              <LocalAlert.Content>
                Vi kunne ikke sjekke arbeidsforholdet akkurat nå. Prøv igjen senere.
              </LocalAlert.Content>
            </LocalAlert>
          ) : null}

          <VStack gap="space-16">
            {nyttUtkast ? null : (
              <SistLagretInfo timestamp={lastPersistedAt} saveError={saveError} />
            )}
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
                onClick={() => tilOversikt()}
              >
                Tilbake til oversikt
              </Button>
              <Button
                type="submit"
                variant="primary"
                icon={<ArrowRightIcon aria-hidden />}
                iconPosition="right"
                loading={validerer}
              >
                Neste steg
              </Button>
            </HGrid>
            {nyttUtkast ? null : (
              <DraftActions
                onContinueLater={() => {
                  goToSoknaderWithSaveNotice();
                }}
                onDeleteDraft={async () => {
                  await clearDraft();
                  goToSoknader();
                }}
              />
            )}
          </VStack>
        </VStack>
      </form>
      <BekreftEgenDeltakelseModal
        open={egenDeltakelse !== null}
        onBekreft={handleBekreftEgenDeltakelse}
        onAvbryt={handleAvbrytEgenDeltakelse}
      />
    </DecoratedPage>
  );
}
