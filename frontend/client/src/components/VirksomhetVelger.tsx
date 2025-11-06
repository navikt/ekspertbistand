import React, { useEffect, useState } from "react";
import { BodyShort, ErrorMessage, Label, Loader, VStack } from "@navikt/ds-react";
import {
  Virksomhetsvelger as NavVirksomhetsvelger,
  type Organisasjon,
} from "@navikt/virksomhetsvelger";
import "@navikt/virksomhetsvelger/dist/assets/style.css";
import { EKSPERTBISTAND_ORGANISASJONER_PATH } from "../utils/constants";

type VirksomhetPickerProps = {
  label: React.ReactNode;
  value: string;
  onChange: (organisasjonsnummer: string, virksomhet?: Organisasjon) => void;
  error?: React.ReactNode;
};

export function VirksomhetVelger({ label, value, onChange, error }: VirksomhetPickerProps) {
  const [organisasjoner, setOrganisasjoner] = useState<Organisasjon[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    (async () => {
      try {
        const res = await fetch(EKSPERTBISTAND_ORGANISASJONER_PATH, {
          headers: { Accept: "application/json" },
          signal: controller.signal,
        });
        if (res.ok) {
          const payload = (await res.json()) as { hierarki?: Organisasjon[] };
          setOrganisasjoner(payload.hierarki ?? []);
        } else {
          setOrganisasjoner([]);
        }
      } catch {
        if (!controller.signal.aborted) {
          setOrganisasjoner([]);
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    })();

    return () => {
      controller.abort();
    };
  }, []);

  return (
    <VStack gap="1">
      <Label>{label}</Label>
      {loading && (
        <BodyShort size="small">
          <Loader size="small" title="Laster virksomheter" aria-live="polite" /> Laster virksomheter
          …
        </BodyShort>
      )}
      {organisasjoner.length > 0 ? (
        <NavVirksomhetsvelger
          key={value || "virksomhet-picker"}
          organisasjoner={organisasjoner}
          initValgtOrgnr={value || undefined}
          onChange={(org) => {
            onChange(org.orgnr, org);
          }}
          friKomponent
        />
      ) : !loading ? (
        <BodyShort size="small" textColor="subtle">
          Ingen virksomheter tilgjengelig.
        </BodyShort>
      ) : null}
      {error ? <ErrorMessage>{error}</ErrorMessage> : null}
    </VStack>
  );
}
