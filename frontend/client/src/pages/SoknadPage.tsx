import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowRightIcon } from "@navikt/aksel-icons";
import { type SubmitErrorHandler, type SubmitHandler, useForm } from "react-hook-form";
import {
  Accordion,
  Bleed,
  BodyLong,
  Box,
  Button,
  Checkbox,
  GuidePanel,
  Heading,
  ErrorSummary,
  Link,
  List,
  Alert,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { FocusedErrorSummary } from "../components/FocusedErrorSummary";
import { ApplicationPictogram } from "../components/ApplicationPictogram";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { useErrorFocus } from "../hooks/useErrorFocus";

export default function SoknadPage() {
  const navigate = useNavigate();
  const [apiError, setApiError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const { focusKey: errorFocusKey, bumpFocusKey } = useErrorFocus();

  type IntroInputs = {
    bekreftRiktige: boolean;
    bekreftSamraad: boolean;
  };

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<IntroInputs>({
    reValidateMode: "onBlur",
    shouldFocusError: false,
    defaultValues: { bekreftRiktige: false, bekreftSamraad: false },
  });

  const onValid: SubmitHandler<IntroInputs> = async () => {
    setApiError(null);
    setCreating(true);
    try {
      const res = await fetch(EKSPERTBISTAND_API_PATH, {
        method: "POST",
        headers: { Accept: "application/json" },
      });
      if (!res.ok) {
        throw new Error(`Opprettelse av utkast feilet (${res.status})`);
      }
      const payload = (await res.json()) as { id?: string } | null;
      const id = payload?.id;
      if (!id) {
        throw new Error("Svar manglet id");
      }
      navigate(`/skjema/${id}/steg-1`);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Kunne ikke starte søknaden.";
      setApiError(message);
      bumpFocusKey();
    } finally {
      setCreating(false);
    }
  };
  const onInvalid: SubmitErrorHandler<IntroInputs> = () => {
    bumpFocusKey();
  };

  return (
    <DecoratedPage blockProps={{ width: "lg", gutters: true }}>
      <form onSubmit={handleSubmit(onValid, onInvalid)}>
        <VStack as="main" gap="8" data-aksel-template="form-intropage-v3">
          <VStack gap="3">
            <Bleed asChild marginInline={{ lg: "32" }}>
              <Box
                width={{ xs: "64px", lg: "96px" }}
                height={{ xs: "64px", lg: "96px" }}
                asChild
                position={{ xs: "relative", lg: "absolute" }}
              >
                <ApplicationPictogram />
              </Box>
            </Bleed>
            <VStack gap="1">
              <Heading level="1" size="xlarge">
                Søknad om tilskudd til ekspertbistand
              </Heading>
            </VStack>
          </VStack>

          <GuidePanel poster>
            <Heading level="2" size="medium" spacing>
              Hei!
            </Heading>
            <BodyLong spacing>
              Ekspertbistand dekker hjelp fra en nøytral ekspert som har kompetanse på sykefravær og
              arbeidsmiljø. Eksperten prøver å avdekke mulige årsaker til sykefraværet, og foreslår
              tiltak som gjør at den ansatte kanskje unngår å bli syk igjen. Eksperten skal ikke
              selv behandle.
            </BodyLong>
            <BodyLong>
              Les mer om tilskudd til{" "}
              <Link href="https://www.nav.no/ekspertbistand">ekspertbistand</Link>.
            </BodyLong>
          </GuidePanel>
          <div>
            <Heading level="2" size="large" spacing>
              Før du søker
            </Heading>
            <List>
              <List.Item>
                Du må ha snakket med Nav om denne konkrete saken knyttet til ekspertbistand. Husk å
                notere hvem du har drøftet det med.
              </List.Item>
              <List.Item>
                Du, den ansatte og Nav er enige om at det er hensiktsmessig med ekspertbistand.
              </List.Item>
              <List.Item>
                Du vet hvilken ekspert du ønsker å bruke og hvilken hjelp denne kan tilby.
              </List.Item>
              <List.Item>
                Du har blitt tildelt Altinn-tilgangen “Ekspertbistand” på riktig virksomhet.
              </List.Item>
            </List>
          </div>
          <div>
            <Accordion>
              <Accordion.Item>
                <Accordion.Header>Hvordan vi behandler personopplysninger</Accordion.Header>
                <Accordion.Content>
                  <BodyLong>
                    Søknader og utkast om tilskudd til ekspertbistand er synlig for alle som har
                    Altinn-tilgangen “Ekspertbistand” i virksomheten. Av hensyn til personvernet
                    fjerner vi deres tilgang til søknaden 8 måneder etter at tiltaket er avsluttet.
                  </BodyLong>
                </Accordion.Content>
              </Accordion.Item>
              <Accordion.Item>
                <Accordion.Header>Vi lagrer svar underveis</Accordion.Header>
                <Accordion.Content>
                  <BodyLong spacing>
                    Svarene dine lagres automatisk mens du fyller ut søknaden. Det betyr at du kan
                    ta pauser og fortsette senere.
                  </BodyLong>
                  <BodyLong spacing>
                    Utkastet er synlig for alle som har Altinn-tilgangen “Ekspertbistand” i
                    virksomheten.
                  </BodyLong>
                  <BodyLong>
                    Hvis du ikke fortsetter innen 48 timer, blir utkastet slettet.
                  </BodyLong>
                </Accordion.Content>
              </Accordion.Item>
            </Accordion>
          </div>
          <div>
            <BodyLong>
              Det er viktig at du gir oss riktige opplysninger slik at vi kan behandle saken din.{" "}
              {""}
              <Link href="https://www.nav.no/endringer">
                Les mer om viktigheten av å gi riktige opplysninger.
              </Link>
            </BodyLong>
            <Box paddingBlock="4 8">
              <Checkbox
                id="bekreftRiktige"
                error={!!errors.bekreftRiktige}
                {...register("bekreftRiktige", {
                  required: "Du må bekrefte at du vil svare så riktig som mulig.",
                })}
              >
                Jeg vil svare så godt jeg kan på spørsmålene i søknaden.
              </Checkbox>
              <Checkbox
                id="bekreftSamraad"
                error={!!errors.bekreftSamraad}
                {...register("bekreftSamraad", {
                  required: "Du må bekrefte at søknaden fylles ut i samråd med den ansatte.",
                })}
              >
                Jeg bekrefter at søknaden fylles ut i samråd med den ansatte. Arbeidsgiver og ansatt
                er enige om at ekspertbistand er hensiktsmessig.
              </Checkbox>
            </Box>
            <VStack gap="6">
              {(Object.values(errors).length > 0 || apiError) && (
                <FocusedErrorSummary
                  isActive={Object.values(errors).length > 0 || Boolean(apiError)}
                  focusKey={errorFocusKey}
                  heading="Du må rette disse feilene før du kan fortsette:"
                >
                  {Object.entries(errors).map(([key, error]) => (
                    <ErrorSummary.Item key={key} href={`#${key}`}>
                      {error?.message as string}
                    </ErrorSummary.Item>
                  ))}
                  {apiError ? (
                    <ErrorSummary.Item href="#start-soknad-feil">{apiError}</ErrorSummary.Item>
                  ) : null}
                </FocusedErrorSummary>
              )}
              {apiError ? (
                <Alert variant="error" inline>
                  {apiError}
                </Alert>
              ) : null}
              <Button
                type="submit"
                variant="primary"
                id="start-soknad-feil"
                icon={<ArrowRightIcon aria-hidden />}
                iconPosition="right"
                loading={creating}
              >
                Start søknad
              </Button>
            </VStack>
          </div>
        </VStack>
      </form>
    </DecoratedPage>
  );
}
