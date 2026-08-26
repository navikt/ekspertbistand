import { useState } from "react";
import { useNavigate, useParams } from "react-router";
import {
  Alert,
  BodyShort,
  Button,
  ExpansionCard,
  FileUpload,
  FormSummary,
  Heading,
  Label,
  Loader,
  VStack,
  type FileAccepted,
  type FileObject,
  type FileRejected,
} from "@navikt/ds-react";
import { FileTextIcon, PaperplaneIcon } from "@navikt/aksel-icons";
import DecoratedPage from "../components/DecoratedPage";
import { BackLink } from "../components/BackLink";
import { useSoknad } from "../hooks/useSoknad";
import { useSluttrapportStatus } from "../hooks/useSluttrapportStatus";
import { formatDate } from "../components/summaryFormatters";
import { EKSPERTBISTAND_SLUTTRAPPORT_PATH } from "../utils/constants";
import { resolveApiError, type ApiErrorInfo } from "../utils/http";

const MAX_FILES = 1;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

export default function SluttrapportPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { soknad, isLoading, error: fetchError } = useSoknad(id);
  const {
    sluttrapport,
    isLoading: isLoadingStatus,
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
        throw new Error(`Feil ved innsending (${response.status})`);
      }
      await mutateStatus();
      navigate(`/skjema/${id}/kvittering`);
    } catch (err) {
      setSubmitError(resolveApiError(err, "Kunne ikke sende inn sluttrapporten akkurat nå."));
    } finally {
      setIsSubmitting(false);
    }
  };

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

  const hasFiles = acceptedFiles.length > 0;
  const atFileLimit = acceptedFiles.length >= MAX_FILES;

  return (
    <DecoratedPage>
      <VStack gap="space-32" data-aksel-template="form-summarypage-v5">
        <BackLink to={`/skjema/${id}/kvittering`}>Tilbake til avtalen</BackLink>

        <Heading level="1" size="xlarge">
          Send inn sluttrapport
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

        {sluttrapport ? (
          <ExpansionCard aria-label="Sluttrapport sendt inn" defaultOpen>
            <ExpansionCard.Header>
              <VStack gap="space-8">
                <FileTextIcon aria-hidden fontSize="1.5rem" />
                <ExpansionCard.Title size="small">Sluttrapport sendt inn</ExpansionCard.Title>
                <ExpansionCard.Description>
                  {formatDate(sluttrapport.lastetOpp)}
                </ExpansionCard.Description>
              </VStack>
            </ExpansionCard.Header>
            <ExpansionCard.Content>
              <VStack gap="space-16">
                <VStack gap="space-2">
                  <Label>Vedlegg</Label>
                  <BodyShort>{sluttrapport.filnavn}</BodyShort>
                </VStack>
                <VStack gap="space-2">
                  <Label>Sendt inn til Nav</Label>
                  <BodyShort>{formatDate(sluttrapport.lastetOpp)}</BodyShort>
                </VStack>
              </VStack>
            </ExpansionCard.Content>
          </ExpansionCard>
        ) : (
          <>
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
                      ? rejectedFiles
                          .map((f) => `${f.file.name}: ${f.reasons.join(", ")}`)
                          .join("\n")
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
          </>
        )}
      </VStack>
    </DecoratedPage>
  );
}
