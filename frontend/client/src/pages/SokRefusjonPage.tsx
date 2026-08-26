import { useMemo, useState } from "react";
import { useNavigate, useParams, Navigate } from "react-router";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Alert,
  BodyShort,
  Button,
  Checkbox,
  ErrorMessage,
  FileUpload,
  FormSummary,
  Heading,
  Loader,
  TextField,
  Textarea,
  VStack,
  type FileAccepted,
  type FileObject,
  type FileRejected,
} from "@navikt/ds-react";
import { PaperplaneIcon } from "@navikt/aksel-icons";
import DecoratedPage from "../components/DecoratedPage";
import { BackLink } from "../components/BackLink";
import { FormErrorSummary } from "../components/FormErrorSummary";
import { useSoknad } from "../hooks/useSoknad";
import { formatDate } from "../components/summaryFormatters";
import { EKSPERTBISTAND_REFUSJON_PATH } from "../utils/constants";
import { resolveApiError, type ApiErrorInfo } from "../utils/http";
import { useErrorFocus } from "../hooks/useErrorFocus";
import { isProd } from "../utils/env";

const MAX_FILES = 5;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
const MAX_UTGIFTER_CHARS = 2000;

// TODO: Vi må sjekke refusjonsbeløpet mot maksbeløpet i tilsagnsbrevet.
// Maksbeløpet er ikke eksponert fra backend/tilsagnsbrev ennå – når det er på
// plass, hent det via useSoknad/tilsagn og send det inn til makeRefusjonSchema
// nedenfor. Da vil feltet vise feil og skjemaet blokkeres ved beløp over maks.
const makeRefusjonSchema = (maksBelopKroner?: number) =>
  z.object({
    utgifter: z
      .string()
      .min(1, "Du må beskrive hvilke utgifter tilskuddet skal dekke.")
      .max(MAX_UTGIFTER_CHARS, `Beskrivelsen kan ikke være lengre enn ${MAX_UTGIFTER_CHARS} tegn.`),
    belop: z
      .string()
      .min(1, "Du må oppgi beløp for refusjonskravet.")
      .regex(/^\d+$/, "Beløpet må være et helt antall kroner, f.eks. 12500.")
      .superRefine((value, ctx) => {
        if (maksBelopKroner === undefined || !/^\d+$/.test(value)) return;
        if (Number(value) > maksBelopKroner) {
          ctx.addIssue({
            code: "custom",
            message: `Beløpet kan ikke være høyere enn maksbeløpet i tilsagnsbrevet (${maksBelopKroner} kroner).`,
          });
        }
      }),
    bekreftUtgifter: z
      .boolean()
      .refine(Boolean, { message: "Du må bekrefte at utgiftene er betalt." }),
  });

type RefusjonInputs = z.infer<ReturnType<typeof makeRefusjonSchema>>;

const FIELDS = ["utgifter", "belop", "bekreftUtgifter"] as const satisfies ReadonlyArray<
  keyof RefusjonInputs
>;

