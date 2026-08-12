# ADR: Vedtak, saksbehandling og klage i egen saksbehandlingsløsning

**Status:** Forslag — til gjennomgang med fag
**Dato:** 2026-08-10
**Gjelder:** Datamodell for behandling, foreløpig vedtak, vedtak, frister, underretning og klage

---

## Kontekst

Vi bygger vår egen saksbehandlingsløsning og skal ut av Arena. I dag er Ekspertbistand
i praksis en innsendingskanal: `SoknadStatus` går `utkast → innsendt → godkjent | avlyst`,
og statusene settes av eventer fra Arena. Vedtaket, beløpet, beslutteren og
refusjonsfristen lever i Arena — vi speiler dem i `TilsagnData`.

Når vi eier dette selv må vi fatte vedtaket, produsere brevene, holde fristene og støtte
klage. Dagens modell har verken vedtak, behandling, beslutterflyt eller frister som egne
begreper.

Dokumentet bygger på teamets prosesskartlegging, designutkastene for ny
saksbehandlerløsning, og dagpenger-teamets kartlegging av egen klageflyt.

---

## Behandlingsmotoren

Kjernen i den nye løsningen er én generisk løype som brukes for **alle** avgjørelser:

```
   ┌──────────────────────────────────────────────────────────┐
   │  1. Vilkårsvurdering                                     │
   │     veiledende sjekkliste, automatisk + manuelt satt     │
   │                        ▼                                 │
   │  2. Foreløpig vedtak   (saksbehandlers innstilling)      │
   │     utfall: innvilge | innvilge redusert | avslå         │
   │     begrunnelse tilpasses per part                       │
   │     forhåndsvisning av brev per part                     │
   │                        ▼                                 │
   │              sendt til beslutter                         │
   │                        │                                 │
   │            ┌───────────┴────────────┐                    │
   │            ▼                        ▼                    │
   │      retur m/ notat            VEDTAK FATTET             │
   │            │                        │                    │
   │            └──► tilbake til 1/2     ▼                    │
   │                              underretning per part       │
   └──────────────────────────────────────────────────────────┘
```

Den samme løypa kjøres for **søknad**, **refusjonssøknad** og **klage**.
Det er dette som gjør at klagestøtte i stor grad er gjenbruk, ikke nybygg.

### Saksforløpet

```
  Drøftingsmøte                     veileder + IA-rådgiver + arbeidsgiver
   │  protokoll på personmappe               (utenfor løsningen)
   ▼
  Søknad sendes inn (AG) ──────────► kopi til deltaker
   ▼
  [ BEHANDLINGSMOTOR ]  ──► VEDTAK: tilskudd                    ◄── KLAGE
   │                          innvilget | redusert | avslag
   │                          brev til deltaker + brev til AG
   │                          Bestilling: midler settes av
   ▼
  Tiltaket gjennomføres
   │  påminnelse når sluttdato nærmer seg
   ▼
  Sluttrapport (AG)  ──► saksbehandlers kontroll ──► godkjent
   │                       └► avvist → purring → ny innsending
   │                          (mangelbrev, ikke vedtak)
   ▼
  ── refusjonsfrist ───────────────────────────────────
   ▼
  Refusjonskrav (AG, med bilag)   kan komme etter fristen
   │   └► mangelfullt → saksbehandler låser opp → AG retter (B21)
   ▼
  [ BEHANDLINGSMOTOR ]  ──► VEDTAK: refusjon                    ◄── KLAGE
   │                          innvilget | avslag | avvist
   ▼
  Faktura + oppgjør av hele bestillingen                        (B18)
   ▼
  Utbetalt

  Sidespor:
   • AG trekker søknaden / saksbehandler trekker på vegne av AG   ◄── KLAGE?
```

---

## Hvor kan det klages — og hvor kan det ikke

Klageobjektet er **alltid et fattet vedtak**, aldri en intern arbeidstilstand.

### Klagbare avgjørelser

| Avgjørelse | Utfall som utløser klage | Klager |
|------------|--------------------------|--------|
| Tilskuddsvedtak | avslag, redusert beløp | Arbeidsgiver |
| Tilskuddsvedtak | innvilget | Deltaker |
| Refusjonsvedtak | avslag, avvist, justert beløp | Arbeidsgiver |
| Trukket søknad | hvis dette regnes som vedtak (Å2) | Begge |

### Ikke klagbare — prosessledende eller interne

| Hendelse | Hvorfor ikke |
|----------|--------------|
| **Foreløpig vedtak** | Ikke fattet, ingen rettsvirkning, ikke underrettet. En innstilling |
| **Retur fra beslutter** | Intern arbeidsflyt |
| **Vilkårsvurdering** | Veiledende arbeidsverktøy, ikke en avgjørelse |
| **Avvist sluttrapport / purring** | Anmodning om retting — mangelbrev, ikke vedtak |
| **Refusjonskrav låst opp for retting** | Samme — mangelbrev, jf. B21 |

> **Navnerisiko:** «foreløpig vedtak» er en innstilling, ikke et vedtak. Begrepet er
> innarbeidet internt, men hvis det noen gang lekker til parten — i et brevutkast, en
> loggvisning eller en innsynsbegjæring — kan det leses som at avgjørelsen er tatt.
> Verdt en runde med fag på om «innstilling» er tryggere. Uansett navn skal det ikke
> underrettes og ikke kunne påklages.

### Klagen kommer inn manuelt

Det finnes ikke noe digitalt inntak for klage i første versjon. Klagen må foreligge
skriftlig og registreres av saksbehandler med selve klagedokumentet som vedlegg (B23).

### Klagebehandling bruker samme motor

En klage blir en `Behandling` av type `klage`. Saksbehandler vurderer, innstiller på et
foreløpig vedtak med klageutfall, og beslutter fatter. Ved medhold inngår omgjøringen i
**samme innstilling**, slik at beslutter tar stilling til klagevurderingen og det nye
vedtaket i én operasjon — og kan returnere hele pakken.

