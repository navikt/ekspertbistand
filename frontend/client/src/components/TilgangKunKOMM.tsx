import { Bleed, Box, Heading, LinkCard, List, VStack } from "@navikt/ds-react";
import { ApplicationPictogram } from "./ApplicationPictogram.tsx";
import { TILGANGSSTYRING_URL } from "../utils/constants.ts";

export default function TilgangKunKOMM() {
  return (
    <VStack gap="space-8" data-aksel-template="form-intropage-v4">
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
      <div>
        <Heading level="2" size="large" spacing>
          Du mangler tilgang i Altinn
        </Heading>
        <List>
          <List.Item>
            Du har fått delegert tilgangen på et nivå som ikke fungerer for virksomheter i
            organisasjoner med organisasjonsledd. Be den som ga deg enkelttjenesten “Tilskudd til
            ekspertbistand” om å delegere på nytt – på organisasjonsleddet eller direkte på
            virksomheten. Dette er en kjent begrensning i Altinn.
          </List.Item>
        </List>
      </div>
      <LinkCard>
        <LinkCard.Title>
          <LinkCard.Anchor href={TILGANGSSTYRING_URL} target="_blank" rel="noreferrer">
            Les mer om tilgangsstyring
          </LinkCard.Anchor>
        </LinkCard.Title>
      </LinkCard>
    </VStack>
  );
}
