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
  Heading,
  InfoCard,
  LinkCard,
  Loader,
  TextField,
  VStack,
  type FileAccepted,
  type FileObject,
  type FileRejected,
} from "@navikt/ds-react";
import { PaperplaneIcon, XMarkOctagonIcon } from "@navikt/aksel-icons";
import DecoratedPage from "../components/DecoratedPage";
import { BackLink } from "../components/BackLink";
import { FormErrorSummary } from "../components/FormErrorSummary";
import { Nokkeldata } from "../components/Nokkeldata";
import { useSoknad } from "../hooks/useSoknad";
import { useRefusjonStatus } from "../hooks/useRefusjonStatus";
import { useMaksBelop } from "../hooks/useMaksBelop";
import { useKontonummerFinnes } from "../hooks/useKontonummerFinnes";
import { useVedtak } from "../hooks/useVedtak";
import { formatCurrency } from "../components/summaryFormatters";
import { EKSPERTBISTAND_REFUSJON_PATH, KONTONUMMER_REGISTRERING_URL } from "../utils/constants";
import { HttpError, resolveApiError, type ApiErrorInfo } from "../../utils/http";
import { useErrorFocus } from "../hooks/useErrorFocus";
import { lesInnsendingsfeil } from "../utils/innsendingsfeil";

const MAX_FILES = 5;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

const makeRefusjonSchema = (maksBelopKroner?: number) =>
  z.object({
    belop: z
      .string()
      .min(1, "Du må oppgi beløp for refusjonskravet.")
      .regex(/^\d+$/, "Beløpet må være et helt antall kroner, f.eks. 12500.")
      .superRefine((value, ctx) => {
        if (maksBelopKroner === undefined || !/^\d+$/.test(value)) return;
        if (Number(value) > maksBelopKroner) {
          ctx.addIssue({
            code: "custom",
            message: `Beløpet kan ikke være høyere enn innvilget beløp (${formatCurrency(maksBelopKroner)}).`,
          });
        }
      }),
    bekreftUtgifter: z
      .boolean()
      .refine(Boolean, { message: "Du må bekrefte at utgiftene er betalt." }),
  });

type RefusjonInputs = z.infer<ReturnType<typeof makeRefusjonSchema>>;

const FIELDS = ["belop", "bekreftUtgifter"] as const satisfies ReadonlyArray<keyof RefusjonInputs>;