export default function SokRefusjonPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { soknad, isLoading, error: fetchError } = useSoknad(id);

  const [acceptedFiles, setAcceptedFiles] = useState<File[]>([]);
  const [rejectedFiles, setRejectedFiles] = useState<FileRejected[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { focusKey, bumpFocusKey } = useErrorFocus();

  // TODO: Hent maksbeløpet fra tilsagnsbrevet når det er tilgjengelig fra backend.
  // Så lenge dette er undefined gjøres ingen maks-sjekk.
  const maksBelopKroner: number | undefined = undefined;
  const refusjonSchema = useMemo(() => makeRefusjonSchema(maksBelopKroner), [maksBelopKroner]);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RefusjonInputs>({
    resolver: zodResolver(refusjonSchema),
    reValidateMode: "onBlur",
    shouldFocusError: false,
    defaultValues: { utgifter: "", belop: "", bekreftUtgifter: false },
  });

  const onSelect = (files: FileObject[]) => {
    const accepted = files.filter((f): f is FileAccepted => !f.error).map((f) => f.file);
    const rejected = files.filter((f): f is FileRejected => f.error);
    setAcceptedFiles((prev) => [...prev, ...accepted].slice(0, MAX_FILES));
    setRejectedFiles(rejected);
    setFileError(null);
  };

  const removeFile = (file: File) => {
    setAcceptedFiles((prev) => prev.filter((f) => f !== file));
  };

  const onValid = async (data: RefusjonInputs) => {
    if (acceptedFiles.length === 0) {
      setFileError("Du må laste opp minst én fil.");
      bumpFocusKey();
      return;
    }
    if (!id) return;

    setSubmitError(null);
    setIsSubmitting(true);
    try {
      const formData = new FormData();
      formData.append("utgifter", data.utgifter);
      formData.append("belop", data.belop);
      acceptedFiles.forEach((file) => formData.append("filer", file));

      const response = await fetch(EKSPERTBISTAND_REFUSJON_PATH(id), {
        method: "POST",
        body: formData,
      });
      if (!response.ok) {
        throw new Error(`Feil ved innsending (${response.status})`);
      }
      navigate(`/skjema/${id}/kvittering`);
    } catch (err) {
      setSubmitError(resolveApiError(err, "Kunne ikke sende inn refusjonskravet akkurat nå."));
    } finally {
      setIsSubmitting(false);
    }
  };

  const onInvalid = () => {
    if (acceptedFiles.length === 0) setFileError("Du må laste opp minst én fil.");
    bumpFocusKey();
  };

  if (isProd()) {
    return <Navigate to={id ? `/skjema/${id}/kvittering` : "/soknader"} replace />;
  }

  if (isLoading) {
    return (
      <DecoratedPage>
        <VStack align="center" gap="space-4" padding="space-32">
          <Loader size="large" title="Laster søknad" />
          <BodyShort>Laster søknad …</BodyShort>
        </VStack>
      </DecoratedPage>
    );
  }

  if (fetchError || !soknad) {
    return (
      <DecoratedPage>
        <Alert variant="error">Kunne ikke hente søknadsdata. Prøv å laste siden på nytt.</Alert>
      </DecoratedPage>
    );
  }

  const atFileLimit = acceptedFiles.length >= MAX_FILES;

  return (
    <DecoratedPage>
      <form onSubmit={handleSubmit(onValid, onInvalid)} autoComplete="off" noValidate>
        <VStack gap="space-32" data-aksel-template="form-summarypage-v5">
          <BackLink to={`/skjema/${id}/kvittering`}>Tilbake til avtalen</BackLink>

          <Heading level="1" size="xlarge">
            Søk refusjon
          </Heading>

          <FormSummary>
            <FormSummary.Header>
              <FormSummary.Heading level="2">Opplysninger om saken</FormSummary.Heading>
            </FormSummary.Header>
            <FormSummary.Answers>
              <FormSummary.Answer>
                <FormSummary.Label>Arbeidsgiver</FormSummary.Label>
                <FormSummary.Value>{soknad.virksomhet?.virksomhetsnavn ?? "–"}</FormSummary.Value>
              </FormSummary.Answer>
              <FormSummary.Answer>
                <FormSummary.Label>Ansatt</FormSummary.Label>
                <FormSummary.Value>{soknad.ansatt?.navn ?? "–"}</FormSummary.Value>
              </FormSummary.Answer>
              <FormSummary.Answer>
                <FormSummary.Label>Ekspert</FormSummary.Label>
                <FormSummary.Value>{soknad.ekspert?.navn ?? "–"}</FormSummary.Value>
              </FormSummary.Answer>
              <FormSummary.Answer>
                <FormSummary.Label>Startdato</FormSummary.Label>
                <FormSummary.Value>
                  {soknad.behovForBistand?.startdato
                    ? formatDate(soknad.behovForBistand.startdato)
                    : "–"}
                </FormSummary.Value>
              </FormSummary.Answer>
            </FormSummary.Answers>
          </FormSummary>

          <FormErrorSummary
            errors={errors}
            fields={FIELDS}
            heading="Du må rette disse feilene før du kan sende inn:"
            focusKey={focusKey}
            extraItems={[
              ...(fileError ? [{ id: "filer", message: fileError, href: "#filer" }] : []),
              ...(submitError
                ? [{ id: "submit-error", message: submitError.message, href: "#submit-error" }]
                : []),
            ]}
          />

          <Textarea
            id="utgifter"
            label="Hvilke utgifter skal tilskuddet til ekspertbistand dekke?"
            maxLength={MAX_UTGIFTER_CHARS}
            error={errors.utgifter?.message}
            {...register("utgifter")}
          />

          <TextField
            id="belop"
            label="Beløp for refusjonskravet"
            description="Oppgi beløp i hele kroner, f.eks. 12500"
            inputMode="numeric"
            error={errors.belop?.message}
            style={{ maxWidth: "16rem" }}
            {...register("belop")}
          />

          <VStack gap="space-16" id="filer">
            <FileUpload>
              <FileUpload.Dropzone
                label="Last opp kvittering eller dokumentasjon på faktiske utgifter"
                description="Kun PDF-filer. Maks 10 MB per fil."
                accept=".pdf,application/pdf"
                maxSizeInBytes={MAX_FILE_SIZE_BYTES}
                multiple
                fileLimit={{ max: MAX_FILES, current: acceptedFiles.length }}
                onSelect={onSelect}
                error={
                  fileError ??
                  (rejectedFiles.length > 0
                    ? rejectedFiles.map((f) => `${f.file.name}: ${f.reasons.join(", ")}`).join("\n")
                    : undefined)
                }
              />
            </FileUpload>

            {acceptedFiles.length > 0 && (
              <VStack gap="space-8" as="ul">
                {acceptedFiles.map((file) => (
                  <FileUpload.Item
                    key={`${file.name}-${file.size}`}
                    as="li"
                    file={file}
                    button={{ action: "delete", onClick: () => removeFile(file) }}
                  />
                ))}
              </VStack>
            )}

            {atFileLimit && (
              <Alert variant="info" inline>
                Maks {MAX_FILES} filer er nådd.
              </Alert>
            )}
          </VStack>

          <VStack gap="space-2">
            <Checkbox
              id="bekreftUtgifter"
              error={!!errors.bekreftUtgifter}
              errorId="bekreftUtgifter-error"
              {...register("bekreftUtgifter")}
            >
              Jeg bekrefter at utgiftene som kreves refundert er betalt.
            </Checkbox>
            {errors.bekreftUtgifter?.message && (
              <ErrorMessage id="bekreftUtgifter-error">
                {errors.bekreftUtgifter.message}
              </ErrorMessage>
            )}
          </VStack>

          {submitError && (
            <Alert variant="error" role="alert" id="submit-error">
              {submitError.message}
            </Alert>
          )}

          <Button
            type="submit"
            variant="primary"
            icon={<PaperplaneIcon aria-hidden />}
            iconPosition="right"
            loading={isSubmitting}
          >
            Send inn
          </Button>
        </VStack>
      </form>
    </DecoratedPage>
  );
}
