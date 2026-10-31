# Aktører og tilstander: fra søknad til utbetalt refusjon

Dette dokumentet viser hva arbeidsgiver, deltaker, saksbehandler og beslutter
gjør med løsningen, fra søknaden sendes inn til refusjonen er utbetalt. Hvert
steg er merket med status, så vi ser hva vi har og hva som mangler.

Gjelder saker i ny saksbehandling (kilde `EKSPERTBISTAND`). Arena-saker
behandles ferdig i Arena og er ikke med. Klage og tilbakekreving er utenfor
løsningen.

Arbeidsgiver ser nye saker på nye sider under en egen rute bak feature toggle
(🛤️). Dagens sider beholdes uendret for Arena-saker.

Andre dokumenter i mappa:

- [Lanseringsplan](lanseringsplan_2027-04-01.md): status, rekkefølge, spor og
  endringslogg fram til 1. april 2027
- [Tidslinje](https://htmlpreview.github.io/?https://github.com/navikt/ekspertbistand/blob/main/planning/lanseringsplan_2027-04-01.html):
  Gantt-diagram over sporene uke for uke

## Interaksjoner

Hvert steg er merket med status:

- 🟢 finnes, i prod eller klart i dev
- 🟡 delvis: bygget, men ikke koblet inn, bare i dev eller mangler noe
- 🔴 mangler

Emojien etter statusen er steget i [lanseringsplanen](lanseringsplan_2027-04-01.md),
for eksempel 🧾 for refusjonsbehandling. Statusene er fra 31.10.2026.

```mermaid
sequenceDiagram
    autonumber
    actor AG as Arbeidsgiver
    actor DT as Deltaker
    actor SB as Saksbehandler
    actor BE as Beslutter
    participant EB as Ekspertbistand

    rect rgba(0, 103, 197, 0.08)
    Note over AG,EB: 1. Søknad 🟢🟢🟡🟡🔴🔴
    AG->>EB: 🟡 🪪 Oppgir deltaker, sjekkes mot Aa-reg
    EB-->>AG: 🔴 📨 Informerer om at deltaker får kopi
    AG->>EB: 🟢 Sender inn søknaden
    EB->>EB: 🟡 Journalfører søknaden og oppretter sak (bare dev)
    Note over EB: Søknad innsendt · Sak OPPRETTET
    EB-->>AG: 🟢 Varsel om mottatt søknad
    EB-->>DT: 🔴 📨 Kopi av søknaden (dokdist)
    end

    rect rgba(6, 137, 58, 0.08)
    Note over AG,EB: 2. Saksbehandling 🟢🟢🟢🟢🟢🟢🟡🔴🔴
    SB->>EB: 🟢 Tildeler seg saken
    Note over EB: Sak UNDER_BEHANDLING
    EB->>EB: 🔴 ⚖️ Vilkåret om arbeidsforhold oppfylt fra Aa-reg
    SB->>EB: 🟡 ⚖️ Vurderer vilkårene (to nye vilkår mangler)
    opt Deltaker er uenig i arbeidsforholdet
        DT->>SB: Tar kontakt med Nav (utenfor løsningen)
        SB->>EB: 🟢 Overstyrer vilkåret
    end
    opt Deltaker trekker seg
        DT->>SB: Ber om at søknaden slettes (utenfor løsningen)
        SB->>EB: 🔴 🛡️ Stanser behandlingen og sletter
    end
    SB->>EB: 🟢 Sender foreløpig vedtak med brev
    Note over EB: Sak TIL_BESLUTNING
    BE->>EB: 🟢 Tildeler seg saken
    alt Retur
        BE->>EB: 🟢 Returnerer med årsak
        Note over EB: Sak UNDER_BEHANDLING, oppgave SOKNAD_RETUR
    else Vedtak
        BE->>EB: 🟢 Fatter vedtak
        Note over EB: Sak INNVILGET eller AVSLATT
    end
    end

    rect rgba(255, 145, 0, 0.10)
    Note over AG,EB: 3. Etter vedtak 🟢🟢🔴🔴🔴🔴🔴🔴🔴🔴🔴
    EB->>EB: 🔴 🗄️ Journalfører vedtaksbrevet
    EB-->>DT: 🔴 📬 Vedtaksbrevet (dokdist)
    alt Innvilget
        EB->>EB: 🟢 🏦 Bestiller hos OeBS, setter av penger
        EB->>EB: 🟢 🏦 OeBS kvitterer, bestilling AKTIV
        EB->>EB: 🔴 📝 Lager og journalfører tilsagnsbrevet
        EB-->>AG: 🔴 🔔 Varsel om innvilget, lenke til tilsagnsbrevet
        AG->>EB: 🔴 🛤️ Åpner vedtaket på ny rute
        EB-->>AG: 🔴 🛤️ Viser innvilget beløp og tilsagnsbrevet
        Note over AG,EB: Søknad godkjent
    else Avslått
        EB-->>AG: 🔴 🔔 Varsel om avslag
        AG->>EB: 🔴 🛤️ Åpner vedtaket på ny rute
        EB-->>AG: 🔴 🛤️ Viser avslaget og avslagsbrevet
        Note over AG,EB: Søknad avslatt. Saken er ferdig.
    end
    end

    rect rgba(102, 52, 176, 0.08)
    Note over AG,EB: 4. Gjennomføring og refusjonskrav 🟢🟡🟡🔴🔴
    Note over AG: Eksperten gjennomfører bistanden (utenfor løsningen)
    AG->>EB: 🟡 📤 Sender sluttrapport (bare dev)
    AG->>EB: 🟡 📤 Sender refusjonskrav med vedlegg (bare dev)
    EB->>EB: 🟢 📤 Avviser krav over innvilget beløp
    EB->>EB: 🔴 🧾 Publiserer event og lager oppgave til saksbehandler
    Note over EB: Refusjonskrav MOTTATT · Sak REFUSJON_MOTTATT
    opt Refusjonsfristen går ut uten krav
        EB->>EB: 🔴 Avslutter saken og annullerer bestillingen (avklares)
    end
    end

    rect rgba(195, 0, 0, 0.07)
    Note over AG,EB: 5. Refusjonsbehandling og utbetaling 🟡🔴🔴🔴🔴🔴🔴🔴🔴🔴
    SB->>EB: 🔴 🧾 Tildeler seg refusjonsoppgaven
    SB->>EB: 🟡 📤 Ser refusjonskravet og sluttrapporten
    alt Godkjent
        SB->>EB: 🔴 🧾 Godkjenner (krever sluttrapport)
        Note over EB: Sak REFUSJON_GODKJENT
        EB-->>AG: 🔴 📣 Varsel om godkjent refusjon
        EB->>EB: 🔴 💸 Sender faktura til OeBS
        EB->>EB: 🔴 💸 OeBS melder FULLT_BETALT
        EB->>EB: 🔴 💸 Avslutter saken
        Note over EB: Sak AVSLUTTET · bestilling OPPGJORT
    else Avslag
        SB->>EB: 🔴 🧾 Innstiller avslag
        BE->>EB: 🔴 🧾 Avslår eller returnerer
        EB-->>AG: 🔴 Varsel om avslag (avklares)
        Note over EB: Sak AVSLUTTET
    end
    end
```

🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟡🟡🟡🟡🟡🟡🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴

🟢 11 finnes · 🟡 6 delvis · 🔴 24 mangler, av 41 steg i diagrammet. OeBS er ikke egen deltaker i
diagrammet. Kall til og svar fra OeBS vises som steg i Ekspertbistand.

Deltaker får ikke varsel om refusjonen. Bare arbeidsgiver varsles når
refusjonen er godkjent.

## Tilstander for saken

Grønn finnes i `Saksstatus`. Gul finnes i enumen, men ingenting setter den.
Rød er forslag, og navnene avklares i specen for 🧾.

```mermaid
stateDiagram-v2
    classDef finnes fill:#06893a,stroke:#00341a,color:#fff
    classDef delvis fill:#ffb703,stroke:#7a5800,color:#000
    classDef mangler fill:#c30000,stroke:#5c0000,color:#fff

    [*] --> OPPRETTET: Arbeidsgiver sender inn
    OPPRETTET --> UNDER_BEHANDLING: Saksbehandler tildeler seg
    UNDER_BEHANDLING --> TIL_BESLUTNING: Saksbehandler sender foreløpig vedtak
    TIL_BESLUTNING --> UNDER_BEHANDLING: Beslutter returnerer
    TIL_BESLUTNING --> AVSLATT: Beslutter avslår
    TIL_BESLUTNING --> INNVILGET: Beslutter innvilger
    AVSLATT --> [*]
    INNVILGET --> REFUSJON_MOTTATT: Arbeidsgiver sender refusjonskrav
    INNVILGET --> AVSLUTTET: Fristen går ut uten krav (avklares)
    REFUSJON_MOTTATT --> REFUSJON_UNDER_BEHANDLING: Saksbehandler tildeler seg
    REFUSJON_UNDER_BEHANDLING --> REFUSJON_GODKJENT: Saksbehandler godkjenner
    REFUSJON_UNDER_BEHANDLING --> REFUSJON_TIL_BESLUTNING: Saksbehandler innstiller avslag
    REFUSJON_TIL_BESLUTNING --> REFUSJON_UNDER_BEHANDLING: Beslutter returnerer
    REFUSJON_TIL_BESLUTNING --> AVSLUTTET: Beslutter avslår
    REFUSJON_GODKJENT --> AVSLUTTET: OeBS melder fullt betalt
    AVSLUTTET --> [*]

    class OPPRETTET,UNDER_BEHANDLING,TIL_BESLUTNING,INNVILGET,AVSLATT finnes
    class AVSLUTTET delvis
    class REFUSJON_MOTTATT,REFUSJON_UNDER_BEHANDLING,REFUSJON_TIL_BESLUTNING,REFUSJON_GODKJENT mangler
```

## Tilstander på tvers

Hver aktør ser sin egen status. Tabellen viser hvordan de henger sammen.

| Fase | Søknad (arbeidsgiver ser) | Sak (saksbehandling) | Refusjonskrav (arbeidsgiver ser) | OeBS |
|------|---------------------------|----------------------|----------------------------------|------|
| Utkast | 🟢 `utkast` | – | – | – |
| Sendt inn | 🟢 `innsendt` | 🟢 `OPPRETTET` | – | – |
| Under behandling | 🟢 `innsendt` | 🟢 `UNDER_BEHANDLING` | – | – |
| Til beslutning | 🟢 `innsendt` | 🟢 `TIL_BESLUTNING` | – | – |
| Avslått | 🟢 `avslatt` | 🟢 `AVSLATT` | – | – |
| Innvilget | 🟢 `godkjent` | 🟢 `INNVILGET` | – | 🟡 bestilling `SENDT`, så `AKTIV` |
| Krav sendt | 🟢 `godkjent` | 🔴 `REFUSJON_MOTTATT` | 🟡 `MOTTATT` | 🟡 `AKTIV` |
| Krav under behandling | 🟢 `godkjent` | 🔴 `REFUSJON_UNDER_BEHANDLING` | 🔴 under behandling | 🟡 `AKTIV` |
| Krav godkjent | 🟢 `godkjent` | 🔴 `REFUSJON_GODKJENT` | 🔴 godkjent | 🔴 faktura `SENDT` |
| Utbetalt | 🟢 `godkjent` | 🟡 `AVSLUTTET` | 🔴 utbetalt | 🔴 faktura `FULLT_BETALT`, bestilling `OPPGJORT` |
| Krav avslått | 🟢 `godkjent` | 🟡 `AVSLUTTET` | 🔴 avslått | 🔴 bestilling `ANNULLERT`? |

## Funn

Kartleggingen viser disse hullene i tillegg til det som står i
lanseringsplanen:

1. **Refusjonskravet og sluttrapporten går ikke via event-køen.**
   `RefusjonApi` lagrer direkte i tabellene. Saksloggen, statistikken og
   oppgavelista ser dem derfor ikke. Innsendingen må publisere events og ha
   handlere, som resten av saken. Det hører til 🧾.
2. **Saken har ingen tilstander etter `INNVILGET`.** `Oppgavetype` har bare
   oppgaver for søknaden. `AVSLUTTET` finnes, men ingenting setter den. Vi må
   velge om refusjonsstatusene skal ligge i `Saksstatus`, som oppgavetypen
   utledes fra i dag, eller på refusjonskravet.
3. **Arbeidsgiver ser bare `MOTTATT` på refusjonskravet.** Visningen av
   refusjonskravet på ny rute må vise godkjent, avslått og utbetalt.
4. **Fristen går ut uten refusjonskrav.** Hva skjer med saken og
   OeBS-bestillingen? Planen sier ingenting om det.
5. **Avslag på refusjonskravet.** Planen nevner bare varsel når refusjonen
   er godkjent. Et avslag er også et vedtak, så arbeidsgiver må trolig få
   brev og varsel. Vi må også avklare om bestillingen skal annulleres.
6. **`AVSLUTTET` dekker tre utfall:** utbetalt, avslått krav og utløpt frist.
   Trenger fag eller statistikken å skille dem?
