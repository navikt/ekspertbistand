import React from "react";
import { useNavigate } from "react-router-dom";
import { ArrowRightIcon } from "@navikt/aksel-icons";
import { type SubmitErrorHandler, type SubmitHandler, useForm } from "react-hook-form";
import {
  Accordion,
  Bleed,
  BodyLong,
  BodyShort,
  Box,
  Button,
  Checkbox,
  GuidePanel,
  Heading,
  ErrorSummary,
  Link,
  List,
  VStack,
} from "@navikt/ds-react";
import DecoratedPage from "../components/DecoratedPage";

export default function SoknadPage() {
  const navigate = useNavigate();
  const errorSummaryRef = React.useRef<HTMLDivElement>(null);

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

  const onValid: SubmitHandler<IntroInputs> = () => navigate("/skjema");
  const onInvalid: SubmitErrorHandler<IntroInputs> = () => {
    errorSummaryRef.current?.focus();
  };

  return (
    <DecoratedPage
      blockProps={{ width: "text", gutters: true }}
      languages={[
        { locale: "nb", url: "https://www.nav.no" },
        { locale: "en", url: "https://www.nav.no/en" },
      ]}
    >
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
              <BodyShort size="small">Nav 10-07.03 (om relevant)</BodyShort>
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
              Tilskuddet dekker hjelp til arbeidsgiver og ansatt fra en nøytral ekspert som har
              kompetanse på sykefravær og arbeidsmiljø.
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
                Du har snakket med Nav og den ansatte og dere er enige om at det er hensiktsmessig
                med ekspertbistand.
              </List.Item>
              <List.Item>
                Du har vet hvilken ekspert du ønsker å bruke og hvilken hjelp denne kan tilby.
              </List.Item>
            </List>
          </div>
          <div>
            <Accordion>
              <Accordion.Item>
                <Accordion.Header>Informasjon vi henter om deg</Accordion.Header>
                <Accordion.Content>
                  <BodyLong>
                    Vi henter ut hvilke virksomheter du har Altinn enkeltrettighten “XX”
                  </BodyLong>
                </Accordion.Content>
              </Accordion.Item>
              <Accordion.Item>
                <Accordion.Header>Hvordan vi behandler personopplysninger</Accordion.Header>
                <Accordion.Content>
                  <BodyLong>
                    Her skal det stå informasjon om hvordan vi behandler personopplysningene til
                    søkeren.
                  </BodyLong>
                </Accordion.Content>
              </Accordion.Item>
              <Accordion.Item>
                <Accordion.Header>Vi lagrer svar underveis</Accordion.Header>
                <Accordion.Content>
                  <BodyLong>
                    Her skal det stå informasjon om hvordan denne søknaden mellomlagrer
                    informasjonen til søkeren og hvor lenge informasjonen lagres. Vi skal informere
                    om mellomlagring ved både automatisk lagring og ved samtykke til lagring med
                    lagre-knapp.
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
                Jeg bekrefter at jeg vil svare så riktig som jeg kan.
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
              {Object.values(errors).length > 0 && (
                <ErrorSummary
                  ref={errorSummaryRef}
                  heading="Du må rette disse feilene før du kan fortsette:"
                >
                  {Object.entries(errors).map(([key, error]) => (
                    <ErrorSummary.Item key={key} href={`#${key}`}>
                      {error?.message as string}
                    </ErrorSummary.Item>
                  ))}
                </ErrorSummary>
              )}
              <Button
                type="submit"
                variant="primary"
                icon={<ArrowRightIcon aria-hidden />}
                iconPosition="right"
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

const ApplicationPictogram = (props: React.SVGProps<SVGSVGElement>) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    width="96"
    height="96"
    viewBox="0 0 72 72"
    fill="none"
    aria-hidden
    {...props}
  >
    <rect x="23.25" y="22.5" width="26.25" height="9" fill="#CCE2F0" />
    <rect x="23.25" y="36.75" width="26.25" height="9" fill="#CCE2F0" />
    <circle cx="36.75" cy="34.5" r="21" fill="#CCE2F0" />
    <path
      d="M23.7672 5.508L30.1202 11.8434M1.5 33.75H34.5M26.4706 2.81211L10.5882 18.6506L9 26.5699L16.9412 24.986L32.8235 9.14751C34.5778 7.39804 34.5778 4.56158 32.8235 2.81211C31.0692 1.06263 28.2249 1.06263 26.4706 2.81211Z"
      stroke="#23262A"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M56.25 44.25L63.75 44.25"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M56.25 52.5L63.75 52.5"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M56.25 60.75L63.75 60.75"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M48 52.5L51 52.5"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M48 52.5L51 52.5"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M48 44.25L51 44.25"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M48 44.25L51 44.25"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M48 60.75L51 60.75"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M48 60.75L51 60.75"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <rect
      x="41.25"
      y="33"
      width="29.25"
      height="37.5"
      stroke="#262626"
      strokeWidth="2.25"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);
