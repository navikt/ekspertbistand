import { Bleed, Box, Heading, LinkCard, List, VStack } from "@navikt/ds-react";
import { ApplicationPictogram } from "./ApplicationPictogram.tsx";
import { TILGANGSSTYRING_URL } from "../utils/constants.ts";

export default function ManglerTilganger() {
  return (
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
      <div>
        <Heading level="2" size="large" spacing>
          Før du søker
        </Heading>
        <List>
          <List.Item>
            For å kunne sende in søknad må du ha blitt tildelt enkeltrettigheten “Ekspertbistand”
            for den aktuelle virksomheten.
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
