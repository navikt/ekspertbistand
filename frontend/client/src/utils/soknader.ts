import type { TagProps } from "@navikt/ds-react";

import { EKSPERTBISTAND_API_PATH } from "./constants";
import { formatDateTime } from "./date";
import { parseErrorMessage } from "./http";

const DEFAULT_TITLE = "Søknad om tilskudd til ekspertbistand";

const STATUS_CONFIG: Record<string, { label: string; variant: TagProps["variant"] }> = {
  godkjent: { label: "Godkjent", variant: "success" },
  innsendt: { label: "Innsendt", variant: "success" },
  avslag: { label: "Avslag", variant: "error" },
  utkast: { label: "Utkast", variant: "neutral" },
  underbehandling: { label: "Under behandling", variant: "info" },
};

export type SkjemaStatus = "innsendt" | "utkast" | (string & NonNullable<unknown>);

export type RawSkjema = {
  id?: string | null;
  status?: SkjemaStatus | null;
  virksomhet?: {
    navn?: string | null;
    virksomhetsnummer?: string | null;
  } | null;
  ansatt?: {
    navn?: string | null;
  } | null;
  opprettetTidspunkt?: string | null;
  innsendtTidspunkt?: string | null;
  beslutning?: {
    status?: string | null;
    tidspunkt?: string | null;
  } | null;
};

export type ApplicationListItem = {
  id: string;
  title: string;
  description: string;
  href: string;
  tag: {
    label: string;
    variant: TagProps["variant"];
  };
};

type ApplicationWithSort = ApplicationListItem & { sortKey: number };

const STATUSES_TO_FETCH: Array<Extract<SkjemaStatus, "innsendt" | "utkast">> = [
  "innsendt",
  "utkast",
];

export async function fetchApplications(signal: AbortSignal): Promise<ApplicationListItem[]> {
  const rawResults = await Promise.all(
    STATUSES_TO_FETCH.map(async (status) => {
      const response = await fetch(`${EKSPERTBISTAND_API_PATH}?status=${status}`, {
        headers: { Accept: "application/json" },
        signal,
      });

      if (!response.ok) {
        const message = await parseErrorMessage(response);
        throw new Error(message ?? `Klarte ikke å hente søknader (${response.status}).`);
      }

      return await parseJsonArray(response);
    })
  );

  return rawResults
    .flat()
    .map(mapSkjemaToSoknad)
    .filter((item): item is ApplicationWithSort => item !== null)
    .sort((a, b) => b.sortKey - a.sortKey)
    .map(({ sortKey: _unused, ...rest }) => rest);
}

function mapSkjemaToSoknad(raw: RawSkjema): ApplicationWithSort | null {
  const id = raw.id?.trim();
  if (!id) return null;

  const employeeName = raw.ansatt?.navn?.trim();
  const virksomhetNavn = raw.virksomhet?.navn?.trim();
  const orgnr = formatOrgNr(raw.virksomhet?.virksomhetsnummer);

  const baseTitle = employeeName ? `${DEFAULT_TITLE} – ${employeeName}` : DEFAULT_TITLE;
  const descriptionParts = [virksomhetNavn, orgnr ? `(org.nr ${orgnr})` : null].filter(Boolean);
  const description =
    descriptionParts.length > 0 ? descriptionParts.join(" ") : "Virksomhetsinformasjon mangler.";

  const statusKey = (raw.beslutning?.status ?? raw.status ?? "ukjent").toLowerCase();
  const statusConfig = STATUS_CONFIG[statusKey] ?? {
    label: capitalize(statusKey),
    variant: "info" as TagProps["variant"],
  };

  const timestamp =
    raw.beslutning?.tidspunkt ?? raw.innsendtTidspunkt ?? raw.opprettetTidspunkt ?? null;

  const formattedTimestamp = timestamp ? formatDateTime(timestamp) : null;
  const sortKey = timestamp && !Number.isNaN(Date.parse(timestamp)) ? Date.parse(timestamp) : 0;
  const label = formattedTimestamp
    ? `${statusConfig.label}: ${formattedTimestamp}`
    : statusConfig.label;

  const isDraft = (raw.status ?? "").toLowerCase() === "utkast";
  return {
    id,
    title: baseTitle,
    description,
    href: isDraft ? `/skjema/${id}` : `/skjema/${id}/kvittering`,
    tag: { label, variant: statusConfig.variant },
    sortKey,
  };
}

async function parseJsonArray(response: Response): Promise<RawSkjema[]> {
  if (response.status === 204) {
    return [];
  }

  const body = await response.text();
  if (!body || body.trim().length === 0) {
    return [];
  }

  try {
    const parsed = JSON.parse(body) as unknown;
    return Array.isArray(parsed) ? (parsed as RawSkjema[]) : [];
  } catch (error) {
    throw new Error(error instanceof Error ? error.message : "Kunne ikke tolke svar fra serveren.");
  }
}

function capitalize(value: string) {
  if (!value) return "Status";
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function formatOrgNr(value?: string | null) {
  if (!value) return null;
  const digits = value.replace(/\D/g, "");
  if (digits.length !== 9) return digits;
  return `${digits.slice(0, 3)} ${digits.slice(3, 6)} ${digits.slice(6)}`;
}
