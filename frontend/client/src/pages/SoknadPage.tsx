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
import { EKSPERTBISTAND_API_PATH, EKSPERTBISTAND_INFO_URL, LOGIN_URL } from "../utils/constants";
import { useErrorFocus } from "../hooks/useErrorFocus";
import useSWRMutation from "swr/mutation";
import { fetchJson } from "../utils/api";
import { resolveApiError, type ApiErrorInfo } from "../utils/http";

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
  const [apiError, setApiError] = useState<ApiErrorInfo | null>(null);
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
      setApiError(resolveApiError(error, "Kunne ikke starte søknaden."));
      bumpFocusKey();
    }
  };

  return (
    <DecoratedPage>
      <form onSubmit={handleSubmit(onValid, () => bumpFocusKey())} autoComplete="off">
        <VStack gap="space-32" data-aksel-template="form-intropage-v4">
          <VStack gap="space-12">
            <Bleed asChild marginInline={{ lg: "space-128" }}>
              <Box
                width={{ xs: "64px", lg: "96px" }}
                height={{ xs: "64px", lg: "96px" }}
                asChild
                position={{ xs: "relative", lg: "absolute" }}
              >
                <ApplicationPictogram />
              </Box>
            </Bleed>
            <VStack gap="space-4" align="start">
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
              Tilskudd til ekspertbistand kan gis til arbeidsgivere for å forebygge lange eller
              hyppig gjentagende sykefravær i enkeltsaker.
            </BodyLong>
            <BodyLong spacing>
              Arbeidsgiver og den ansatte skal ha vurdert og/eller forsøkt å fjerne årsakene til
              sykefraværet uten å lykkes, for eksempel ved oppfølging og tilrettelegging eller
              gjennom kontakt med bedriftshelsetjenesten.
            </BodyLong>
            <BodyLong spacing>
              Arbeidsgiveren, Nav og den enkelte arbeidstakeren skal være enige om at det er
              hensiktsmessig med ekspertbistand.
            </BodyLong>
            <BodyLong>
              Les mer om <Link href={EKSPERTBISTAND_INFO_URL}>tilskudd til ekspertbistand</Link>.
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
                Du har blitt tildelt Altinn-tilgangen “Tilskudd til ekspertbistand” på riktig
                virksomhet.
              </List.Item>
            </List>
          </div>
          <div>
            <Accordion>
              <Accordion.Item>
                <Accordion.Header>Hvordan vi behandler personopplysninger</Accordion.Header>
                <Accordion.Content>
                  <BodyLong spacing>
                    Søknader og utkast om tilskudd til ekspertbistand er synlig for alle som har
                    Altinn-tilgangen “Tilskudd til ekspertbistand” i virksomheten. Av hensyn til
                    personvernet fjerner vi deres tilgang til søknaden 12 måneder etter at tiltaket
                    er avsluttet.
                  </BodyLong>
                  <BodyLong spacing>
                    Søknad om tilskudd til ekspertbistand inneholder personopplysninger. Søknaden
                    skal kun inneholde personopplysninger om den arbeidstakeren søknaden gjelder, og
                    det skal ikke gis flere personopplysninger enn det som er nødvendig for å
                    behandle søknaden.
                  </BodyLong>
                  <BodyLong spacing>
                    Vær oppmerksom på at arbeidstakeren har innsynsrett i søknaden om tilskudd til
                    ekspertbistand.
                  </BodyLong>
                  <BodyLong spacing>
                    Virksomheten er ansvarlig for å sikre at personopplysninger blir behandlet i
                    tråd med bestemmelsene i personopplysningsloven. Se nærmere informasjon om
                    personvern på arbeidsplassen i{" "}
                    <Link href="https://www.datatilsynet.no/personvern-pa-ulike-omrader/personvern-pa-arbeidsplassen/">
                      Datatilsynets veileder
                    </Link>
                    .
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
                    Utkastet er synlig for alle som har Altinn-tilgangen “Tilskudd til
                    ekspertbistand” i virksomheten.
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
            <Box paddingBlock="space-16 space-32">
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
            <VStack gap="space-32" align="start">
              <FormErrorSummary
                errors={errors}
                fields={INTRO_FIELD_PATHS}
                heading="Du må rette disse feilene før du kan fortsette:"
                focusKey={errorFocusKey}
                extraItems={
                  apiError
                    ? [
                        {
                          id: "start-soknad-feil",
                          message: apiError.message,
                          href: "#start-soknad-feil",
                        },
                      ]
                    : undefined
                }
              />
              {apiError ? (
                <Alert variant="error" inline>
                  <VStack gap="space-32">
                    <BodyLong>{apiError.message}</BodyLong>
                    {apiError.requiresLogin ? (
                      <Button as="a" href={LOGIN_URL} size="small" variant="primary">
                        Logg inn
                      </Button>
                    ) : null}
                  </VStack>
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
