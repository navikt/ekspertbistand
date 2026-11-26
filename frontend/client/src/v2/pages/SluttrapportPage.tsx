import { useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router";
import {
  Alert,
  BodyLong,
  BodyShort,
  Button,
  FileUpload,
  Heading,
  Loader,
  VStack,
  type FileAccepted,
  type FileObject,
  type FileRejected,
} from "@navikt/ds-react";
import { PaperplaneIcon } from "@navikt/aksel-icons";
import DecoratedPage from "../components/DecoratedPage";
import { BackLink } from "../components/BackLink";
import { Nokkeldata } from "../components/Nokkeldata";
import { useSoknad } from "../hooks/useSoknad";
import { useSluttrapportStatus } from "../hooks/useSluttrapportStatus";
import { useVedtak } from "../hooks/useVedtak";
import { EKSPERTBISTAND_SLUTTRAPPORT_PATH } from "../utils/constants";
import { lesInnsendingsfeil } from "../utils/innsendingsfeil";
import { resolveApiError, type ApiErrorInfo } from "../../utils/http";

const MAX_FILES = 1;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

export default function SluttrapportPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { soknad, isLoading, error: fetchError } = useSoknad(id);
  const { vedtak, isLoading: vedtakLoading, error: vedtakError } = useVedtak(id);
  const {
    sluttrapport,
    isLoading: isLoadingStatus,
    error: statusError,
    mutate: mutateStatus,
  } = useSluttrapportStatus(id);

  const [acceptedFiles, setAcceptedFiles] = useState<File[]>([]);
  const [rejectedFiles, setRejectedFiles] = useState<FileRejected[]>([]);
  const [submitError, setSubmitError] = useState<ApiErrorInfo | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const onSelect = (files: FileObject[]) => {
    const accepted = files.filter((f): f is FileAccepted => !f.error).map((f) => f.file);
    const rejected = files.filter((f): f is FileRejected => f.error);
    setAcceptedFiles((prev) => [...prev, ...accepted].slice(0, MAX_FILES));
    setRejectedFiles(rejected);
  };

  const removeFile = (file: File) => {
    setAcceptedFiles((prev) => prev.filter((f) => f !== file));
  };

  const handleSubmit = async () => {
    if (!id || acceptedFiles.length === 0) return;
    setSubmitError(null);
    setIsSubmitting(true);
    try {
      const formData = new FormData();
      acceptedFiles.forEach((file) => formData.append("filer", file));
      const response = await fetch(EKSPERTBISTAND_SLUTTRAPPORT_PATH(id), {
        method: "POST",
        body: formData,
      });
      if (!response.ok) {
        throw new Error(await lesInnsendingsfeil(response));
      }
    } catch (err) {
      setSubmitError(resolveApiError(err, "Kunne ikke sende inn sluttrapporten akkurat nå."));
      setIsSubmitting(false);
      return;
    }

    await mutateStatus();
    navigate(`/v2/skjema/${id}/vedtak`, { state: { sluttrapportSendtInn: true } });
  };

  const nySluttrapportKreves = sluttrapport?.status === "NY_INNSENDING_KREVES";

  if (id && sluttrapport && !nySluttrapportKreves && !statusError && !isSubmitting) {
    return <Navigate to={`/v2/skjema/${id}/vedtak`} replace />;
  }

  if (isLoading || isLoadingStatus) {
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
          Kunne ikke hente status for sluttrapporten. Prøv å laste siden på nytt.
        </Alert>
      </DecoratedPage>
    );
  }

  const hasFiles = acceptedFiles.length > 0;
  const atFileLimit = acceptedFiles.length >= MAX_FILES;
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
          {nySluttrapportKreves ? "Send inn ny sluttrapport" : "Send inn sluttrapport"}
        </Heading>

        {nySluttrapportKreves && (
          <Alert variant="warning">
            <Heading level="2" size="xsmall" spacing>
              Nav ber om ny sluttrapport
            </Heading>
            <BodyLong>{sluttrapport.begrunnelse}</BodyLong>
          </Alert>
        )}

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

        <VStack gap="space-16">
          <FileUpload>
            <FileUpload.Dropzone
              label="Last opp sluttrapport fra eksperten"
              description="Kun PDF-filer. Maks 10 MB per fil."
              accept=".pdf,application/pdf"
              maxSizeInBytes={MAX_FILE_SIZE_BYTES}
              fileLimit={{ max: MAX_FILES, current: acceptedFiles.length }}
              onSelect={onSelect}
              error={
                rejectedFiles.length > 0
                  ? rejectedFiles.map((f) => `${f.file.name}: ${f.reasons.join(", ")}`).join("\n")
                  : undefined
              }
            />
          </FileUpload>

          {hasFiles && (
            <VStack gap="space-8" as="ul">
              {acceptedFiles.map((file) => (
                <FileUpload.Item
                  key={`${file.name}-${file.size}`}
                  as="li"
                  file={file}
                  button={{
                    action: "delete",
                    onClick: () => removeFile(file),
                  }}
                />
              ))}
            </VStack>
          )}

          {atFileLimit && (
            <Alert variant="info" inline>
              Du kan kun laste opp ett vedlegg.
            </Alert>
          )}
        </VStack>

        {submitError && (
          <Alert variant="error" role="alert">
            {submitError.message}
          </Alert>
        )}

        <Button
          type="button"
          variant="primary"
          icon={<PaperplaneIcon aria-hidden />}
          iconPosition="right"
          loading={isSubmitting}
          disabled={!hasFiles}
          onClick={handleSubmit}
        >
          Send inn
        </Button>
      </VStack>
    </DecoratedPage>
  );
}