Dette er nøyaktig det dagpenger-teamet ønsker seg og ikke får: de må hoppe ut av
klagesaken og opprette en ny behandling manuelt, med separat kontroll og separate brev.

**Den økonomiske siden av et medhold er derimot manuell.** Se B18 og B19.

---

## Beslutninger

### B1 — Behandling, foreløpig vedtak og vedtak er tre ulike begreper

- **Behandling** — arbeidsprosessen. Har tilstand over tid og kan gå frem og tilbake
  mellom saksbehandler og beslutter. Typer: `soknad | refusjon | klage`
- **Foreløpig vedtak** — saksbehandlers innstilling, med utfall, beløp og begrunnelse
  per part. Versjoneres ved retur
- **Vedtak** — resultatet, fattet av beslutter. Først her oppstår rettsvirkning,
  underretningsplikt og klagerett

`SoknadStatus` med `godkjent | avlyst` erstattes av dette.

### B2 — Beslutter fatter vedtaket, saksbehandler innstiller

```
under_arbeid ──foreløpig vedtak──► til_beslutning
     ▲                                    │
     └──── i_retur ◄──retur + notat ──────┤
                                          │
                                 vedtak_fattet
```

`Vedtak.fattetAv` er **beslutteren**, `innstiltAv` er saksbehandleren. Dette speiler
Arena, hvor `TilsagnData` bærer både `saksbehandler` og `beslutter`.

Beslutters retur bærer et notat. Både innstillingen og returnotatet skal bevares — de er
en del av saksdokumentasjonen, og en klager kan be om innsyn i dem.

### B3 — Foreløpig vedtak versjoneres, ikke overskrives

Retur–revider-løkka kan gå flere runder. Hver innstilling og hvert returnotat lagres som
en ny versjon. `antallReturer` faller ut gratis som kvalitetsindikator.

### B4 — Én avgjørelse, ett vedtaksbrev per part

Designutkastene viser at det foreløpige vedtaket allerede har begrunnelse og
brevforhåndsvisning **per part** — «Deltaker – tilpass begrunnelse» og «Arbeidsgiver –
tilpass begrunnelse».

Vi modellerer dette som **ett `Vedtak` med flere `Underretning`-er**, ikke som to vedtak.
Det er én realitetsavgjørelse; to vedtak ville gitt to klageobjekter for samme forhold.

### B5 — Vilkårsvurderingen er veiledende, men fryses ved vedtak

Vilkårsvurderingen er støtte til saksbehandler, ikke en låsemekanisme. Vilkår kan settes
automatisk eller manuelt, og saksbehandler kan overstyre med begrunnelse.

**Men:** når beslutter fatter vedtaket må vilkårsvurderingen fryses og knyttes uforanderlig
til vedtaket. Uten dette mister vi sporet av *hva som faktisk ble vurdert* — og det er
nettopp det en klagebehandler trenger å se.

Særlig viktig for vilkåret **«Deltaker er enig»**, som bygger på arbeidsgivers påstand om
at samtykke foreligger (B15). Klager deltaker senere med at de ikke var enige, er den
frosne vilkårsvurderingen dokumentasjonen på hva Nav la til grunn og hvorfor.

### B6 — Refusjon er et vedtak, ikke bare en attestering

Designutkastene viser refusjonskrav med samme vilkårsvurdering, samme foreløpige vedtak,
samme forhåndsvisning og samme beslutterløype som søknaden. Refusjonsvedtaket har dermed
begrunnelse, underretning og klagerett på lik linje med tilskuddsvedtaket.

### B7 — Utfallsstyrt klageflyt

| Klageutfall | Neste steg |
|-------------|-----------|
| Avvist (formkrav ikke oppfylt) | Melding om vedtak i klagesaken. Slutt |
| Opprettholdelse | Oversendelsesbrev til Nav klageinstans + orientering til klager |
| Medhold / delvis medhold | Omgjøringen inngår i samme innstilling til beslutter |

### B8 — Refusjonsfrist er en frist, ikke et vilkår

Et refusjonskrav kan sendes inn etter fristen, og det er saksbehandlers
**skjønnsvurdering** om det likevel skal realitetsbehandles.

- Innsending etter frist blokkeres ikke teknisk; kravet markeres som fremsatt etter fristen
- Vurderingen begrunnes
- Godtas ikke fristoversittelsen, avvises kravet med `avvisningsgrunn = oversittet_frist`.
  Det er et vedtak og kan påklages

### B9 — Sluttrapport: forutsetning eller ikke er uavklart

Ikke landet om sluttrapport skal være en forutsetning for refusjon. To alternativer:

1. **Utenfor vilkårene** — sluttrapport spores, men blokkerer ikke refusjon
2. **Gult vilkår** — vises som ikke oppfylt, men saksbehandler kan krysse av «fortsett
   uten» med begrunnelse

Alternativ 2 er mest i tråd med at vilkårsvurderingen skal veilede og ikke låse, og gir en
dokumentert skjønnsutøvelse i stedet for en usynlig omgåelse. Uansett valg: avvisning av
sluttrapport med purring er et **mangelbrev, ikke et vedtak**, og utløser ikke klagerett.
Se Å3.

### B10 — Tre fristtyper skal ikke blandes

| Frist | Kilde | Konsekvens ved oversittelse |
|-------|-------|-----------------------------|
| Tiltaksperiode | Tilsagnets `periode` | Skjønnsvurdering ved refusjon, jf. B11 |
| Refusjonsfrist | Tilsagnets `refusjonsfrist` | Skjønnsvurdering, jf. B8 |
| Klagefrist | Underretning om vedtak (fvl. § 29) | Klagen kan avvises |

Klagefrist og formkrav holdes i en ytelsesuavhengig modul — ren forvaltningslov, ingen
kobling til ekspertbistand-faget.

