import { useNavigate } from "react-router-dom";
import { ArrowLeftIcon, FloppydiskIcon, PaperplaneIcon, TrashIcon } from "@navikt/aksel-icons";
import {
  BodyLong,
  BodyShort,
  Box,
  Button,
  FormProgress,
  FormSummary,
  Heading,
  HGrid,
  Link,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";

export default function OppsummeringPage() {
  const navigate = useNavigate();

  return (
    <DecoratedPage
      blockProps={{ as: "main", width: "text", gutters: true }}
      languages={[
        { locale: "nb", url: "https://www.nav.no" },
        { locale: "en", url: "https://www.nav.no/en" },
      ]}
    >
      <VStack gap="8" data-aksel-template="form-summarypage-v3">
        <VStack gap="3">
          <Heading level="1" size="xlarge">
            Oppsummering av søknad om ekspertbistand
          </Heading>
        </VStack>

        <div>
          <Link
            href="#"
            onClick={(e) => {
              e.preventDefault();
              navigate("/skjema");
            }}
          >
            <ArrowLeftIcon aria-hidden /> Forrige steg
          </Link>
          <Box paddingBlock="6 5">
            <Heading level="2" size="large">
              Oppsummering
            </Heading>
          </Box>

          <FormProgress activeStep={2} totalSteps={2}>
            <FormProgress.Step href="#" onClick={(e) => e.preventDefault()}>
              Skjema
            </FormProgress.Step>
            <FormProgress.Step href="#" onClick={(e) => e.preventDefault()}>
              Oppsummering
            </FormProgress.Step>
          </FormProgress>
        </div>

        <BodyLong>
          Nå kan du se over at alt er riktig før du sender inn søknaden. Ved behov kan du endre
          opplysningene.
        </BodyLong>

        <FormSummary>
          <FormSummary.Header>
            <FormSummary.Heading level="2">Kontaktperson i virksomheten</FormSummary.Heading>
          </FormSummary.Header>
          <FormSummary.Answers>
            <FormSummary.Answer>
              <FormSummary.Label>Navn</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>E-post</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Telefonnummer</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
          </FormSummary.Answers>
          <FormSummary.Footer>
            <FormSummary.EditLink
              href="#"
              onClick={(e) => {
                e.preventDefault();
                navigate("/skjema");
              }}
            />
          </FormSummary.Footer>
        </FormSummary>

        <FormSummary>
          <FormSummary.Header>
            <FormSummary.Heading level="2">Ansatt</FormSummary.Heading>
          </FormSummary.Header>
          <FormSummary.Answers>
            <FormSummary.Answer>
              <FormSummary.Label>Fødselsnummer</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Navn</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
          </FormSummary.Answers>
          <FormSummary.Footer>
            <FormSummary.EditLink
              href="#"
              onClick={(e) => {
                e.preventDefault();
                navigate("/skjema");
              }}
            />
          </FormSummary.Footer>
        </FormSummary>

        <FormSummary>
          <FormSummary.Header>
            <FormSummary.Heading level="2">Ekspert</FormSummary.Heading>
          </FormSummary.Header>
          <FormSummary.Answers>
            <FormSummary.Answer>
              <FormSummary.Label>Navn</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Tilknyttet virksomhet</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Kompetanse</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Problemstilling</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Tiltak for tilrettelegging</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Anslått kostnad</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Startdato</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
            <FormSummary.Answer>
              <FormSummary.Label>Kontakt i Nav</FormSummary.Label>
              <FormSummary.Value>—</FormSummary.Value>
            </FormSummary.Answer>
          </FormSummary.Answers>
          <FormSummary.Footer>
            <FormSummary.EditLink
              href="#"
              onClick={(e) => {
                e.preventDefault();
                navigate("/skjema");
              }}
            />
          </FormSummary.Footer>
        </FormSummary>

        <VStack gap="4">
          <BodyShort as="div" size="small" textColor="subtle">
            Sist lagret: —
          </BodyShort>
          <HGrid
            gap={{ xs: "4", sm: "8 4" }}
            columns={{ xs: 1, sm: 2 }}
            width={{ sm: "fit-content" }}
          >
            <Button
              variant="secondary"
              icon={<ArrowLeftIcon aria-hidden />}
              iconPosition="left"
              onClick={() => navigate("/skjema")}
            >
              Forrige steg
            </Button>
            <Button
              variant="primary"
              icon={<PaperplaneIcon aria-hidden />}
              iconPosition="right"
              type="button"
            >
              Send søknad
            </Button>

            <Box asChild marginBlock={{ xs: "4 0", sm: "0" }}>
              <Button
                variant="tertiary"
                icon={<FloppydiskIcon aria-hidden />}
                iconPosition="left"
                type="button"
              >
                Fortsett senere
              </Button>
            </Box>
            <Button
              variant="tertiary"
              icon={<TrashIcon aria-hidden />}
              iconPosition="left"
              type="button"
            >
              Slett søknaden
            </Button>
          </HGrid>
        </VStack>
      </VStack>
    </DecoratedPage>
  );
}
