import {
  Alert,
  Button,
  DatePicker,
  ErrorSummary,
  Fieldset,
  HStack,
  Loader,
  Radio,
  RadioGroup,
  TextField,
  Textarea,
  VStack,
  useDatepicker,
} from "@navikt/ds-react";
import { useEffect, useRef, useState } from "react";
import {
  type ForelopigVedtakOppsett,
  useForelopigVedtakOppsett,
} from "../../hooks/useForelopigVedtakOppsett";
import type { ForelopigUtfall, SakDetaljer } from "../../hooks/useSak";
import type { Vilkår } from "../../hooks/useVilkår";
import { tolkSoknadssum } from "../../utils/belop";
import { gjeldendeMaksSats } from "../../utils/maksSats";
import VurderingKort from "./VurderingKort";
import {
  type FeltId,
  type ForelopigVedtakSkjemaTilstand,
  INGEN_SATS_TEKST,
  type Mottaker,
  UTFALL_TEKST,
  begrunnelse,
  fraIsoDato,
  harBegrunnelseTilArbeidsgiver,
  harTiltaksperiode,
  innvilgeSperretTekst,
  standardSluttdato,
  tilIsoDato,
  validerSkjema,
  vurderingRader,
} from "./forelopigVedtak";

export type OppdaterSkjema = (
  endring:
    | Partial<ForelopigVedtakSkjemaTilstand>
    | ((tilstand: ForelopigVedtakSkjemaTilstand) => Partial<ForelopigVedtakSkjemaTilstand>)
) => void;

type Props = {
  sak: SakDetaljer;
  vilkår: Vilkår[];
  tilstand: ForelopigVedtakSkjemaTilstand;
  oppdater: OppdaterSkjema;
};

const UTFALL: ForelopigUtfall[] = ["INNVILGE", "INNVILGE_REDUSERT", "AVSLAA"];

const FELT_ID: Record<Exclude<FeltId, "utfall">, string> = {
  nyttBelop: "forelopig-vedtak-nytt-belop",
  startdato: "forelopig-vedtak-startdato",
  sluttdato: "forelopig-vedtak-sluttdato",
  deltaker: "forelopig-vedtak-begrunnelse-deltaker",
  arbeidsgiver: "forelopig-vedtak-begrunnelse-arbeidsgiver",
};

const radioId = (utfall: ForelopigUtfall) => `forelopig-vedtak-utfall-${utfall}`;

const BEGRUNNELSE_LABEL: Record<Mottaker, string> = {
  deltaker: "Deltaker - tilpass begrunnelse",
  arbeidsgiver: "Arbeidsgiver - tilpass begrunnelse",
};

export default function ForelopigVedtakSkjema(props: Props) {
  const { oppsett, error, isLoading } = useForelopigVedtakOppsett();

  if (isLoading) return <Loader size="medium" title="Laster standardtekster og satser" />;
  if (error || !oppsett) {
    return (
      <Alert variant="error" size="small">
        Kunne ikke hente standardtekster og satser. Prøv igjen senere.
      </Alert>
    );
  }
  return <Skjema {...props} oppsett={oppsett} />;
}

