import { EyeClosedIcon, PlusIcon } from "@navikt/aksel-icons";
import {
  BodyShort,
  Box,
  Button,
  HStack,
  Loader,
  LocalAlert,
  Textarea,
  VStack,
} from "@navikt/ds-react";
import { useEffect, useRef, useState } from "react";
import {
  type NotatFeil,
  type Saksnotat,
  SAKSNOTAT_MAKS_LENGDE,
  useSaksnotatHandlinger,
} from "../hooks/useSaksnotater";
import { useInnloggetAnsatt } from "../tilgang/useTilgang";
import type { HttpError } from "../utils/http";
import classes from "./Saksnotater.module.css";

const tidspunktFormat = new Intl.DateTimeFormat("nb-NO", {
  day: "numeric",
  month: "short",
  year: "numeric",
});
const klokkeslettFormat = new Intl.DateTimeFormat("nb-NO", {
  hour: "2-digit",
  minute: "2-digit",
});

function formaterTidspunkt(iso: string) {
  const dato = new Date(iso);
  return `${tidspunktFormat.format(dato)} kl. ${klokkeslettFormat.format(dato)}`;
}

const TOM_TEKST = "Skriv et notat før du lagrer.";

type NotatSkjemaProps = {
  label: string;
  startverdi: string;
  isSaving: boolean;
  onLagre: (tekst: string) => Promise<NotatFeil | null>;
  onAvbryt: () => void;
};

function NotatSkjema({ label, startverdi, isSaving, onLagre, onAvbryt }: NotatSkjemaProps) {
  const [tekst, setTekst] = useState(startverdi);
  const [feltFeil, setFeltFeil] = useState<string | undefined>();
  const feltRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    feltRef.current?.focus();
  }, []);

  const lagre = async () => {
    if (tekst.trim().length === 0) {
      setFeltFeil(TOM_TEKST);
      feltRef.current?.focus();
      return;
    }
    setFeltFeil(undefined);
    const feil = await onLagre(tekst.trim());
    if (feil?.iFeltet) {
      setFeltFeil(feil.melding);
      feltRef.current?.focus();
    }
  };

  return (
    <VStack gap="space-8">
      <Textarea
        ref={feltRef}
        label={label}
        size="small"
        value={tekst}
        onChange={(e) => {
          setTekst(e.target.value);
          if (feltFeil) setFeltFeil(undefined);
        }}
        maxLength={SAKSNOTAT_MAKS_LENGDE}
        minRows={3}
        error={feltFeil}
      />
      <HStack gap="space-8">
        <Button size="small" variant="primary" onClick={lagre} loading={isSaving}>
          Lagre
        </Button>
        <Button size="small" variant="secondary" onClick={onAvbryt} disabled={isSaving}>
          Avbryt
        </Button>
      </HStack>
    </VStack>
  );
}

type NotatProps = {
  notat: Saksnotat;
  erEget: boolean;
  redigerer: boolean;
  kanRedigere: boolean;
  isSaving: boolean;
  articleRef: (element: HTMLElement | null) => void;
  onEndre: () => void;
  onSlett: () => void;
  onLagre: (tekst: string) => Promise<NotatFeil | null>;
  onAvbryt: () => void;
};

function Notat({
  notat,
  erEget,
  redigerer,
  kanRedigere,
  isSaving,
  articleRef,
  onEndre,
  onSlett,
  onLagre,
  onAvbryt,
}: NotatProps) {
  return (
    <article ref={articleRef} tabIndex={-1} aria-label={`Notat fra ${notat.skrevetAvNavn}`}>
      <VStack gap="space-8">
        <HStack gap="space-8" align="center" wrap={false}>
          <BodyShort
            as="span"
            size="small"
            weight="semibold"
            className={classes.initial}
            aria-hidden
          >
            {notat.skrevetAvNavn.charAt(0).toUpperCase()}
          </BodyShort>
          <VStack>
            <BodyShort size="small" weight="semibold">
              {notat.skrevetAvNavn}
            </BodyShort>
            <BodyShort size="small" textColor="subtle">
              <time dateTime={notat.opprettet}>{formaterTidspunkt(notat.opprettet)}</time>
              {notat.endret && (
                <>
                  {" · endret "}
                  <time dateTime={notat.endret}>{formaterTidspunkt(notat.endret)}</time>
                </>
              )}
            </BodyShort>
          </VStack>
        </HStack>
        {redigerer ? (
          <NotatSkjema
            label="Endre notat"
            startverdi={notat.tekst}
            isSaving={isSaving}
            onLagre={onLagre}
            onAvbryt={onAvbryt}
          />
        ) : (
          <>
            <BodyShort className={classes.tekst}>{notat.tekst}</BodyShort>
            {erEget && (
              <HStack gap="space-4">
                <Button size="xsmall" variant="tertiary" onClick={onEndre} disabled={!kanRedigere}>
                  Endre
                </Button>
                <Button
                  size="xsmall"
                  variant="tertiary"
                  data-color="danger"
                  onClick={onSlett}
                  disabled={!kanRedigere}
                >
                  Slett
                </Button>
              </HStack>
            )}
          </>
        )}
      </VStack>
    </article>
  );
}