### B11 — Et fattet tilsagn endres ikke. Avvik håndteres med skjønn ved refusjon

Det bygges **ingen** flyt for å endre tiltaksperiode eller tilsagnsbeløp etter at vedtaket
er fattet. Arbeidsgiver kan ikke be om forlengelse, og saksbehandler kan ikke justere
tilsagnet.

I stedet håndteres avvik der pengene faktisk gjøres opp: **i refusjonsbehandlingen, ved
saksbehandlers og beslutters skjønn.** Har tiltaket løpt litt utover perioden, eller ble
det brukt mindre enn planlagt, vurderes det når refusjonskravet behandles. Tiltaksperioden
er dermed et skjønnsmoment i refusjonsvurderingen, ikke en hard grense.

Tre operasjoner må holdes fra hverandre, fordi de har ulik gjennomførbarhet mot OeBS:

| Operasjon | Mekanisme | Status |
|-----------|-----------|--------|
| Utbetale **mindre** enn tilsagnet | `Faktura` på lavere beløp + oppgjør av bestillingen | Støttet, og det normale |
| **Endre** tilsagnsbeløpet før utbetaling | `Annullering` + ny `Bestilling` | Ute av scope som brukerinitiert flyt |
| **Kreve tilbake** utbetalte penger | Ingen meldingstype finnes | Manuell rutine, se B24 |

**Mekanismen i rad 2 kan ikke scopes helt bort.** Et klagemedhold som øker tilsagnsbeløpet
krever nettopp `Annullering` + ny `Bestilling`. En klagerett systemet ikke kan innfri er
ikke en klagerett. Vi scoper ut inngangen, ikke kapabiliteten — se Å1.

#### Avveining: skal `delvis_innvilget` støttes på søknad? *(ikke besluttet)*

Fag har etterspurt delvis innvilgelse på søknad. Det er verdt å veie mot kompleksiteten.

**Det er `delvis_innvilget` som skaper det vanskelige klagetilfellet.** Klage på et rent
avslag er teknisk enkelt ved medhold: det finnes ingen bestilling fra før, så vi oppretter
bare én. Klage på et *redusert* beløp krever at en eksisterende bestilling annulleres og
erstattes — operasjonen Å1 stiller spørsmål ved. Droppes `delvis_innvilget`, forsvinner den
hardeste økonomiske klagesituasjonen med den.

To konsekvenser som bør være med i avveiningen:

**Uten `delvis_innvilget` finnes heller ikke «delvis medhold».** Klageutfallene reduseres i
praksis til opprettholdelse eller fullt medhold, siden ingen vedtakstilstand uttrykker «noe
av det du ba om». Det binder også en eventuell klageinstans.

**Omveien fjerner klageretten.** Alternativet uten teknisk støtte er at arbeidsgiver etter
en prat med veileder trekker søknaden og sender en ny på lavere beløp. Da finnes det *ingen
avgjørelse å klage på* — reduksjonen fremstår som arbeidsgivers eget valg, selv om den i
realiteten er Navs. Det er sannsynligvis en vesentlig del av grunnen til at fag etterspør
funksjonen, og bør ikke leses som ren bekvemmelighet.

Modellen bærer `delvis_innvilget` på tilskuddsvedtak. Å ta det ut senere er en innsnevring;
å legge det til senere treffer vedtak, brev, økonomiflyt og klage samtidig.

### B12 — Trukket søknad avsluttes eksplisitt og frigjør avsatte midler

Både arbeidsgiver og saksbehandler (på vegne av arbeidsgiver) kan trekke søknaden. Begge
tilfeller varsler både arbeidsgiver og deltaker. Om trekking er et enkeltvedtak er
uavklart, se Å2.

### B13 — Underretning registreres per part

Underretning er en liste av hendelser, ikke ett felt — én per part, med eget dokument og
egen kanal. I praksis sendes de **samtidig og med samme klagefrist** (B22), men de
registreres hver for seg fordi dokument og kanal er ulike, og fordi sporbarheten på hvem
som ble underrettet når må være entydig.

### B14 — Innsigelse før vedtak skilles fra klage etter vedtak

Deltaker får kopi av søknaden ved innsending, altså før vedtaket fattes.

- **Innsigelse før vedtak** — en opplysning i saken som saksbehandler tar med i
  vurderingen, typisk mot vilkåret «Deltaker er enig». Ingen formkrav, ingen frist, ingen
  beslutterrunde
- **Klage etter vedtak** — formell klageflyt

Deltaker deltar ikke i drøftingsmøtet slik kartleggingen viser det. Kopi av søknaden er
dermed deltakers første berøring med saken, og innsigelsesvinduet er reelt.

### B15 — Ikke samtykke eller trepartssignering

Besluttet med fag. Deltaker varsles i stedet om mottatt søknad, med kopi. Arbeidsgiver
informeres i søknaden om at deltaker får kopi.

Samtykket lever videre som **vilkåret «Deltaker er enig»**, basert på arbeidsgivers
påstand. Vi har dokumentasjon på *varsling* og på *hva Nav la til grunn*, ikke på
*involvering*. `KopiAvSoknadSendtDeltaker` og den frosne vilkårsvurderingen (B5) er
bevisgrunnlaget.

### B16 — Klage registreres manuelt av saksbehandler

I første omgang bygges **ikke** noe grensesnitt der arbeidsgiver eller deltaker kan sende
klage digitalt. Saksbehandler registrerer klagen i saksbehandlingsløsningen. Samme gjelder
innsigelser før vedtak (B14).

Dette flytter noen krav over på andre deler av løsningen:

**Klageveiledningen må ligge i vedtaksbrevet.** Uten digital kanal er brevet eneste sted
parten får vite at de kan klage. Brevet må oppfylle fvl. § 27 tredje ledd: klagerett,
klagefrist, klageinstans, og hvor klagen sendes. Gjelder begge brevmalene, for både
tilskudds- og refusjonsvedtak.