function Skjema({
  sak,
  vilkår,
  tilstand,
  oppdater,
  oppsett,
}: Props & { oppsett: ForelopigVedtakOppsett }) {
  const { standardtekster, maksSatser } = oppsett;
  const soknadssum = tolkSoknadssum(sak.soknad.behovForBistand.estimertKostnad);
  const { utfall, tiltaksperiodeFra: fra, tiltaksperiodeTil: til } = tilstand;

  const [forsøkt, setForsøkt] = useState(false);
  const [sluttdatoEtterÅrsskiftet, setSluttdatoEtterÅrsskiftet] = useState(false);
  const [antallMislykkedeForsøk, setAntallMislykkedeForsøk] = useState(0);
  const errorSummaryRef = useRef<HTMLDivElement>(null);
  // Hindrer at sluttdatoen vi regner ut, telles som en endring saksbehandler har gjort.
  const setterSluttdatoAutomatisk = useRef(false);

  useEffect(() => {
    if (antallMislykkedeForsøk > 0) errorSummaryRef.current?.focus();
  }, [antallMislykkedeForsøk]);

  const startdato = fra ? fraIsoDato(fra) : undefined;

  const sluttdatoPicker = useDatepicker({
    defaultSelected: til ? fraIsoDato(til) : undefined,
    toDate: startdato ? new Date(startdato.getFullYear(), 11, 31) : undefined,
    onDateChange: (dato) => {
      if (setterSluttdatoAutomatisk.current) return;
      oppdater({ tiltaksperiodeTil: dato ? tilIsoDato(dato) : null, sluttdatoEndret: true });
    },
    onValidate: (validering) => setSluttdatoEtterÅrsskiftet(validering.isAfter),
  });

  const startdatoPicker = useDatepicker({
    defaultSelected: startdato,
    onDateChange: (dato) => {
      const nyFra = dato ? tilIsoDato(dato) : null;
      if (!nyFra || tilstand.sluttdatoEndret) {
        oppdater({ tiltaksperiodeFra: nyFra });
        return;
      }
      const nyTil = standardSluttdato(nyFra);
      oppdater({ tiltaksperiodeFra: nyFra, tiltaksperiodeTil: nyTil });
      setterSluttdatoAutomatisk.current = true;
      sluttdatoPicker.setSelected(nyTil ? fraIsoDato(nyTil) : undefined);
      setterSluttdatoAutomatisk.current = false;
      setSluttdatoEtterÅrsskiftet(false);
    },
  });

  const sats = fra ? gjeldendeMaksSats(maksSatser, fra) : undefined;
  const innvilgeSperret = soknadssum !== null && sats !== undefined && soknadssum > sats.belop;
  const antallOppfylt = vilkår.filter((v) => v.vurdering.status === "oppfylt").length;

  const tekst = (mottaker: Mottaker) =>
    utfall ? begrunnelse(tilstand, utfall, mottaker, standardtekster, soknadssum) : "";
  const begrunnelseDeltaker = tekst("deltaker");
  const begrunnelseArbeidsgiver = tekst("arbeidsgiver");

  const { feil, input } = validerSkjema({
    tilstand,
    soknadssum,
    maksSatser,
    begrunnelseDeltaker,
    begrunnelseArbeidsgiver,
    sluttdatoEtterÅrsskiftet,
  });

  // Sperren mot innvilgelse og manglende sats vises med en gang, ikke først ved forhåndsvisning.
  const løpendeFeil = (felt: FeltId): string | undefined => {
    if (felt === "utfall" && utfall === "INNVILGE" && innvilgeSperret && sats) {
      return innvilgeSperretTekst(sats);
    }
    if (felt === "startdato" && utfall && harTiltaksperiode(utfall) && fra && !sats) {
      return INGEN_SATS_TEKST;
    }
    return undefined;
  };
  const feilFor = (felt: FeltId) =>
    (forsøkt ? feil.find((f) => f.felt === felt)?.melding : undefined) ?? løpendeFeil(felt);

  const hrefFor = (felt: FeltId) => {
    if (felt !== "utfall") return `#${FELT_ID[felt]}`;
    const valgbart = utfall ?? UTFALL.find((u) => u !== "INNVILGE" || !innvilgeSperret);
    return `#${radioId(valgbart ?? "AVSLAA")}`;
  };

  const forhandsvis = () => {
    setForsøkt(true);
    if (input) {
      oppdater({ forhandsvisning: input });
    } else {
      setAntallMislykkedeForsøk((n) => n + 1);
    }
  };

  const endreBegrunnelse = (mottaker: Mottaker, verdi: string) => {
    if (!utfall) return;
    oppdater((t) => ({
      redigerteBegrunnelser: {
        ...t.redigerteBegrunnelser,
        [utfall]: { ...t.redigerteBegrunnelser[utfall], [mottaker]: verdi },
      },
    }));
  };

  const begrunnelsefelt = (mottaker: Mottaker, verdi: string) => (
    <Textarea
      id={FELT_ID[mottaker]}
      label={BEGRUNNELSE_LABEL[mottaker]}
      description={`Teksten blir synlig i vedtaksbrevet til ${mottaker}.`}
      value={verdi}
      onChange={(e) => endreBegrunnelse(mottaker, e.target.value)}
      error={feilFor(mottaker)}
      minRows={3}
    />
  );

  return (
    <VStack gap="space-24">
      <RadioGroup
        legend="Vurdering"
        value={utfall ?? ""}
        onChange={(verdi: ForelopigUtfall) => oppdater({ utfall: verdi })}
        error={feilFor("utfall")}
      >
        {UTFALL.map((u) => (
          <Radio
            key={u}
            id={radioId(u)}
            value={u}
            disabled={u === "INNVILGE" && innvilgeSperret}
            description={
              u === "INNVILGE" && innvilgeSperret && sats ? innvilgeSperretTekst(sats) : undefined
            }
          >
            {u === "INNVILGE"
              ? `${UTFALL_TEKST[u]} (${antallOppfylt} av ${vilkår.length} vilkår oppfylt)`
              : UTFALL_TEKST[u]}
          </Radio>
        ))}
      </RadioGroup>

      {utfall && (
        <>
          <VurderingKort
            utfall={utfall}
            tittelNivå="h2"
            rader={vurderingRader(
              utfall,
              sak,
              utfall === "INNVILGE_REDUSERT" ? null : soknadssum,
              null
            )}
          />

          {utfall === "INNVILGE_REDUSERT" && (
            <TextField
              id={FELT_ID.nyttBelop}
              label="Nytt beløp"
              inputMode="numeric"
              htmlSize={14}
              value={tilstand.nyttBelop}
              onChange={(e) => oppdater({ nyttBelop: e.target.value })}
              error={feilFor("nyttBelop")}
            />
          )}

          {harTiltaksperiode(utfall) && (
            <Fieldset legend="Tiltaksperiode" errorPropagation={false}>
              <HStack gap="space-16" wrap>
                <DatePicker {...startdatoPicker.datepickerProps}>
                  <DatePicker.Input
                    {...startdatoPicker.inputProps}
                    id={FELT_ID.startdato}
                    label="Startdato"
                    error={feilFor("startdato")}
                  />
                </DatePicker>
                <DatePicker {...sluttdatoPicker.datepickerProps}>
                  <DatePicker.Input
                    {...sluttdatoPicker.inputProps}
                    id={FELT_ID.sluttdato}
                    label="Sluttdato"
                    error={feilFor("sluttdato")}
                  />
                </DatePicker>
              </HStack>
            </Fieldset>
          )}

          {begrunnelsefelt("deltaker", begrunnelseDeltaker)}
          {harBegrunnelseTilArbeidsgiver(utfall) &&
            begrunnelsefelt("arbeidsgiver", begrunnelseArbeidsgiver)}
        </>
      )}

      {forsøkt && feil.length > 0 && (
        <ErrorSummary
          ref={errorSummaryRef}
          heading="Du må rette disse feilene før du kan gå videre"
        >
          {feil.map(({ felt, melding }) => (
            <ErrorSummary.Item key={`${felt}-${melding}`} href={hrefFor(felt)}>
              {melding}
            </ErrorSummary.Item>
          ))}
        </ErrorSummary>
      )}

      <HStack>
        <Button type="button" onClick={forhandsvis}>
          Forhåndsvis foreløpig vedtak
        </Button>
      </HStack>
    </VStack>
  );
}