type SaksnotaterProps = {
  sakId: string;
  notater: Saksnotat[] | undefined;
  error: HttpError | undefined;
  isLoading: boolean;
};

type Redigering = { type: "ny" } | { type: "endre"; notatId: string } | null;

export default function Saksnotater({ sakId, notater, error, isLoading }: SaksnotaterProps) {
  const innloggetAnsatt = useInnloggetAnsatt();
  const { opprett, endre, slett, isSaving } = useSaksnotatHandlinger(sakId);
  const [redigering, setRedigering] = useState<Redigering>(null);
  const [handlingFeil, setHandlingFeil] = useState<string | null>(null);
  const nyKnappRef = useRef<HTMLButtonElement>(null);
  const notatRefs = useRef(new Map<string, HTMLElement>());
  const fokusEtterSletting = useRef<string | "ny" | null>(null);

  useEffect(() => {
    const mål = fokusEtterSletting.current;
    if (!mål) return;
    fokusEtterSletting.current = null;
    if (mål === "ny") nyKnappRef.current?.focus();
    else notatRefs.current.get(mål)?.focus();
  }, [notater]);

  const håndterFeil = (feil: NotatFeil | null) => {
    if (feil && !feil.iFeltet) setHandlingFeil(feil.melding);
    return feil;
  };

  const lagreNytt = async (tekst: string) => {
    setHandlingFeil(null);
    const feil = håndterFeil(await opprett(tekst));
    if (!feil) setRedigering(null);
    return feil;
  };

  const lagreEndring = (notatId: string) => async (tekst: string) => {
    setHandlingFeil(null);
    const feil = håndterFeil(await endre(notatId, tekst));
    if (!feil) setRedigering(null);
    return feil;
  };

  const slettNotat = async (notatId: string, index: number) => {
    setHandlingFeil(null);
    const neste = notater?.[index + 1] ?? notater?.[index - 1];
    fokusEtterSletting.current = neste ? neste.notatId : "ny";
    const feil = håndterFeil(await slett(notatId));
    if (feil) fokusEtterSletting.current = null;
  };

  const avbryt = () => {
    const forrige = redigering;
    setRedigering(null);
    if (forrige?.type === "ny") requestAnimationFrame(() => nyKnappRef.current?.focus());
  };

  return (
    <VStack gap="space-16">
      <Box
        padding="space-12"
        borderRadius="8"
        borderWidth="1"
        borderColor="meta-purple-subtle"
        background="meta-purple-soft"
      >
        <HStack gap="space-8" wrap={false}>
          <EyeClosedIcon aria-hidden fontSize="1.5rem" />
          <VStack gap="space-4">
            <BodyShort size="small" weight="semibold">
              Internt arbeidsnotat
            </BodyShort>
            <BodyShort size="small">
              Vises ikke til arbeidsgiver eller deltaker med mindre de ber om innsyn.
            </BodyShort>
          </VStack>
        </HStack>
      </Box>

      {redigering?.type === "ny" ? (
        <NotatSkjema
          label="Nytt notat"
          startverdi=""
          isSaving={isSaving}
          onLagre={lagreNytt}
          onAvbryt={avbryt}
        />
      ) : (
        <Button
          ref={nyKnappRef}
          size="small"
          variant="primary"
          icon={<PlusIcon aria-hidden />}
          onClick={() => {
            setHandlingFeil(null);
            setRedigering({ type: "ny" });
          }}
          disabled={isLoading || !!error}
        >
          Skriv nytt notat
        </Button>
      )}

      {handlingFeil && (
        <LocalAlert status="error" size="small">
          <LocalAlert.Header>
            <LocalAlert.Title as="h3">{handlingFeil}</LocalAlert.Title>
          </LocalAlert.Header>
        </LocalAlert>
      )}

      {isLoading ? (
        <Loader size="medium" title="Laster notatene" />
      ) : error ? (
        <LocalAlert status="error" size="small">
          <LocalAlert.Header>
            <LocalAlert.Title as="h3">Kunne ikke hente notatene.</LocalAlert.Title>
          </LocalAlert.Header>
        </LocalAlert>
      ) : !notater || notater.length === 0 ? (
        <BodyShort>Ingen notater ennå.</BodyShort>
      ) : (
        <VStack gap="space-24">
          {notater.map((notat, index) => (
            <Notat
              key={notat.notatId}
              notat={notat}
              erEget={notat.skrevetAvIdent === innloggetAnsatt?.id}
              redigerer={redigering?.type === "endre" && redigering.notatId === notat.notatId}
              kanRedigere={redigering === null && !isSaving}
              isSaving={isSaving}
              articleRef={(element) => {
                if (element) notatRefs.current.set(notat.notatId, element);
                else notatRefs.current.delete(notat.notatId);
              }}
              onEndre={() => {
                setHandlingFeil(null);
                setRedigering({ type: "endre", notatId: notat.notatId });
              }}
              onSlett={() => slettNotat(notat.notatId, index)}
              onLagre={lagreEndring(notat.notatId)}
              onAvbryt={avbryt}
            />
          ))}
        </VStack>
      )}
    </VStack>
  );
}
