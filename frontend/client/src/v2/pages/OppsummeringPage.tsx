import { useCallback, useState } from "react";
import type React from "react";
import { type Path, useFormContext } from "react-hook-form";
import { ArrowLeftIcon, PaperplaneIcon } from "@navikt/aksel-icons";
import {
  Alert,
  BodyLong,
  Box,
  Button,
  FormSummary,
  Heading,
  HGrid,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import {
  sjekkSoknadssumMotMaks,
  soknadSchema,
  STEP1_FIELDS,
  STEP2_FIELDS,
  STEP3_FIELDS,
} from "../features/soknad/schema";
import { useLagretUtkastId, useSoknadDraft } from "../context/SoknadDraftContext";
import { DraftActions } from "../components/DraftActions.tsx";
import { EKSPERTBISTAND_API_PATH, LOGIN_URL } from "../utils/constants";
import { BackLink } from "../components/BackLink";
import useSWRMutation from "swr/mutation";
import { fetchJson } from "../../utils/api";
import { useSkjemaNavigation } from "../hooks/useSkjemaNavigation";
import { SkjemaFormProgress } from "../components/SkjemaFormProgress";
import { useNavigate } from "react-router";
import { HttpError, resolveApiError, type ApiErrorInfo } from "../../utils/http";
import { SistLagretInfo } from "../components/SistLagretInfo.tsx";
import {
  formatCurrency,
  formatDate,
  formatStringArray,
  formatTimer,
  formatValue,
} from "../components/summaryFormatters";
import type { SoknadInputs } from "../features/soknad/schema";
import { useDraftAutosave } from "../context/DraftAutosaveContext";
import { FormSummaryAnswer } from "@navikt/ds-react/FormSummary";
import { useVirksomhetAdresse } from "../hooks/useVirksomhetAdresse";
import { useMaksSats } from "../hooks/useMaksSats";

type SoknadSummaryProps = {
  data: SoknadInputs;
  editable?: boolean;
  onEditStep1?: React.MouseEventHandler<HTMLAnchorElement>;
  onEditStep2?: React.MouseEventHandler<HTMLAnchorElement>;
  onEditStep3?: React.MouseEventHandler<HTMLAnchorElement>;
};

function SoknadSummary({
  data,
  editable = false,
  onEditStep1,
  onEditStep2,
  onEditStep3,
}: SoknadSummaryProps) {
  const { virksomhet, ansatt, ekspert, behovForBistand, nav } = data;
  const {
    adresse,
    isLoading: adresseLoading,
    error: adresseError,
  } = useVirksomhetAdresse(virksomhet.virksomhetsnummer);

  return (
    <>
      <FormSummary>
        <FormSummary.Header>
          <FormSummary.Heading level="2">Deltaker</FormSummary.Heading>
        </FormSummary.Header>
        <FormSummary.Answers>
          <FormSummary.Answer>
            <FormSummary.Label>Navn</FormSummary.Label>
            <FormSummary.Value>{formatValue(ansatt.navn)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Fødselsnummer</FormSummary.Label>
            <FormSummary.Value>{formatValue(ansatt.fnr)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Navn på virksomhet</FormSummary.Label>
            <FormSummary.Value>{formatValue(virksomhet.virksomhetsnavn)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Organisasjonsnummer</FormSummary.Label>
            <FormSummary.Value>{formatValue(virksomhet.virksomhetsnummer)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Beliggenhetsadresse</FormSummary.Label>
            <FormSummary.Value>
              {adresseError
                ? "Kunne ikke hente beliggenhetsadresse."
                : adresseLoading
                  ? "Laster ..."
                  : formatValue(adresse)}
            </FormSummary.Value>
          </FormSummary.Answer>
        </FormSummary.Answers>
        {editable && onEditStep1 && (
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={onEditStep1} />
          </FormSummary.Footer>
        )}
      </FormSummary>

      <FormSummary>
        <FormSummary.Header>
          <FormSummary.Heading level="2">Kontaktperson og ekspert</FormSummary.Heading>
        </FormSummary.Header>
        <FormSummary.Answers>
          <FormSummaryAnswer>
            <FormSummary.Label>Kontaktperson i virksomheten</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(virksomhet.kontaktperson.navn)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>E-post</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(virksomhet.kontaktperson.epost)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Telefonnummer</FormSummary.Label>
                  <FormSummary.Value>
                    {formatValue(virksomhet.kontaktperson.telefonnummer)}
                  </FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummaryAnswer>

          <FormSummaryAnswer>
            <FormSummary.Label>Ekspert</FormSummary.Label>
            <FormSummary.Value>
              <FormSummary.Answers>
                <FormSummary.Answer>
                  <FormSummary.Label>Navn</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ekspert.navn)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Tilknyttet virksomhet</FormSummary.Label>
                  <FormSummary.Value>{formatValue(ekspert.virksomhet)}</FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>
                    Offentlig godkjent utdanning eller autorisasjon
                  </FormSummary.Label>
                  <FormSummary.Value>
                    {formatStringArray(ekspert.godkjentUtdanningEllerAutorisasjon)}
                  </FormSummary.Value>
                </FormSummary.Answer>
                <FormSummary.Answer>
                  <FormSummary.Label>Relevant kompetanse for denne saken</FormSummary.Label>
                  <FormSummary.Value>
                    {formatStringArray(ekspert.relevantKompetanse)}
                  </FormSummary.Value>
                </FormSummary.Answer>
              </FormSummary.Answers>
            </FormSummary.Value>
          </FormSummaryAnswer>
          <FormSummary.Answer>
            <FormSummary.Label>
              Hvem i Nav har du drøftet behovet om ekspertbistand i denne saken med?
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(nav.kontaktperson)}</FormSummary.Value>
          </FormSummary.Answer>
        </FormSummary.Answers>
        {editable && onEditStep2 && (
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={onEditStep2} />
          </FormSummary.Footer>
        )}
      </FormSummary>

      <FormSummary>
        <FormSummary.Header>
          <FormSummary.Heading level="2">Behov for bistand</FormSummary.Heading>
        </FormSummary.Header>
        <FormSummary.Answers>
          <FormSummary.Answer>
            <FormSummary.Label>
              Beskriv den ansattes arbeidssituasjon, sykefravær og hvorfor dere ser behov for
              ekspertbistand
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(behovForBistand.begrunnelse)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Hva vil dere ha hjelp til fra eksperten?</FormSummary.Label>
            <FormSummary.Value>{formatValue(behovForBistand.behov)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>
              Hvilke tiltak for tilrettelegging har dere allerede gjort, vurdert eller forsøkt?
            </FormSummary.Label>
            <FormSummary.Value>{formatValue(behovForBistand.tilrettelegging)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Hvor mange timer skal eksperten hjelpe dere?</FormSummary.Label>
            <FormSummary.Value>{formatTimer(behovForBistand.timer)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Søknadssum</FormSummary.Label>
            <FormSummary.Value>{formatCurrency(behovForBistand.estimertKostnad)}</FormSummary.Value>
          </FormSummary.Answer>
          <FormSummary.Answer>
            <FormSummary.Label>Startdato</FormSummary.Label>
            <FormSummary.Value>{formatDate(behovForBistand.startdato)}</FormSummary.Value>
          </FormSummary.Answer>
        </FormSummary.Answers>
        {editable && onEditStep3 && (
          <FormSummary.Footer>
            <FormSummary.EditLink href="#" onClick={onEditStep3} />
          </FormSummary.Footer>
        )}
      </FormSummary>
    </>
  );
}

type Felt = Path<SoknadInputs>;

// Backend validerer felt som ikke vises i skjemaet. Feilen vises på feltet arbeidsgiver fyller ut.
const SYNLIG_FELT: Partial<Record<string, Felt>> = {
  "virksomhet.virksomhetsnavn": "virksomhet.virksomhetsnummer",
  "ekspert.virksomhetNavn": "ekspert.virksomhet",
  "ekspert.virksomhetOrgnr": "ekspert.virksomhet",
};

const FEILMELDING: Partial<Record<Felt, string>> = {
  "behovForBistand.estimertKostnad":
    "Søknadssummen er ugyldig eller over maksgrensen for startdatoen.",
  "behovForBistand.startdato": "Startdatoen er ugyldig, eller ingen maksgrense gjelder for den.",
};

const finnSteg = (felt: Felt): 1 | 2 | 3 | null => {
  if ((STEP1_FIELDS as readonly Felt[]).includes(felt)) return 1;
  if ((STEP2_FIELDS as readonly Felt[]).includes(felt)) return 2;
  if ((STEP3_FIELDS as readonly Felt[]).includes(felt)) return 3;
  return null;
};

export default function OppsummeringPage() {
  const navigate = useNavigate();
  const draftId = useLagretUtkastId();
  const { clearDraft, lastPersistedAt, saveError, ventPaLagring } = useSoknadDraft();
  const { flushDraft } = useDraftAutosave();
  const { watch, getValues, setError } = useFormContext<SoknadInputs>();
  const formData = watch();
  const {
    maksSats,
    isLoading: satsLoading,
    error: satsError,
    mutate: hentSats,
  } = useMaksSats(formData.behovForBistand.startdato);
  const {
    goToSoknader,
    goToSoknaderWithSaveNotice,
    goToStep1,
    goToStep2,
    goToStep3,
    createLinkHandler,
  } = useSkjemaNavigation();
  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);
  const [lagrer, setLagrer] = useState(false);
  const { trigger: submitDraft, isMutating: submitting } = useSWRMutation<
    null,
    Error,
    [string, string],
    RequestInit
  >(["submit-draft", draftId], ([, id], { arg }) =>
    fetchJson<null>(`${EKSPERTBISTAND_API_PATH}/${id}`, arg)
  );

  const handleStep1Link = createLinkHandler(goToStep1);
  const handleStep2Link = createLinkHandler(goToStep2);
  const handleStep3Link = createLinkHandler(goToStep3);

  const handleSubmit = useCallback(async () => {
    const result = soknadSchema.safeParse(getValues());
    if (!result.success) {
      setSubmitError({
        message:
          "Du må fylle ut alle feltene før du sender inn. Gå tilbake til stegene og fyll inn manglende opplysninger.",
        requiresLogin: false,
      });
      window.scrollTo({ top: 0, behavior: "smooth" });
      return;
    }
    if (!maksSats || satsLoading || satsError) {
      setSubmitError({
        message: "Kunne ikke hente maksgrensen for startdatoen. Prøv igjen før du sender inn.",
        requiresLogin: false,
      });
      return;
    }
    const belopsfeil = sjekkSoknadssumMotMaks(
      result.data.behovForBistand.estimertKostnad,
      maksSats.belop
    );
    if (belopsfeil) {
      setError("behovForBistand.estimertKostnad", { type: "maksSats", message: belopsfeil });
      goToStep3();
      return;
    }
    setSubmitError(null);
    flushDraft();
    setLagrer(true);
    const lagret = await ventPaLagring();
    setLagrer(false);
    if (!lagret) {
      setSubmitError({
        message: "Vi fikk ikke lagret de siste endringene, så søknaden er ikke sendt. Prøv igjen.",
        requiresLogin: false,
      });
      window.scrollTo({ top: 0, behavior: "smooth" });
      return;
    }
    try {
      // Backend sender inn utkastet slik det er lagret.
      await submitDraft({ method: "PUT" });
      navigate(`/v2/skjema/${draftId}/kvittering`, {
        replace: true,
        state: { submissionSuccess: true },
      });
    } catch (error) {
      const serverFelt = error instanceof HttpError ? error.field : undefined;
      const felt = serverFelt ? (SYNLIG_FELT[serverFelt] ?? (serverFelt as Felt)) : null;
      const steg = felt ? finnSteg(felt) : null;
      if (felt && steg) {
        setError(felt, {
          type: "server",
          message: FEILMELDING[felt] ?? "Fyll ut feltet riktig før du sender inn.",
        });
        [goToStep1, goToStep2, goToStep3][steg - 1]();
        return;
      }
      setSubmitError(resolveApiError(error, "Kunne ikke sende søknaden akkurat nå."));
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  }, [
    draftId,
    getValues,
    navigate,
    submitDraft,
    maksSats,
    satsLoading,
    satsError,
    setError,
    flushDraft,
    ventPaLagring,
    goToStep1,
    goToStep2,
    goToStep3,
  ]);

  return (
    <DecoratedPage>
      <VStack gap="space-32" data-aksel-template="form-summarypage-v5">
        <VStack>
          <Heading level="1" size="xlarge">
            Oppsummering av søknad om ekspertbistand
          </Heading>
        </VStack>

        <div>
          <BackLink to={`/v2/skjema/${draftId}/steg-3`} onClick={handleStep3Link}>
            Forrige steg
          </BackLink>
          <Box paddingBlock="space-32 space-16">
            <Heading level="2" size="large">
              Oppsummering
            </Heading>
          </Box>
          <SkjemaFormProgress
            activeStep={4}
            onStep1={handleStep1Link}
            onStep2={handleStep2Link}
            onStep3={handleStep3Link}
          />
        </div>

        <BodyLong>
          Nå kan du se over at alt er riktig før du sender inn søknaden. Ved behov kan du endre
          opplysningene.
        </BodyLong>

        {submitError && (
          <Alert variant="error" role="alert">
            <VStack gap="space-16">
              <BodyLong>{submitError.message}</BodyLong>
              {submitError.requiresLogin ? (
                <Button as="a" href={LOGIN_URL} size="small" variant="primary">
                  Logg inn
                </Button>
              ) : null}
            </VStack>
          </Alert>
        )}
        {satsError && (
          <Alert variant="error">
            Kunne ikke hente maksgrensen for startdatoen. Prøv igjen før du sender inn.
            <Button type="button" variant="secondary" onClick={() => void hentSats()}>
              Prøv igjen
            </Button>
          </Alert>
        )}

        <SoknadSummary
          data={formData}
          editable
          onEditStep1={handleStep1Link}
          onEditStep2={handleStep2Link}
          onEditStep3={handleStep3Link}
        />

        <VStack gap="space-16">
          <BodyLong>
            Når du sender inn søknaden, får den ansatte også en kopi av søknaden. Du kommer til en
            kvitteringsside med informasjon om veien videre.
          </BodyLong>
          <SistLagretInfo timestamp={lastPersistedAt} saveError={saveError} />
          <HGrid
            gap={{ xs: "space-32", sm: "space-16" }}
            columns={{ xs: 1, sm: 2 }}
            width={{ sm: "fit-content" }}
          >
            <Button
              variant="secondary"
              icon={<ArrowLeftIcon aria-hidden />}
              iconPosition="left"
              onClick={goToStep3}
            >
              Forrige steg
            </Button>
            <Button
              variant="primary"
              icon={<PaperplaneIcon aria-hidden />}
              iconPosition="right"
              type="button"
              loading={lagrer || submitting || satsLoading}
              onClick={handleSubmit}
            >
              Send søknad
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
    </DecoratedPage>
  );
}