**Mottatt og registrert er to ulike datoer.** Klagefristen måles mot da Nav faktisk mottok
klagen, ikke da den ble tastet inn. Saksbehandler må kunne sette en mottaksdato bakover i
tid, og begge datoene lagres.

**Kvittering går ut selv om inntaket er manuelt.** Når klagen er registrert bør parten få
en beskjed om at den er mottatt.

### B17 — Saksbehandler og beslutter må være ulike personer **per behandling**

Regelen gjelder per behandling, ikke per sak. Det stilles ingen krav om at saksbehandler
eller beslutter i en klagesak må være andre enn de som behandlet det opprinnelige vedtaket.

> Kari innstiller på søknadsvedtaket, Ola fatter det. Seks måneder senere kommer en klage.
> Ola kan innstille på klagen, og Kari kan fatte den. Hver behandling har to par øyne.

En Nav-ansatt kan inneha begge rollene, så dette kan ikke håndheves gjennom rolletildeling.
Håndhevingen er på handlingsnivå og må ligge i backend:

- `ForelopigVedtak.innstiltAv` registreres ved innsending til beslutter
- Beslutt-handlingen avvises hvis `innloggetBruker` har innstilt på **noen versjon** av
  samme behandling

### B18 — Økonomi via tiltaksøkonomi. Vi holder ikke av penger etter refusjon