export default function SokRefusjonPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { soknad, isLoading, error: fetchError } = useSoknad(id);
  const { vedtak, isLoading: vedtakLoading, error: vedtakError } = useVedtak(id);
  const {
    refusjon,
    isLoading: isLoadingStatus,
    error: statusError,
    mutate: mutateStatus,
  } = useRefusjonStatus(id);
  const {
    maksBelopKroner,
    isLoading: isLoadingMaksBelop,
    error: maksBelopError,
  } = useMaksBelop(id);
  const {
    finnes: kontonummerFinnes,
    isLoading: kontonummerLoading,
    error: kontonummerError,
  } = useKontonummerFinnes(soknad?.virksomhet?.virksomhetsnummer);

  const [acceptedFiles, setAcceptedFiles] = useState<File[]>([]);
  const [rejectedFiles, setRejectedFiles] = useState<FileRejected[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { focusKey, bumpFocusKey } = useErrorFocus();

  const refusjonSchema = useMemo(
    () => makeRefusjonSchema(maksBelopKroner ?? undefined),
    [maksBelopKroner]
  );

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RefusjonInputs>({
    resolver: zodResolver(refusjonSchema),
    reValidateMode: "onBlur",
    shouldFocusError: false,
    defaultValues: { belop: "", bekreftUtgifter: false },
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
      formData.append("belop", data.belop);
      acceptedFiles.forEach((file) => formData.append("filer", file));

      const response = await fetch(EKSPERTBISTAND_REFUSJON_PATH(id), {
        method: "POST",
        body: formData,
      });
      if (!response.ok) {
        throw new Error(await lesInnsendingsfeil(response));
      }
    } catch (err) {
      setSubmitError(resolveApiError(err, "Kunne ikke sende inn refusjonskravet akkurat nå."));
      setIsSubmitting(false);
      return;
    }

    await mutateStatus();
    navigate(`/v2/skjema/${id}/vedtak`, { state: { refusjonSendtInn: true } });
  };

  const onInvalid = () => {
    if (acceptedFiles.length === 0) setFileError("Du må laste opp minst én fil.");
    bumpFocusKey();
  };

  if (id && refusjon && !statusError && !isSubmitting) {
    return <Navigate to={`/v2/skjema/${id}/vedtak`} replace />;
  }

  if (isLoading || isLoadingStatus || isLoadingMaksBelop || kontonummerLoading) {
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

  if (statusError) {
    return (
      <DecoratedPage>
        <Alert variant="error" role="alert">
          Kunne ikke hente status for refusjonskravet. Prøv å laste siden på nytt.
        </Alert>
      </DecoratedPage>
    );
  }

  const atFileLimit = acceptedFiles.length >= MAX_FILES;

  if (maksBelopError) {
    const isConflict = maksBelopError instanceof HttpError && maksBelopError.status === 409;
    return (
      <DecoratedPage>
        <Alert variant={isConflict ? "warning" : "error"} role="alert">
          {isConflict
            ? maksBelopError.message
            : "Kunne ikke hente maksbeløpet for refusjonskravet. Prøv å laste siden på nytt."}
        </Alert>
      </DecoratedPage>
    );
  }

  const tiltaksperiode = vedtak?.tiltaksperiode;
  const periodeTekst =
    vedtakError || vedtak === null
      ? "Kunne ikke hentes"
      : vedtakLoading
        ? "Laster tiltaksperiode …"
        : tiltaksperiode
          ? `${tiltaksperiode.fra} – ${tiltaksperiode.til}`
          : "–";

  return (
    <DecoratedPage>
      <VStack gap="space-32" data-aksel-template="form-summarypage-v5">
        <BackLink to={`/v2/skjema/${id}/vedtak`}>Tilbake til avtalen</BackLink>

        <Heading level="1" size="xlarge">
          Søk refusjon
        </Heading>

        <VStack gap="space-8" aria-live="polite">
          <Nokkeldata
            kompakt
            rader={[
              ["Arbeidsgiver", soknad.virksomhet?.virksomhetsnavn?.trim() || "–"],
              ["Ansatt", soknad.ansatt?.navn?.trim() || "–"],
              ["Ekspert", soknad.ekspert?.navn?.trim() || "–"],
              ["Tiltaksperiode", periodeTekst],
            ]}
          />
          {(vedtakError || vedtak === null) && (
            <Alert variant="error" role="alert">
              {vedtakError
                ? "Kunne ikke hente tiltaksperioden. Prøv å laste siden på nytt."
                : "Fant ikke vedtaket for denne søknaden. Prøv å laste siden på nytt."}
            </Alert>
          )}
        </VStack>

        {kontonummerError ? (
          <Alert variant="error">
            Kunne ikke sjekke om virksomheten har registrert kontonummer. Prøv å laste siden på
            nytt.
          </Alert>
        ) : !kontonummerFinnes ? (
          <VStack gap="space-16">
            <InfoCard data-color="danger">
              <InfoCard.Header icon={<XMarkOctagonIcon aria-hidden />}>
                <InfoCard.Title>Kontonummer mangler</InfoCard.Title>
              </InfoCard.Header>
              <InfoCard.Content>
                For at du skal kunne søke refusjon må Nav ha et kontonummer registret for
                utbetaling.
              </InfoCard.Content>
            </InfoCard>
            <LinkCard>
              <LinkCard.Title>
                <LinkCard.Anchor
                  href={KONTONUMMER_REGISTRERING_URL}
                  target="_blank"
                  rel="noreferrer"
                >
                  Registrer kontonummer ho Nav
                </LinkCard.Anchor>
              </LinkCard.Title>
              <LinkCard.Description>
                Les mer om hvordan du som arbeidsgiver går frem og hvilke Altinn tilganger som
                kreves.
              </LinkCard.Description>
            </LinkCard>
          </VStack>
        ) : (
          <form onSubmit={handleSubmit(onValid, onInvalid)} autoComplete="off" noValidate>
            <VStack gap="space-32">
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

              <TextField
                id="belop"
                label="Beløp for refusjonskravet"
                description={`Du kan kreve inntil ${formatCurrency(maksBelopKroner)}. Oppgi beløp i hele kroner, f.eks. 12500.`}
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
                        ? rejectedFiles
                            .map((f) => `${f.file.name}: ${f.reasons.join(", ")}`)
                            .join("\n")
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
        )}
      </VStack>
    </DecoratedPage>
  );
}
