import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowRightIcon } from "@navikt/aksel-icons";
import { type Path, type SubmitHandler, useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Accordion,
  Bleed,
  BodyLong,
  Box,
  Button,
  Checkbox,
  GuidePanel,
  Heading,
  Link,
  List,
  Alert,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";
import { FormErrorSummary } from "../components/FormErrorSummary";
import { ApplicationPictogram } from "../components/ApplicationPictogram";
import { EKSPERTBISTAND_API_PATH } from "../utils/constants";
import { useErrorFocus } from "../hooks/useErrorFocus";
import useSWRMutation from "swr/mutation";
import { fetchJson } from "../utils/api";

const introSchema = z.object({
  bekreftRiktige: z
    .boolean()
    .refine(Boolean, { message: "Du må bekrefte at du vil svare så riktig som mulig." }),
  bekreftSamraad: z
    .boolean()
    .refine(Boolean, { message: "Du må bekrefte at søknaden fylles ut i samråd med den ansatte." }),
});

type IntroInputs = z.infer<typeof introSchema>;

const INTRO_FIELD_PATHS = ["bekreftRiktige", "bekreftSamraad"] as const satisfies ReadonlyArray<
  Path<IntroInputs>
>;

export default function SoknadPage() {
  const navigate = useNavigate();
  const [apiError, setApiError] = useState<string | null>(null);
  const { focusKey: errorFocusKey, bumpFocusKey } = useErrorFocus();
  const { trigger: createDraft, isMutating: creating } = useSWRMutation<
    { id?: string } | null,
    Error,
    string,
    RequestInit
  >(EKSPERTBISTAND_API_PATH, (url, { arg }) => fetchJson<{ id?: string } | null>(url, arg));

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<IntroInputs>({
    reValidateMode: "onBlur",
    shouldFocusError: false,
    defaultValues: { bekreftRiktige: false, bekreftSamraad: false },
    resolver: zodResolver(introSchema),
  });

  const onValid: SubmitHandler<IntroInputs> = async () => {
    setApiError(null);
    try {
      const payload = await createDraft({ method: "POST" });
      const id = payload?.id;
      if (!id) {
        throw new Error("Svar manglet id");
      }
      navigate(`/skjema/${id}/steg-1`);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Kunne ikke starte søknaden.";
      setApiError(message);
      bumpFocusKey();
    }
  };

  return (
    <DecoratedPage>
      <form onSubmit={handleSubmit(onValid, () => bumpFocusKey())}>
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
                error={Boolean(errors.bekreftRiktige)}
                {...register("bekreftRiktige")}
              >
                Jeg vil svare så godt jeg kan på spørsmålene i søknaden.
              </Checkbox>
              <Checkbox
                id="bekreftSamraad"
                error={Boolean(errors.bekreftSamraad)}
                {...register("bekreftSamraad")}
              >
                Jeg bekrefter at søknaden fylles ut i samråd med den ansatte. Arbeidsgiver og ansatt
                er enige om at ekspertbistand er hensiktsmessig.
              </Checkbox>
            </Box>
            <VStack gap="6">
              <FormErrorSummary
                errors={errors}
                fields={INTRO_FIELD_PATHS}
                heading="Du må rette disse feilene før du kan fortsette:"
                focusKey={errorFocusKey}
                extraItems={
                  apiError
                    ? [{ id: "start-soknad-feil", message: apiError, href: "#start-soknad-feil" }]
                    : undefined
                }
              />
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