[Tiltaksøkonomi](https://github.com/navikt/mulighetsrommet/tree/main/mulighetsrommet-tiltaksokonomi)
er en ACL mot OeBS PO/AP. Vi produserer Kafka-meldinger og lytter på statustopics; vi
snakker ikke med OeBS direkte.

| Vår hendelse | Melding til tiltaksøkonomi |
|--------------|----------------------------|
| Tilskuddsvedtak innvilget | `Bestilling` — holder av midlene |
| Søknad trukket, avslag etter tilsagn | `Annullering` |
| Refusjonsvedtak innvilget | `Faktura` + `GjorOppBestilling` for eventuelt restbeløp |
| Refusjonsfrist passert uten krav, refusjonskrav avslått | `GjorOppBestilling` |
| Klagemedhold som krever penger | Ny `Bestilling` — manuell håndtering, se B19 |

**Bestillingen gjøres opp i sin helhet ved refusjonsvedtaket.** Vi holder ikke midler
avsatt i påvente av en eventuell klage. Er kravet lavere enn tilsagnet, utbetales det
dokumenterte beløpet og resten gjøres opp umiddelbart.

Konsekvensen er bevisst: **et klagemedhold etter utbetaling krever en ny bestilling og
behandles manuelt.** Vi bytter bort automatikk i et sjeldent tilfelle mot å slippe å holde
budsjettmidler bundet i uker etter at saken reelt er ferdig. En delvis innvilgelse på
refusjon etterlater dermed heller ikke noe utestående delbeløp arbeidsgiver kan gjøre krav
på — bestillingen er lukket.

**Annullér-og-opprett er OeBS-modellen.** Det finnes ingen «endre bestilling». Skal et
fattet tilsagn endres — i praksis kun etter klagemedhold — må det gjøres ved å annullere og
opprette på nytt, eller ved en ny bestilling dersom den gamle er gjort opp.

#### Bestillingens livsløp

```
 søknadsvedtak                                    refusjonsvedtak
      │                                                  │
      ▼                                                  ▼
  Bestilling ──────── midler bundet, ikke trukket ──── Faktura + GjorOppBestilling
                                                              │
                                                       bestillingen er lukket
                                                       senere medhold = ny bestilling,
                                                       manuelt
```

**Klage på tilskuddsvedtaket treffer nesten alltid en ubetalt bestilling.** Klagefristen er
seks uker fra underretning, mens refusjon først kommer etter at tiltaket er gjennomført. En
tilskuddsklage behandles derfor i praksis mens midlene er bundet men ikke trukket — det
gunstigste tidspunktet for `Annullering` + ny `Bestilling`.

**Uavklart: datoer på bestillingen.** Aksepterer saksbehandler et refusjonskrav etter
refusjonsfristen, eller utenfor tiltaksperioden, kommer fakturaen mot en bestilling hvis
datoer ikke lenger stemmer. Om OeBS bryr seg om dette, og om det må håndteres med nye
datoer eller en ny bestilling, må avklares med team Valp — og det trengs en beskrevet
rutine for saksbehandler. Se Å5.

### B19 — Vedtak og økonomistatus er to ulike tilstander

Kvitteringer fra OeBS kommer asynkront. Et vedtak kan være fattet og underrettet mens
bestillingen fortsatt er ubekreftet — eller har feilet. Feilkoder som
`PO_PDOI_INVALID_PROJ_INFO` krever endringer hos OeBS før de kan løses.

Vi kan ikke rulle tilbake et fattet og underrettet vedtak fordi en bestilling feilet.
`Tilsagn` og `Refusjonskrav` må derfor bære en **egen økonomistatus**, og det trengs en
**avstemmingsliste** for saker der de to er i utakt. Uten den blir feilede bestillinger
usynlige til noen oppdager at pengene aldri ble avsatt.

Samme liste er stedet der **klagemedhold som krever penger** havner, siden de håndteres
manuelt (B18). En saksbehandler må kunne se «dette vedtaket er omgjort, økonomien er ikke
gjennomført» og gjøre noe med det.

Tiltaksøkonomi garanterer rekkefølge og avhengigheter. Duplikathåndtering
(`DUPLICATE INVOICE NUMBER`) passer med `IdempotencyGuard` vi allerede har.

### B20 — Arbeidsgiver oppgir krevd beløp, validert mot tilsagnet

Arbeidsgiver oppgir `krevdBelop` i skjemaet. Beløpet **valideres mot et tak lik tilsagnet**
— det er ikke mulig å kreve mer enn det som er innvilget. Bilagene dokumenterer kravet, men
beløpet avledes ikke automatisk fra dem; det er saksbehandlers oppgave å kontrollere at
bilagene faktisk understøtter beløpet.

At kravet er lavere enn tilsagnet er **ikke** en delvis innvilgelse. Arbeidsgiver har krevd
det de har krevd, og får det innfridd. Restbeløpet gjøres opp mot bestillingen (B18).
`delvis_innvilget` som utfall reserveres for tilskuddsvedtaket.

**Åpent: kan saksbehandler justere krevd beløp?** Det er mulig vi bør tillate at
saksbehandler setter et lavere beløp enn arbeidsgiver har krevd, framfor å låse opp kravet
og be om retting. Fordi bestillingen uansett gjøres opp i sin helhet (B18), er dette
økonomisk uproblematisk — det er ingen midler som blir hengende.

Men det er en avgjørelse i disfavør av arbeidsgiver og dermed klagbart på beløpet.
Merk at dette står i spenning med avgrensningen over om at `delvis_innvilget` reserveres
for tilskuddsvedtak: en nedjustering *er* en delvis innvilgelse, uansett hva feltet heter.
Se Å4.

Uansett utfall gjelder B18: en klage på refusjonsbeløpet som fører fram må håndteres med ny
bestilling og manuelt arbeid.

### B21 — Saksbehandler låser opp refusjonskravet for retting

Et innsendt refusjonskrav er **låst**. Er bilagene mangelfulle, beløpet feil, eller noe
annet krever oppdatering, **låser saksbehandler opp kravet** slik at arbeidsgiver kan endre
det — erstatte eller supplere bilag, justere beløp — og sende inn på nytt.

```
  Refusjonskrav innsendt (låst)
        ▼
  saksbehandlers kontroll
        ├── i orden ──────────────► behandlingsmotoren → vedtak
        └── mangelfullt
              ▼
        låst opp med begrunnelse ──► Oppgave til AG (med svarfrist)
              ▼                         │
        AG retter og sender inn  ◄──────┘  purring ved manglende svar
              ▼
        låst igjen → tilbake til kontroll
```

Opplåsing framfor «send inn på nytt» beholder det arbeidsgiver allerede har fylt ut, og
holder saken samlet på ett krav i stedet for å spre den over flere innsendinger.

**Dette er et mangelbrev, ikke et vedtak.** Det utløser ikke klagerett, på linje med avvist
sluttrapport.

**Historikken bevares.** Hvilke bilag som lå ved opprinnelig, hva som ble bedt rettet, og
hva som kom tilbake, må kunne rekonstrueres. En senere klage kan gjelde nettopp dette.

**Svarer arbeidsgiver aldri, ender det i avslag.** Etter purring uten respons avslås
kravet. Det er et vedtak med klagerett, og bestillingen gjøres opp.

### B22 — Begge parter har klagerett, med samtidig underretning og felles frist

Både arbeidsgiver og deltaker er parter med klagerett på tilskuddsvedtaket.

Underretningene sendes **samtidig**, og klagefristen er dermed **den samme for begge**.
Modellen holder fristen per underretning (B13), men i praksis er de like — det er ingen
skjev fristberegning å håndtere.

Deltaker underrettes ikke om refusjonsvedtaket (B26), og har dermed ingen klagerett der.

### B23 — Klagen må foreligge skriftlig og registreres med vedlegg

Fvl. § 32 krever skriftlig klage. Om den kommer på e-post eller i fysisk post er
uinteressant for oss — kravet er at det finnes et skriftlig klagedokument.

Saksbehandler registrerer klagen manuelt, og **selve klagedokumentet legges ved** som
vedlegg på saken. Registreringen uten vedlegget er ikke tilstrekkelig; det er dokumentet
som er klagen.

Ringer noen for å klage, blir de bedt om å sende det skriftlig. Telefonhenvendelsen bør
likevel loggføres på saken, fordi den kan være relevant for å vurdere en senere
fristoversittelse (fvl. § 31).

### B24 — Tilbakekreving er en manuell rutine som registreres i saken

Tiltaksøkonomi har ingen meldingstype for tilbakekreving. Ved feilutbetaling håndteres
tilbakekrevingen etter en **egen servicerutine utenfor systemet**.

Vi bygger ikke selve prosessen, men gjør det **mulig å registrere at tilbakekreving er
iverksatt og gjennomført** på saken, slik at saksbildet ikke lyver om hva som faktisk er
utbetalt. Rutinen beskrives separat.

### B25 — Kontonummer hentes fra Sokos kontoregister

Utbetaling skjer til virksomhetens **kontonummer for refusjoner**. Det oppgis ikke manuelt
— verken av arbeidsgiver eller saksbehandler.

Ved innsending av refusjonskrav validerer vi at kontonummer for refusjoner er registrert.
Mangler det, blokkeres innsendingen og arbeidsgiver henvises til å registrere det i Sokos
kontoregister.

### B26 — Deltaker får kopi av søknaden, men ikke refusjonsvedtaket

Deltaker skal få tilgang til en kopi av søknaden, fortrinnsvis via Min side. De tekniske
detaljene avgjøres ved implementasjon.

Deltaker underrettes **ikke** om refusjonsvedtaket. Refusjonen er et økonomisk oppgjør
mellom Nav og arbeidsgiver som ikke berører deltakers rettsstilling.

---

## Datamodell

```
Sak
  id, soknadId, virksomhetsnummer, deltaker

Behandling
  id, sakId
  type            : soknad | refusjon | klage
  status          : under_arbeid | til_beslutning | i_retur
                  | vedtak_fattet | trukket | henlagt
  gjelderKlage    : KlageId?
  antallReturer   : int

Vilkaarsvurdering                        // én per behandling, fryses ved vedtak (B5)
  behandlingId
  frosset         : bool
  vilkaar[]       : {
      kode        : arbeidsforhold | deltaker_enig | behov_tilrettelegging
                  | sykefravaershistorikk | ekspertkompetanse | sluttrapport | ...
      status      : oppfylt | ikke_oppfylt | ikke_vurdert | fortsett_uten
      kilde       : automatisk | manuell
      begrunnelse : tekst?
  }

ForelopigVedtak                          // innstilling, versjonert (B3)
  id, behandlingId, versjon
  utfall          : innvilge | innvilge_redusert | avslaa
                  | (klage) avvis | oppretthold | medhold | delvis_medhold
  innvilgetBelop
  begrunnelsePerPart : { deltaker: tekst, arbeidsgiver: tekst }
  innstiltAv      : NAVident
  sendtTidspunkt
  returNotat      : tekst?
  returnertAv     : NAVident?

Vedtak
  id, behandlingId, sakId
  type            : tilskudd | refusjon
  utfall          : innvilget | delvis_innvilget | avslag | avvist
                  //  delvis_innvilget kun på type = tilskudd, jf. B20
  avvisningsgrunn : oversittet_frist | ...
  omsoktBelop / innvilgetBelop
  bygdePaaForelopigVedtak : ForelopigVedtakId
  innstiltAv      : NAVident            // saksbehandler
  fattetAv        : NAVident            // beslutter
  fattetTidspunkt
  erstatterVedtak : VedtakId?
  annullert       : bool
  foranledigetAv  : KlageId?

Underretning                             // én per part (B13), sendes samtidig (B22)
  vedtakId, part, kanal, dokumentId, tidspunkt
  klagefrist      : dato

Tilsagn
  vedtakId, periode, refusjonsfrist, tilskuddsbrevId, avsattBelop
  bestillingsnummer  : string?
  okonomistatus      : ikke_sendt | sendt | bekreftet | feilet
                     | annullert | gjort_opp                   // jf. B19
  okonomifeil        : { kode, melding, tidspunkt }?

Sluttrapport
  id, sakId, tilsagnVedtakId
  innsendt, innhold, bilag[]
  status          : mottatt | godkjent | avvist | venter_purring
  avvisningsbegrunnelse : tekst?

Refusjonskrav
  id, sakId, tilsagnVedtakId, behandlingId
  status             : utkast | innsendt_laast | apnet_for_retting
                     | til_behandling | avgjort               // jf. B21
  innsendt           : dato
  krevdBelop         : oppgitt av AG, validert mot tilsagn    // jf. B20
  bilag[]
  fremsattEtterFrist : bool
  fristvurdering     : tekst?
  opplaasinger[]     : { begrunnelse, svarfrist, aapnetAv, tidspunkt,
                         bilagFoer[], innsendtPaaNytt? }      // historikk, B21
  fakturanummer      : string?
  okonomistatus      : ikke_sendt | sendt | bekreftet | feilet | utbetalt
  tilbakekreving     : { registrertAv, tidspunkt, belop, notat }?   // B24

Klage
  id
  gjelderVedtak   : VedtakId             // tilskudd eller refusjon
  klager          : deltaker | arbeidsgiver | fullmektig
  mottatt         : dato                 // da Nav mottok klagen — måler fristen
  registrert      : tidspunkt
  registrertAv    : NAVident
  dokumentId      : selve klagedokumentet, påkrevd            // jf. B23
  frist / innenFrist / grunnlag
  formkrav        : { skriftlig, signert, angirEndring, rettsligKlageinteresse }
  behandlingId    : BehandlingId

Innsigelse                               // deltaker, før vedtak — ikke en klage
  id, soknadId, mottatt, registrert, registrertAv, innhold
```

---

## Eventer

Legges på den eksisterende event-loggen, jf.
`adr/0001-asynkron-prosessering-med-event-ko.md` og `event/Events.kt`.

**Søknad, innsigelse og underretning**
```
SoknadInnsendt                                                      (finnes)
KopiAvSoknadSendtDeltaker(soknadId, mottaker, kanal, tidspunkt, dokumentId)
InnsigelseMottatt(soknadId, innhold, mottatt, registrertAv)
SoknadTrukket(soknadId, trukketAv, begrunnelse)
PartUnderrettet(vedtakId, part, kanal, tidspunkt, dokumentId, klagefrist)
```

**Behandlingsmotoren** — identisk for søknad, refusjon og klage
```
BehandlingOpprettet(behandlingId, sakId, type)
VilkaarVurdert(behandlingId, kode, status, kilde, begrunnelse?)
ForelopigVedtakSendtTilBeslutter(forelopigVedtakId, behandlingId, versjon,
                                 utfall, innvilgetBelop, begrunnelsePerPart, innstiltAv)
ForelopigVedtakReturnert(forelopigVedtakId, returNotat, returnertAv)
VilkaarsvurderingFrosset(behandlingId, vedtakId)
VedtakFattet(vedtakId, behandlingId, type, utfall, avvisningsgrunn?,
             innvilgetBelop, bygdePaaForelopigVedtak,
             innstiltAv, fattetAv, erstatterVedtak?, foranledigetAv?)
VedtakAnnullert(vedtakId, erstattetAv, aarsak)
```

**Gjennomføring, sluttrapport og refusjon**
```
TilskuddsbrevGenerert(vedtakId, dokumentId, periode, refusjonsfrist)
SluttrapportPaaminnelseSendt(sakId, tidspunkt)
SluttrapportMottatt(sluttrapportId, sakId, bilag)
SluttrapportAvvist(sluttrapportId, begrunnelse)
SluttrapportPurringSendt(sluttrapportId, tidspunkt)
SluttrapportGodkjent(sluttrapportId, godkjentAv)
RefusjonsfristPassert(tilsagnVedtakId, tidspunkt)
RefusjonskravMottatt(refusjonskravId, krevdBelop, bilag, fremsattEtterFrist)
RefusjonskravAapnetForRetting(refusjonskravId, begrunnelse, svarfrist, aapnetAv)
RefusjonskravPurringSendt(refusjonskravId, tidspunkt)
RefusjonskravSendtInnPaaNytt(refusjonskravId, krevdBelop, bilag, tidspunkt)
FristoversittelseVurdert(refusjonskravId, godtatt: bool, begrunnelse)
```

**Økonomi mot tiltaksøkonomi** (B18, B19)
```
BestillingSendt(vedtakId, bestillingsnummer, belop, periode)
BestillingBekreftet(vedtakId, bestillingsnummer, tidspunkt)
BestillingFeilet(vedtakId, feilkode, melding, tidspunkt)
AnnulleringSendt(vedtakId, bestillingsnummer, aarsak)
FakturaSendt(refusjonskravId, fakturanummer, bestillingsnummer, belop)
FakturaBekreftet(refusjonskravId, fakturanummer, tidspunkt)
FakturaFeilet(refusjonskravId, feilkode, melding, tidspunkt)
BestillingGjortOpp(vedtakId, restbelop, aarsak)
TilbakekrevingRegistrert(refusjonskravId, belop, registrertAv, notat)   // B24
```

**Klage**
```
KlageveiledningGitt(sakId, kanal: telefon, tidspunkt, notat)
KlageRegistrert(klageId, gjelderVedtak, klager, grunnlag,
                mottatt, registrert, registrertAv, dokumentId, frist)
KlageMottakBekreftet(klageId, part, kanal, tidspunkt)
KlageFormkravVurdert(klageId, formkrav, innenFrist)
KlageKnyttetTilBehandling(klageId, behandlingId)
VedtakOmgjort(nyttVedtakId, erstatterVedtakId, foranledigetAv: klageId)
KlageOversendtKlageinstans(klageId, oversendelsesbrevId, tidspunkt)
```

---

## Notifikasjonsplattform og Min side

`ProdusentApiKlient` støtter i dag `opprettNySak`, `opprettNyBeskjed`, `nyStatusSak` og
`hardDeleteSak`. **Oppgave er ikke implementert** — sluttrapport-, refusjons- og
rettingsoppgaver er nytt arbeid mot produsent-API-et.

| Hendelse | Arbeidsgiver | Deltaker (Min side) |
|----------|--------------|---------------------|
| Søknad innsendt | Sak opprettes, beskjed | Beskjed med kopi av søknaden |
| Søknad trukket | Beskjed | Beskjed |
| Tilskuddsvedtak | Sakstatus + beskjed, brev | Brev + beskjed, samtidig (B22) |
| Sluttdato nærmer seg | **Oppgave**: send sluttrapport | — |
| Sluttrapport avvist | **Oppgave**: send på nytt, purring | — |
| Refusjon skal søkes | **Oppgave** med utløp = refusjonsfrist | — |
| Refusjonsfrist passert | Oppgaven utgår | — |
| Refusjonskrav mottatt | Beskjed + sakstatusoppdatering | — |
| Kravet låst opp for retting | **Oppgave** med svarfrist, purring | — |
| Refusjonsvedtak | Sakstatus + beskjed, brev | — (B26) |
| Utbetalt | Beskjed | — |
| Klage registrert | Beskjed: «vi har mottatt klagen din» | Ved klage fra deltaker |
| Klage avgjort | Sakstatus + beskjed, brev | Ved klage fra deltaker |

Når refusjonskravet kommer etter fristen er oppgaven allerede utgått. Da er beskjed +
sakstatusoppdatering eneste kvittering. Teksten bør være tydelig på at kravet er registrert.

Manglende kontonummer for refusjoner blokkerer innsending (B25) — meldingen må peke
arbeidsgiver til Sokos kontoregister, ikke bare si at noe mangler.

---

## Saksbehandlerflaten

Kartleggingen etterlyser «oversikt over pågående saker, utestående refusjonskrav, filtrer
etc». Minimum: saker gruppert per sak (ikke per oppgave), med synlig kobling mellom vedtak,
klage og omgjøring, og filtrering på utestående refusjonskrav og passerte frister.

I tillegg trengs **avstemmingslista** fra B19: saker der vedtak og økonomistatus er i utakt,
inkludert klagemedhold som venter på manuell økonomihåndtering.

---

## Samtidighet

**Klage på tilskuddsvedtaket mens gjennomføringen går.** Klagefristen kan løpe inn i
tiltaksperioden. Gjennomføring og refusjonsløp settes ikke på vent. Behandles klagen mens
bestillingen er ubetalt, kan omgjøringen gjøres med `Annullering` + ny `Bestilling`.

**Klage etter at refusjonen er utbetalt.** Bestillingen er da gjort opp i sin helhet (B18).
Et medhold som krever penger må realiseres med en **ny bestilling**, opprettet manuelt, og
saken føres på avstemmingslista til det er gjort.

**Vedtak fattet, bestilling feilet.** Vedtaket står og parten er underrettet, men pengene er
ikke avsatt. Havner på avstemmingslista og løses operativt — ikke ved å omgjøre vedtaket.

**Klage på et annullert vedtak.** Skal ikke være mulig; klagen retter seg mot gjeldende
vedtak, det siste ikke-annullerte i `erstatterVedtak`-kjeden.

**Begge parter klager på samme tilskuddsvedtak.** Modellen tillater flere `Klage` mot samme
`VedtakId`. Siden fristen er felles (B22), vil de normalt komme i samme vindu og bør ses i
sammenheng.

---

## Forvaltningsrettslige premisser

- **Klagefrist** løper fra underretning om vedtaket (fvl. § 29). Underretning skjer
  samtidig til begge parter, så fristen er felles (B22)
- **Klagen må være skriftlig** (fvl. § 32), jf. B23
- **Medhold i klage fra deltaker** på et innvilget tilskuddsvedtak vil i praksis være
  omgjøring til ugunst for arbeidsgiver (fvl. § 35), med egne vilkår og krav om
  forhåndsvarsel
- **Foreløpig vedtak** er en intern innstilling uten rettsvirkning, ikke et enkeltvedtak
  etter fvl. § 2 b
- **Opprettholdelse** medfører oversendelse til Nav klageinstans

---

## Konsekvenser

**Positive**

- Klagebehandling er gjenbruk av behandlingsmotoren, ikke en parallell løsning
- Klage, medhold og omgjøring i én innstilling, én beslutterrunde, ett brevsett
- Frosset vilkårsvurdering gir klagebehandler beslutningsgrunnlaget som faktisk forelå
- Ingen budsjettmidler holdes bundet i påvente av klagefrister
- Antall returer fra beslutter blir en målbar kvalitetsindikator

**Kostnader og ny kompleksitet**

- Migrering fra `SoknadStatus` til behandling + foreløpig vedtak + vedtak
- Versjonering og frysing av vilkårsvurdering
- Fristmotor: refusjonsfrist med oppgaveutløp, sluttrapportpåminnelse, klagefrist
- Oppgave-støtte mot notifikasjonsplattformen: sluttrapport, refusjon, retting
- Opplåsingsmekanikk på refusjonskrav med historikk (B21)
- Kafka-integrasjon mot tiltaksøkonomi, med økonomistatus og avstemmingsliste (B19)
- Oppslag mot Sokos kontoregister med blokkerende validering (B25)
- Manuell håndtering av klagemedhold som krever penger (B18)
- Nye dokgen-maler: vedtaksbrev per part for tilskudd og refusjon, mangelbrev,
  klagevedtak, oversendelsesbrev
- Integrasjon mot Nav klageinstans

---

## Åpne spørsmål

**Å1 — Kan `Annullering` + ny `Bestilling` gjøres på en bekreftet, ubetalt bestilling?**
Antas mulig. Må avsjekkes med team Valp / OeBS om det kan gi feilsituasjoner. Dette er
veien for klagemedhold før utbetaling; etter utbetaling er svaret allerede gitt — ny
bestilling, manuelt (B18).

**Å2 — Er trukket søknad et vedtak?** Har betydning for om det kreves begrunnelse og
underretning med klagerett, særlig når saksbehandler trekker på vegne av arbeidsgiver.
*Hanna følger opp med Sadia.*

**Å3 — Sluttrapport som vilkår.** Utenfor vilkårene, eller gult vilkår med «fortsett uten»
og begrunnelse? Avklares etter hvert. Se B9.

**Å4 — Kan saksbehandler justere krevd beløp på et refusjonskrav?** Alternativet er å låse
opp kravet og be arbeidsgiver rette det selv (B21). Justering er økonomisk uproblematisk
siden bestillingen uansett gjøres opp, men det er en avgjørelse i disfavør av arbeidsgiver
og dermed klagbar på beløpet — og den er reelt en delvis innvilgelse, uansett hva utfallet
heter. Se B20.

**Å5 — Datoer på bestillingen ved skjønnsmessig aksept.** Godtar saksbehandler et krav
etter refusjonsfristen eller utenfor tiltaksperioden, kommer fakturaen mot en bestilling
med datoer som ikke lenger stemmer. Bryr OeBS seg? Må det sendes nye datoer, eller en ny
bestilling? Avklares med team Valp, og det trengs en beskrevet rutine for saksbehandler.
Se B18.

**Å6 — Tilsagnsår og årsskifte.** OeBS avviser bestillinger for langt frem i tid
(`PO_PDOI_INVALID_PROJ_INFO`). Hvilke guards trenger vi? Avklares med team Valp.

**Å7 — Klageinstans-integrasjon.** Tar Nav klageinstans imot via Kabal for vår tiltakstype,
under hvilket tema og i hvilket format? Foreløpig ukjent. Ikke sikkert det må være avklart
før prodsetting, men det må være avklart før første opprettholdelse skal oversendes.

**Å8 — Skal `delvis_innvilget` støttes på tilskuddsvedtaket?** Se avveiningen i B11.

---

## Avgrensning: økonomioversikt

Beslutter — eller en egen økonomirolle — skal kunne se et regnskap per år, avdeling og
sektor. Rolletildeling og tilgangsstyring på enhet og sak er uavklart. **Dette planlegges i
egen sesjon og får sin egen ADR.**

To ting fra B18 og B19 bør tas med dit:

**Oversikten må skille mellom tre tall.** *Disponert* (vedtatt hos oss), *bekreftet*
(kvittert av OeBS) og *utbetalt*. De divergerer i det asynkrone vinduet, og permanent
dersom en melding feiler.

**Vi er ikke fasit.** OeBS er den autoritative økonomikilden. Vår oversikt er en projeksjon
over egne vedtak og mottatte kvitteringer, og bør presenteres som det.
Projeksjonsmekanikken finnes i `event/projections/`.

---

## Referanser

- `adr/0001-asynkron-prosessering-med-event-ko.md` — asynkron prosessering og event-kø
- `backend/src/main/kotlin/no/nav/ekspertbistand/event/Events.kt`
- `backend/src/main/kotlin/no/nav/ekspertbistand/soknad/Api.kt` — dagens `SoknadStatus`
- `backend/src/main/kotlin/no/nav/ekspertbistand/arena/ArenaTilsagnsbrevProcessor.kt`
- `backend/src/main/kotlin/no/nav/ekspertbistand/notifikasjon/ProdusentApiKlient.kt`
- `specifications/saksbehandler_routes_v1.md` — roller, inkl. `Beslutter`
- [mulighetsrommet-tiltaksokonomi](https://github.com/navikt/mulighetsrommet/tree/main/mulighetsrommet-tiltaksokonomi)
  — ACL mot OeBS PO/AP (team Valp)
- Designutkast ny saksbehandlerløsning (Figma)
- Teamets prosesskartlegging
- Dagpenger-teamets kartlegging av klageflyt
