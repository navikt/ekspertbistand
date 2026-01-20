import type { TagProps } from "@navikt/ds-react";
import { z } from "zod";
import { EKSPERTBISTAND_API_PATH } from "../../utils/constants";
import { formatDateTime } from "../../utils/date";
import { fetchJson } from "../../utils/api";
import { ansattServerSchema, draftDtoServerSchema, virksomhetServerSchema } from "./server-schemas";

const DEFAULT_TITLE = "Søknad om tilskudd til ekspertbistand";

export type SkjemaStatus = "innsendt" | "utkast";

const virksomhetListSchema = virksomhetServerSchema
  .extend({
    navn: z.string().trim().optional(),
  })
  .partial();

const ansattListSchema = ansattServerSchema.partial();

const baseSkjemaSchema = draftDtoServerSchema.extend({
  id: z.string().trim().min(1),
  status: z.string().trim().optional(),
  virksomhet: virksomhetListSchema.optional(),
  ansatt: ansattListSchema.optional(),
  beslutning: z
    .object({
      status: z.string().trim().optional(),
      tidspunkt: z.string().optional(),
    })
    .optional(),
});

const skjemaSchema = baseSkjemaSchema.transform((value) => {
  const statusKey = (value.beslutning?.status ?? value.status ?? "ukjent").toLowerCase();
  return {
    ...value,
    statusKey,
    tag: resolveStatusTag(statusKey),
  };
});

const skjemaListSchema = z.array(skjemaSchema);

type Skjema = z.infer<typeof skjemaSchema>;

export type SkjemaListItem = {
  id: string;
  title: string;
  description: string;
  href: string;
  tag: {
    label: string;
    variant: TagProps["variant"];
  };
};

type SkjemaWithSort = SkjemaListItem & { sortKey: number };

const STATUSES_TO_FETCH: Array<Extract<SkjemaStatus, "innsendt" | "utkast">> = [
  "innsendt",
  "utkast",
];

export async function fetchSkjema(): Promise<SkjemaListItem[]> {
  const rawResults = await Promise.all(
    STATUSES_TO_FETCH.map(async (status) => {
      const response = await fetchJson<unknown[]>(`${EKSPERTBISTAND_API_PATH}?status=${status}`);
      return skjemaListSchema.parse(response ?? []);
    })
  );

  return rawResults
    .flat()
    .map(mapSkjemaToSoknad)
    .sort((a, b) => b.sortKey - a.sortKey)
    .map(({ sortKey: _unused, ...rest }) => rest);
}

function mapSkjemaToSoknad(raw: Skjema): SkjemaWithSort {
  const ansattNavn = raw.ansatt?.navn;
  const virksomhetNavn = raw.virksomhet?.navn ?? raw.virksomhet?.virksomhetsnavn;
  const orgnr = raw.virksomhet?.virksomhetsnummer;

  const baseTitle = ansattNavn ? `${DEFAULT_TITLE} – ${ansattNavn}` : DEFAULT_TITLE;
  const descriptionParts = [virksomhetNavn, orgnr ? `(org.nr ${orgnr})` : null].filter(Boolean);
  const description =
    descriptionParts.length > 0 ? descriptionParts.join(" ") : "Virksomhetsinformasjon mangler.";

  const timestamp = raw.innsendtTidspunkt ?? raw.opprettetTidspunkt;

  const formattedTimestamp = timestamp ? formatDateTime(timestamp) : null;
  const sortKey = timestamp && !Number.isNaN(Date.parse(timestamp)) ? Date.parse(timestamp) : 0;
  const label = `${raw.tag.label}: ${formattedTimestamp}`;

  const isDraft = raw.statusKey === "utkast";
  return {
    id: raw.id,
    title: baseTitle,
    description,
    href: isDraft ? `/skjema/${raw.id}` : `/skjema/${raw.id}/kvittering`,
    tag: { label, variant: raw.tag.variant },
    sortKey,
  };
}

function resolveStatusTag(statusKey: string): { label: string; variant: TagProps["variant"] } {
  switch (statusKey) {
    case "godkjent":
      return { label: "Godkjent", variant: "success" };
    case "innsendt":
      return { label: "Innsendt", variant: "alt1" };
    case "avlyst":
      return { label: "Avslag", variant: "error" };
    case "utkast":
      return { label: "Utkast", variant: "neutral" };
    default:
      return { label: statusKey, variant: "info" };
  }
}
