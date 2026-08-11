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
   │                          midler settes av
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
  Refusjonssøknad (AG, med bilag/faktura)   kan komme etter fristen
   ▼
  [ BEHANDLINGSMOTOR ]  ──► VEDTAK: refusjon                    ◄── KLAGE
   │                          innvilget | avslag | avvist   (B20)
   ▼
  Utbetaling via tiltaksøkonomi → OeBS ──► utbetalt

  Sidespor:
   • AG trekker søknaden / saksbehandler trekker på vegne av AG   ◄── KLAGE?
```

---

## Hvor kan det klages — og hvor kan det ikke

Dette er dokumentets hovedpoeng. Klageobjektet er **alltid et fattet vedtak**, aldri en
intern arbeidstilstand.

### Klagbare avgjørelser

| Avgjørelse | Utfall som utløser klage | Klager |
|------------|--------------------------|--------|
| Tilskuddsvedtak | avslag, redusert beløp | AG (se Å1) |
| Tilskuddsvedtak | innvilget | Deltaker |
| Refusjonsvedtak | avslag | AG |
| Refusjonsvedtak | avvist — oversittet frist | AG |
| Trukket søknad | hvis dette regnes som vedtak (Å4) | AG, deltaker |

### Ikke klagbare — prosessledende eller interne

| Hendelse | Hvorfor ikke |
|----------|--------------|
| **Foreløpig vedtak** | Ikke fattet, ingen rettsvirkning, ikke underrettet. En innstilling |
| **Retur fra beslutter** | Intern arbeidsflyt |
| **Vilkårsvurdering** | Veiledende arbeidsverktøy, ikke en avgjørelse |
| **Avvist sluttrapport / purring** | Anmodning om retting — mangelbrev, ikke vedtak |
| **Refusjonskrav returnert til retting** | Samme — mangelbrev, jf. B21 |
| **Etterspørsel om mer dokumentasjon** | Saksforberedelse (fvl. § 17) |

> **Navnerisiko:** «foreløpig vedtak» er en innstilling, ikke et vedtak. Begrepet er
> innarbeidet internt, men hvis det noen gang lekker til parten — i et brevutkast, en
> loggvisning eller en innsynsbegjæring — kan det leses som at avgjørelsen er tatt.
> Verdt en runde med fag på om «innstilling» er tryggere. Uansett navn skal det ikke
> underrettes og ikke kunne påklages.

### Klagen kommer inn manuelt

Det finnes ikke noe digitalt inntak for klage i første versjon. Klagen kommer på e-post og
registreres manuelt av saksbehandler. Den som ringer blir bedt om å sende e-post. Se B16
for hva dette flytter av krav over på vedtaksbrevet og på datamodellen.

### Klagebehandling bruker samme motor

En klage blir en `Behandling` av type `klage`. Saksbehandler vurderer, innstiller på et
foreløpig vedtak med klageutfall, og beslutter fatter. Ved medhold inngår omgjøringen i
**samme innstilling**, slik at beslutter tar stilling til klagevurderingen og det nye
vedtaket i én operasjon — og kan returnere hele pakken.

Dette er nøyaktig det dagpenger-teamet ønsker seg og ikke får: de må hoppe ut av
klagesaken og opprette en ny behandling manuelt, med separat kontroll og separate brev.

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
en ny versjon. `antallReturer` faller ut gratis som kvalitetsindikator, og
saksdokumentasjonen blir komplett.

### B4 — Én avgjørelse, ett vedtaksbrev per part

Designutkastene viser at det foreløpige vedtaket allerede har begrunnelse og
brevforhåndsvisning **per part** — «Deltaker – tilpass begrunnelse» og «Arbeidsgiver –
tilpass begrunnelse».

Vi modellerer dette som **ett `Vedtak` med flere `Underretning`-er**, ikke som to vedtak.
Det er én realitetsavgjørelse; to vedtak ville gitt to klageobjekter for samme forhold.

Klagefrist og klagerett håndteres per underretning. Det er dette som gjør det mulig at
partene har ulik klagerett mot samme vedtak — se Å1.

### B5 — Vilkårsvurderingen er veiledende, men fryses ved vedtak

Vilkårsvurderingen er ment som støtte til saksbehandler, ikke som en låsemekanisme.
Vilkår kan settes automatisk eller manuelt, og saksbehandler kan overstyre med begrunnelse.

**Men:** i det øyeblikket beslutter fatter vedtaket må vilkårsvurderingen fryses og
knyttes uforanderlig til vedtaket. Uten dette mister vi sporet av *hva som faktisk ble
vurdert* — og det er nettopp det en klagebehandler trenger å se.

Dette er særlig viktig for vilkåret **«Deltaker er enig»**, som bygger på arbeidsgivers
påstand om at samtykke foreligger (jf. B15). Hvis deltaker senere klager med at de ikke
var enige, er den frosne vilkårsvurderingen dokumentasjonen på hva Nav la til grunn og
hvorfor.

### B6 — Refusjon er et vedtak, ikke bare en attestering

Designutkastene viser refusjonskrav med samme vilkårsvurdering, samme foreløpige vedtak
med `innvilge | innvilge med redusert beløp | avslå`, samme forhåndsvisning og samme
beslutterløype som søknaden.

Refusjonsvedtaket har dermed begrunnelse, underretning og klagerett på lik linje med
tilskuddsvedtaket. Dette avklarer et tidligere åpent spørsmål — refusjonssteget er ikke
en ren økonomisk attestering.

### B7 — Utfallsstyrt klageflyt

| Klageutfall | Neste steg |
|-------------|-----------|
| Avvist (formkrav ikke oppfylt) | Melding om vedtak i klagesaken. Slutt |
| Opprettholdelse | Oversendelsesbrev til Nav klageinstans + orientering til klager |
| Medhold / delvis medhold | Omgjøringen inngår i samme innstilling til beslutter |

Ved medhold genereres ikke et eget klagebrev først. Klagen og omgjøringen avsluttes med
ett brevsett.

### B8 — Sluttrapport: forutsetning eller ikke er uavklart

Det er ikke landet om sluttrapport skal være en forutsetning for refusjon. To alternativer
er i spill:

1. **Utenfor vilkårene** — sluttrapport spores, men blokkerer ikke refusjon
2. **Gult vilkår** — vises som ikke oppfylt, men saksbehandler kan krysse av «fortsett
   uten» med begrunnelse

Alternativ 2 er mest i tråd med at vilkårsvurderingen skal veilede og ikke låse, og det
gir en dokumentert skjønnsutøvelse i stedet for en usynlig omgåelse. Uansett valg:
avvisning av sluttrapport med purring er et **mangelbrev, ikke et vedtak**, og utløser
ikke klagerett.

Modellen holdes åpen: `Sluttrapport` er et eget objekt med egen tilstand, og koblingen til
refusjonsvedtaket går via vilkårsvurderingen — ikke som en hard fremmednøkkelbetingelse.

### B9 — Refusjonsfrist er en frist, ikke et vilkår

En refusjonssøknad kan sendes inn etter fristen, og det er saksbehandlers
**skjønnsvurdering** om den likevel skal realitetsbehandles.

- Innsending etter frist blokkeres ikke teknisk; kravet markeres som fremsatt etter fristen
- Vurderingen begrunnes
- Godtas ikke fristoversittelsen, avvises kravet med `avvisningsgrunn = oversittet_frist`.
  Det er et vedtak og kan påklages

### B10 — Tre fristtyper skal ikke blandes

| Frist | Kilde | Konsekvens ved oversittelse |
|-------|-------|-----------------------------|
| Tiltaksperiode | Tilsagnets `periode` | Skjønnsvurdering ved refusjon, jf. B11 |
| Refusjonsfrist | Tilsagnets `refusjonsfrist` | Skjønnsvurdering, jf. B9 |
| Klagefrist | Underretning om vedtak (fvl. § 29) | Klagen kan avvises |

Klagefrist og formkrav holdes i en ytelsesuavhengig modul — ren forvaltningslov, ingen
kobling til ekspertbistand-faget.

### B11 — Et fattet tilsagn endres ikke. Avvik håndteres med skjønn ved refusjon

Det bygges **ingen** flyt for å endre tiltaksperiode eller tilsagnsbeløp etter at vedtaket
er fattet. Arbeidsgiver kan ikke be om forlengelse, og saksbehandler kan ikke justere
tilsagnet.

I stedet håndteres avvik der pengene faktisk gjøres opp: **i refusjonsbehandlingen, ved
saksbehandlers og beslutters skjønn.** Har tiltaket løpt litt utover perioden, eller ble
det brukt mindre enn planlagt, vurderes det når refusjonskravet behandles. Dette gjør
tiltaksperioden til et skjønnsmoment i refusjonsvurderingen, ikke en hard grense.

Tre operasjoner må holdes fra hverandre her, fordi de har helt ulik gjennomførbarhet mot
OeBS:

| Operasjon | Mekanisme | Status |
|-----------|-----------|--------|
| Utbetale **mindre** enn tilsagnet | `Faktura` på lavere beløp + `GjorOppBestilling` for resten | Støttet, og det normale (B20) |
| **Endre** tilsagnsbeløpet før utbetaling | `Annullering` + ny `Bestilling` | Mulig, men ute av scope som brukerinitiert flyt |
| **Kreve tilbake** penger som er utbetalt | Ingen meldingstype finnes | Ikke støttet, se Å8 |

Merk konsekvensen av rad 1: **å utbetale mindre enn tilsagnet er ikke en reduksjon av
avsatte midler** — det er en lavere uttrekning fra dem, og fullt støttet. Faktura på det
dokumenterte beløpet, oppgjør av resten. Det er nettopp derfor underforbruk ikke er en
delvis innvilgelse, jf. B20.

Det som scopes ut er rad 2 som brukerinitiert funksjon, og rad 3 helt.

**Men mekanismen i rad 2 kan ikke scopes helt bort.** Et klagemedhold som øker
tilsagnsbeløpet krever nettopp `Annullering` + ny `Bestilling`. En klagerett systemet ikke
kan innfri er ikke en klagerett. Vi scoper altså ut inngangen, ikke kapabiliteten — og den
må avklares mot tiltaksøkonomi før klagestøtten er reell. Se Å3.

#### Avveining: skal `delvis_innvilget` støttes i det hele tatt? *(ikke besluttet)*

Fag har etterspurt delvis innvilgelse, primært på **søknad**. Det er verdt å veie mot
kompleksiteten den drar med seg, og kostnaden er ulik på de to vedtakstypene.

**På søknad er det `delvis_innvilget` som skaper det vanskelige klagetilfellet.** Klage på
et rent avslag er teknisk enkelt ved medhold: det finnes ingen bestilling fra før, så vi
oppretter bare én. Klage på et *redusert* beløp krever derimot at en eksisterende
bestilling annulleres og erstattes — nettopp den operasjonen Å3 stiller spørsmål ved.
Droppes `delvis_innvilget`, forsvinner den hardeste økonomiske klagesituasjonen med den.

**På refusjon er spørsmålet avgjort.** Kravet avledes fra bilagene og kappes til
tilsagnet, så det oppstår ikke noe gap å innvilge delvis. Se B20.

Avveiningen gjelder derfor bare **søknad**.

To konsekvenser som bør være med i avveiningen:

**Uten `delvis_innvilget` finnes heller ikke «delvis medhold».** Klageutfallene reduseres
i praksis til opprettholdelse eller fullt medhold, siden det ikke finnes noen vedtakstilstand
som uttrykker «noe av det du ba om». Det binder også en eventuell klageinstans.

**Omveien fjerner klageretten.** Alternativet uten teknisk støtte er at arbeidsgiver etter
en prat med veileder trekker søknaden og sender en ny på lavere beløp. Da finnes det
*ingen avgjørelse å klage på* — reduksjonen fremstår som arbeidsgivers eget valg, selv om
den i realiteten er Navs. Det er sannsynligvis en vesentlig del av grunnen til at fag
etterspør funksjonen, og det bør ikke leses som ren bekvemmelighet.

Tilsvarende omvei finnes på refusjon: avvise kravet som mangelfullt og be om korrigert
innsending, slik vi allerede gjør for sluttrapport. Samme innvending gjelder der.

Beslutningen for **søknad** tas ikke i dette dokumentet. Modellen bærer
`delvis_innvilget` som utfall på tilskuddsvedtak, og å ta det ut senere er en
innsnevring — det motsatte er en utvidelse som treffer vedtak, brev, økonomiflyt og
klage samtidig. For refusjon er spørsmålet avgjort, jf. B20.

### B12 — Trukket søknad avsluttes eksplisitt og frigjør avsatte midler

Både arbeidsgiver og saksbehandler (på vegne av arbeidsgiver) kan trekke søknaden.
Begge tilfeller varsler både arbeidsgiver og deltaker. Om trekking er et enkeltvedtak er
uavklart — se Å4.

### B13 — Underretning registreres per part

Underretning er en liste av hendelser, ikke ett felt. Partene varsles gjennom ulike
kanaler og kan bli underrettet på ulikt tidspunkt. Klagefristen løper fra underretning og
kan derfor løpe ulikt for de to.

### B14 — Innsigelse før vedtak skilles fra klage etter vedtak

Deltaker får kopi av søknaden ved innsending, altså før vedtaket fattes.

- **Innsigelse før vedtak** — en opplysning i saken som saksbehandler tar med i
  vurderingen, typisk mot vilkåret «Deltaker er enig». Ingen formkrav, ingen frist,
  ingen beslutterrunde
- **Klage etter vedtak** — formell klageflyt

Deltaker deltar ikke i drøftingsmøtet slik kartleggingen viser det. Kopi av søknaden er
dermed deltakers første berøring med saken, og innsigelsesvinduet er reelt.

### B15 — Ikke samtykke eller trepartssignering

Besluttet med fag. Deltaker varsles i stedet om mottatt søknad, med kopi. Arbeidsgiver
informeres i søknaden om at deltaker får kopi.

Samtykket lever videre som **vilkåret «Deltaker er enig»**, basert på arbeidsgivers
påstand. Vi har dermed dokumentasjon på *varsling* og på *hva Nav la til grunn*, ikke på
*involvering*. `KopiAvSoknadSendtDeltaker` og den frosne vilkårsvurderingen (B5) er
bevisgrunnlaget.

### B16 — Klage registreres manuelt av saksbehandler

I første omgang bygges **ikke** noe grensesnitt der arbeidsgiver eller deltaker kan sende
klage digitalt. Klagen skal komme **på e-post**, og saksbehandler registrerer den manuelt i
saksbehandlingsløsningen. Samme gjelder innsigelser før vedtak (B14).

**Telefon er ikke et klageinntak.** Ringer noen for å klage, blir de bedt om å sende klagen
på e-post. Det løser skriftlighetskravet i fvl. § 32 uten at vi må ta stilling til om et
telefonnotat er tilstrekkelig — men det gjenstår å bekrefte at e-post pluss manuell
registrering faktisk oppfyller kravet, se Å2.

Telefonhenvendelsen skal likevel loggføres på saken. Grunnen er fristen: ringer noen på
dag 41 av 42 og e-posten kommer på dag 44, er den loggførte kontakten avgjørende for om
fristoversittelsen bør unnskyldes (fvl. § 31). Uten loggen er den opplysningen tapt.

Dette er en bevisst forenkling, men flytter noen krav over på andre deler av løsningen:

**Klageveiledningen må ligge i vedtaksbrevet.** Når det ikke finnes en digital kanal, er
brevet det eneste stedet parten får vite at de kan klage. Brevet må derfor oppfylle
fvl. § 27 tredje ledd: klagerett, klagefrist, klageinstans, og *hvor og hvordan* klagen
sendes. Dette gjelder begge brevmalene, og for både tilskudds- og refusjonsvedtak.

**Mottatt og registrert er to ulike datoer.** Klagefristen måles mot da Nav faktisk mottok
henvendelsen, ikke da den ble tastet inn. Saksbehandler må kunne sette en mottaksdato
bakover i tid, og begge datoene lagres. Dette er ikke kosmetikk — differansen avgjør om
klagen er rettidig.

**Henvendelsen journalføres som klagedokument.** E-posten arkiveres. Ved telefon skriver
saksbehandler et notat som journalføres.

**Kvittering går ut selv om inntaket er manuelt.** Vi har utgående kanaler mot begge
parter allerede. Når klagen er registrert bør parten få en beskjed om at den er mottatt —
billig å gjøre, og uten det har den som ringte ingen bekreftelse på at noe skjedde.

### B17 — Saksbehandler og beslutter må være ulike personer **per behandling**

Regelen gjelder per behandling, ikke per sak. Det stilles **ingen** krav om at
saksbehandler eller beslutter i en klagesak må være andre enn de som behandlet det
opprinnelige vedtaket.

Illustrasjon av hva det innebærer:

> Kari innstiller på søknadsvedtaket, Ola fatter det. Seks måneder senere kommer en klage.
> Ola kan innstille på klagen, og Kari kan fatte den. Hver behandling har to par øyne, og
> det er tilstrekkelig.

En Nav-ansatt kan inneha begge rollene, så dette kan ikke håndheves gjennom rolletildeling.
Håndhevingen er på handlingsnivå og må ligge i backend, ikke bare i skjuling av en knapp:

- `ForelopigVedtak.innstiltAv` registreres ved innsending til beslutter
- Beslutt-handlingen avvises hvis `innloggetBruker` har innstilt på **noen versjon** av
  samme behandling

Presiseringen om «noen versjon» er nødvendig fordi retur–revider-løkka kan involvere flere
saksbehandlere. Har du innstilt én gang på behandlingen, er du ute som beslutter for den.

Konsekvens for små enheter: en behandling krever to tilgjengelige personer. Per behandling
i stedet for per sak gjør at ingen låses permanent ute av en sak.

### B18 — Økonomi håndteres via tiltaksøkonomi (team Valp), ikke av oss

[Tiltaksøkonomi](https://github.com/navikt/mulighetsrommet/tree/main/mulighetsrommet-tiltaksokonomi)
er en ACL mot OeBS PO/AP som allerede løser avsetning og utbetaling for
arbeidsmarkedstiltak. Vi produserer Kafka-meldinger og lytter på statustopics; vi snakker
ikke med OeBS direkte.

Våre begreper mapper rent over:

| Vår hendelse | Melding til tiltaksøkonomi |
|--------------|----------------------------|
| Tilskuddsvedtak innvilget | `Bestilling` — holder av midlene |
| Søknad trukket, avslag etter tilsagn | `Annullering` |
| Medhold som øker tilsagnsbeløpet | `Annullering` + ny `Bestilling` — se B11 og Å3 |
| Refusjonsvedtak innvilget | `Faktura` mot bestillingen |
| Refusjonsfrist passert uten krav, eller refusjon lavere enn tilsagn | `GjorOppBestilling` |

To ting dette avklarer:

**Annullér-og-opprett-mønsteret er ikke bare praksis, det er OeBS-modellen.** Det finnes
ingen «endre bestilling». Skal et fattet tilsagn endres — i praksis kun etter
klagemedhold, jf. B11 — må det gjøres ved å annullere og opprette på nytt.

**Restmidler må gjøres opp aktivt.** Bruker arbeidsgiver mindre enn tilsagnet, eller lar
refusjonsfristen løpe ut, blir midlene liggende bundet i OeBS til vi sender
`GjorOppBestilling`. Dette er et steg vi må trigge selv — det skjer ikke av seg selv når
saken avsluttes hos oss.

#### Bestillingens livsløp

Bestillingen opprettes ved **søknadsvedtaket** og fullføres ved **refusjonsvedtaket**. Den
lever altså gjennom hele gjennomføringsperioden:

```
 søknadsvedtak                                    refusjonsvedtak
      │                                                  │
      ▼                                                  ▼
  Bestilling ──────── midler bundet, ikke trukket ──── Faktura ──► GjorOppBestilling
      │                                                            (restbeløp)
      │◄── klagefrist tilskudd ──►│                          │◄─ klagefrist refusjon ─►│
```

Dette har tre konsekvenser som ikke er åpenbare:

**Klage på tilskuddsvedtaket treffer nesten alltid en ubetalt bestilling.** Klagefristen er
seks uker fra underretning, mens refusjon først kommer etter at tiltaket er gjennomført.
En tilskuddsklage vil derfor i praksis alltid behandles mens midlene er bundet, men ikke
trukket — som er det gunstigste tidspunktet for `Annullering` + ny `Bestilling`. Det
reduserer risikoen i Å3 betydelig: det er variant «før utbetaling» som betyr noe her.

**Refusjonen er begrenset oppad av bestillingen.** Krever arbeidsgiver mer enn tilsagnet,
kan ikke overskytende utbetales uansett vurdering. Delvis innvilgelse på refusjon er
dermed delvis påtvunget av mekanikken, ikke bare en fagvurdering — se avveiningen i B11.

**`GjorOppBestilling` må ikke sendes for tidlig.** Gjør vi opp restbeløpet straks
refusjonen er utbetalt, river vi bort beholderen vi trenger dersom en klage på
refusjonsvedtaket får medhold innenfor klagefristen. Regel: **vent med oppgjør til
klagefristen på refusjonsvedtaket er utløpt, eller til en verserende klage er avgjort.**
Samme forsiktighet gjelder når refusjonsfristen passerer uten krav mens en klage på
tilskuddsvedtaket fortsatt er åpen.

### B19 — Vedtak og økonomistatus er to ulike tilstander

Kvitteringer fra OeBS kommer asynkront på egne Kafka-topics. Det betyr at et vedtak kan
være fattet og underrettet, mens bestillingen fortsatt er ubekreftet — eller har feilet.
Feilkoder som `PO_PDOI_INVALID_PROJ_INFO` krever endringer hos OeBS før de kan løses.

Vi kan ikke rulle tilbake et fattet og underrettet vedtak fordi en bestilling feilet.
Konsekvensen er at `Tilsagn` og `Refusjonskrav` må bære en **egen økonomistatus** ved siden
av vedtakstilstanden, og at det trengs en **avstemmingsliste** for saker der de to har
kommet i utakt. Uten den blir feilede bestillinger usynlige til noen oppdager at pengene
aldri ble avsatt.

Tiltaksøkonomi garanterer rekkefølge og avhengigheter — en faktura sendes ikke før
bestillingen er kvittert OK — så det slipper vi å håndtere. Duplikathåndtering
(`DUPLICATE INVOICE NUMBER`) passer med `IdempotencyGuard` vi allerede har.

### B20 — Refusjonskravet avledes fra bilagene, og refusjonsvedtaket har binære utfall

**Beslutning**

1. `krevdBelop` **avledes fra bilagene** — arbeidsgiver oppgir ikke et fritt beløp.
   Kravet mot Nav er `min(sum bilag, tilsagn)`.
2. At bilagene dokumenterer mindre enn tilsagnet gir utfallet **`innvilget`**, ikke
   `delvis_innvilget`. Restbeløpet gjøres opp med `GjorOppBestilling`.
3. `delvis_innvilget` støttes **kun på tilskuddsvedtak**. Refusjonsvedtaket har utfallene
   `innvilget | avslag | avvist`.

**Begrunnelse**

*Tilsagnet er et tak, ikke en rettighet.* Ordningen refunderer dokumenterte kostnader
opptil tilsagnet. Bruker arbeidsgiver mindre enn planlagt, er utbetaling av det
dokumenterte beløpet en **full innfrielse av kravet** — ikke en delvis innvilgelse.

| Tilsagn | Sum bilag | Krav mot Nav | Utbetalt | Utfall |
|---------|-----------|--------------|----------|--------|
| 22 000 | 22 000 | 22 000 | 22 000 | `innvilget` |
| 22 000 | 18 000 | 18 000 | 18 000 | `innvilget` |
| 22 000 | 25 000 | 22 000 | 22 000 | `innvilget` |

*Underforbruk er normalen.* Å merke det som «delvis innvilget» ville produsert
klagegrunnlag ut av helt ordinær drift, og krevd en begrunnelse for noe som ikke er
avslått. Et vedtaksbrev som sier «innvilget» inviterer ikke til klage; «delvis innvilget»
gjør det.

*Overforbruk håndteres ved avledningen, ikke ved vedtaket.* Fordi kravet kappes til
tilsagnet allerede ved innsending, oppstår ikke situasjonen der Nav «avslår» det
overskytende. Arbeidsgiver får en tydelig melding i skjemaet om at bilagene overstiger
tilsagnet og at kravet begrenses. Konflikten flyttes fra et påklagbart vedtak til en
valideringsmelding — samme prinsipp som ellers i dokumentet: ikke produser klagbare
avgjørelser du kan unngå.

*Ett sted å endre beløp.* `delvis_innvilget` finnes dermed bare på tilskuddsvedtaket, som
også er der fag har etterspurt det og der beløpet faktisk fastsettes.

**Konsekvenser**

Kan saksbehandler ikke godta deler av dokumentasjonen — en post er ikke støtteberettiget —
finnes det ikke lenger noe «innvilg delvis». Kravet må da returneres til arbeidsgiver for
korrigert innsending, som et **mangelbrev**, på samme måte som en avvist sluttrapport.
Det er ikke et vedtak og utløser ikke klagerett.

Restrisiko: nekter arbeidsgiver å korrigere, står vi igjen med å avslå hele kravet. Det er
et hardere utfall enn en delvis innvilgelse ville vært, og bør følges opp hvis det viser
seg å forekomme.

Klage på et refusjonsvedtak er fortsatt mulig — det er et vedtak. Men den vil gjelde
`avslag` eller `avvist`, ikke beløpsstørrelsen, siden beløpet nå følger direkte av
bilagene og tilsagnet.

### B21 — Refusjonskrav kan returneres til arbeidsgiver for retting

Fordi refusjonsvedtaket har binære utfall (B20), må det finnes en vei tilbake når
dokumentasjonen ikke holder: saksbehandler returnerer kravet og ber arbeidsgiver rette det
— erstatte eller supplere bilag — i stedet for å innvilge delvis.

Mekanikken speiler sluttrapportkontrollen, som allerede har avvis → purring → ny
innsending:

```
  Refusjonskrav mottatt
        ▼
  saksbehandlers kontroll
        ├── i orden ──────────────► behandlingsmotoren → vedtak
        └── mangelfullt
              ▼
        returnert til retting  ──► Oppgave til AG (med svarfrist)
              ▼                         │
        AG erstatter bilag  ◄───────────┘  purring ved manglende svar
              ▼
        ny versjon av kravet → tilbake til kontroll
```

**Dette er et mangelbrev, ikke et vedtak.** Det utløser ikke klagerett, på linje med avvist
sluttrapport og etterspørsel om mer dokumentasjon.

**Kravet versjoneres, det erstattes ikke.** Bilag og avledet `krevdBelop` lagres per
versjon, slik vi gjør for foreløpig vedtak (B3). Den opprinnelige innsendingen må bevares —
en senere klage kan gjelde hva som faktisk ble levert første gang.

**Fristen relaterer seg til første innsending.** Dette er den viktige detaljen: en
retteroppfordring må ikke kunne gjøre et rettidig krav for sent. `fremsattEtterFrist`
vurderes mot **første** innsendingsdato, ikke mot den korrigerte versjonen. Uten denne
regelen kan Nav i praksis frata arbeidsgiver retten til refusjon ved å be om en retting
tett opp mot refusjonsfristen.

**Svarfrist og opphør.** Oppgaven har en svarfrist, med purring ved manglende respons.
Svarer arbeidsgiver aldri, må saken kunne avsluttes: enten ved avslag på kravet — som er et
vedtak med klagerett — eller ved at bestillingen gjøres opp. Hvilken av dem, og etter hvor
lang tid, er ikke avklart, se Å13.

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

ForelopigVedtak                          // saksbehandlers innstilling, versjonert (B3)
  id, behandlingId, versjon
  utfall          : innvilge | innvilge_redusert | avslaa
                  | (klage) avvis | oppretthold | medhold | delvis_medhold
  innvilgetBelop
  begrunnelsePerPart : { deltaker: tekst, arbeidsgiver: tekst }
  innstiltAv      : NAVident
  sendtTidspunkt
  returNotat      : tekst?               // beslutters kommentar hvis returnert
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

Underretning                             // én per part, jf. B4
  vedtakId, part, kanal, dokumentId, tidspunkt
  klagefrist      : dato?                // null når parten ikke har klagerett

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
  forstInnsendt      : dato          // fristen måles mot denne, jf. B21
  status             : mottatt | til_kontroll | returnert_til_retting
                     | til_behandling | avgjort
  kontonummer
  versjoner[]        : {
      versjon, innsendt, bilag[],
      dokumentertBelop : sum bilag,
      krevdBelop       : min(sum bilag, tilsagn)     // avledet, jf. B20
  }
  retur              : { begrunnelse, svarfrist, sendtAv, tidspunkt }?
  fremsattEtterFrist : bool          // vurdert mot forstInnsendt
  fakturanummer      : string?
  okonomistatus      : ikke_sendt | sendt | bekreftet | feilet | utbetalt
  okonomifeil        : { kode, melding, tidspunkt }?

Klage
  id
  gjelderVedtak   : VedtakId             // tilskudd eller refusjon
  klager          : deltaker | arbeidsgiver | fullmektig
  mottakskanal    : epost | brev         // telefon henvises til e-post, B16
  mottatt         : dato                 // da Nav mottok henvendelsen — måler fristen
  registrert      : tidspunkt            // da saksbehandler tastet den inn
  registrertAv    : NAVident
  dokumentId      : journalført henvendelse eller telefonnotat
  frist / innenFrist / grunnlag
  formkrav        : { skriftlig, signert, angirEndring, rettsligKlageinteresse }
  behandlingId    : BehandlingId         // klagen behandles gjennom samme motor

Innsigelse                               // deltaker, før vedtak — ikke en klage
  id, soknadId, mottakskanal, mottatt, registrert, registrertAv, innhold
```

`Klage` peker på `VedtakId` og henger på en `Behandling`. Det er dette som gjør klage på
tilskuddsvedtak og refusjonsvedtak til samme mekanisme.

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
PartUnderrettet(vedtakId, part, kanal, tidspunkt, dokumentId, klagefrist?)
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

**Gjennomføring, sluttrapport, refusjon og utbetaling**
```
TilskuddsbrevGenerert(vedtakId, dokumentId, periode, refusjonsfrist)
SluttrapportPaaminnelseSendt(sakId, tidspunkt)
SluttrapportMottatt(sluttrapportId, sakId, bilag)
SluttrapportAvvist(sluttrapportId, begrunnelse)
SluttrapportPurringSendt(sluttrapportId, tidspunkt)
SluttrapportGodkjent(sluttrapportId, godkjentAv)
RefusjonsfristPassert(tilsagnVedtakId, tidspunkt)
RefusjonskravMottatt(refusjonskravId, krevdBelop, fremsattEtterFrist)
RefusjonskravReturnertTilRetting(refusjonskravId, begrunnelse, svarfrist, sendtAv)
RefusjonskravPurringSendt(refusjonskravId, tidspunkt)
RefusjonskravKorrigert(refusjonskravId, versjon, bilag, krevdBelop, innsendt)
```

**Økonomi mot tiltaksøkonomi** — utgående meldinger og innkommende kvitteringer (B18, B19)
```
BestillingSendt(vedtakId, bestillingsnummer, belop, periode)
BestillingBekreftet(vedtakId, bestillingsnummer, tidspunkt)
BestillingFeilet(vedtakId, feilkode, melding, tidspunkt)
AnnulleringSendt(vedtakId, bestillingsnummer, aarsak)
FakturaSendt(refusjonskravId, fakturanummer, bestillingsnummer, belop, kontonummer)
FakturaBekreftet(refusjonskravId, fakturanummer, tidspunkt)
FakturaFeilet(refusjonskravId, feilkode, melding, tidspunkt)
BestillingGjortOpp(vedtakId, restbelop, aarsak)
```

**Klage** — merk at selve avgjørelsen går via behandlingsmotoren over
```
KlageveiledningGitt(sakId, kanal: telefon, tidspunkt, notat)   // henvist til e-post, B16
KlageRegistrert(klageId, gjelderVedtak, klager, grunnlag, mottakskanal,
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
dokumentasjonsoppgaver er nytt arbeid mot produsent-API-et.

| Hendelse | Arbeidsgiver | Deltaker (Min side-varsler) |
|----------|--------------|------------------------------|
| Søknad innsendt | Sak opprettes, beskjed | Beskjed med kopi av søknaden |
| Søknad trukket | Beskjed | Beskjed |
| Tilskuddsvedtak | Sakstatus + beskjed, brev | Brev + beskjed |
| Sluttdato nærmer seg | **Oppgave**: send sluttrapport | — |
| Sluttrapport avvist | **Oppgave**: send på nytt, purring | — |
| Refusjon skal søkes | **Oppgave** med utløp = refusjonsfrist | — |
| Refusjonsfrist passert | Oppgaven utgår | — |
| Refusjonskrav mottatt | Beskjed + sakstatusoppdatering | — |
| Refusjonskrav returnert til retting | **Oppgave** med svarfrist: erstatt/suppler bilag | — |
| Svarfrist nærmer seg | Purring | — |
| Korrigert krav mottatt | Beskjed, oppgave utført | — |
| Refusjonsvedtak | Sakstatus + beskjed, brev | — (se Å5) |
| Utbetalt | Beskjed | — |
| Klage registrert | Beskjed: «vi har mottatt klagen din» | Samme, ved klage fra deltaker |
| Klage avgjort | Sakstatus + beskjed, brev | Ved klage fra deltaker |

Når refusjonskravet kommer etter fristen er oppgaven allerede utgått. Da er beskjed +
sakstatusoppdatering eneste kvittering. Teksten bør være tydelig på at kravet faktisk er
registrert.

Merk at klageinntaket er manuelt (B16), men **kvitteringen er det ikke**. Utgående kanaler
finnes mot begge parter, så den som har ringt eller sendt e-post bør få en bekreftelse på
at klagen er registrert. Uten det er telefonhenvendelsen sporløs sett fra klagers side.

---

## Samtidighet

**Klage på tilskuddsvedtak mens gjennomføringen går.** Klagefristen kan løpe inn i
tiltaksperioden. Anbefaling: **ikke sett gjennomføring eller refusjonsløp på vent.** La
klagen løpe parallelt og håndter et senere medhold som omgjøring. `erstatterVedtak` og
`foranledigetAv` gjør kjeden sporbar.

**Medhold etter at refusjon er utbetalt.** Øker medholdet tilsagnet, må differansen
etterbetales: `Annullering` + ny `Bestilling` + ny `Faktura` for differansen, i den
rekkefølgen tiltaksøkonomi håndhever. Reduserer medholdet tilsagnet etter utbetaling,
oppstår tilbakekreving — og tiltaksøkonomi har ingen meldingstype for det, se Å8.

**Vedtak fattet, bestilling feilet.** Vedtaket står og parten er underrettet, men pengene
er ikke avsatt. Saken må havne på avstemmingslista (B19) og løses operativt — den kan ikke
løses ved å omgjøre vedtaket.

**Flere behandlinger åpne samtidig.** En klagebehandling kan løpe mens refusjonsløpet
går. Gjeldende vedtak er alltid det siste ikke-annullerte i
`erstatterVedtak`-kjeden.

**Klage på annullert vedtak.** Skal ikke være mulig; klagen retter seg mot gjeldende
vedtak.

---

## Forvaltningsrettslige premisser

Må bekreftes av fag.

- **Klagefrist** løper fra underretning om vedtaket (fvl. § 29), per part
- **Medhold i klage fra deltaker** på et innvilget vedtak vil i praksis være omgjøring til
  ugunst for arbeidsgiver (fvl. § 35), med egne vilkår og krav om forhåndsvarsel
- **Foreløpig vedtak** er en intern innstilling uten rettsvirkning, ikke et
  enkeltvedtak etter fvl. § 2 b
- **Oversittet refusjonsfrist** — hjemmelsgrunnlaget for skjønnet bør identifiseres
- **Opprettholdelse** medfører oversendelse til Nav klageinstans

---

## Konsekvenser

**Positive**

- Klagebehandling er gjenbruk av behandlingsmotoren, ikke en parallell løsning
- Klage, medhold og omgjøring i én innstilling, én beslutterrunde, ett brevsett
- Eksplisitt sporbarhet mellom klage, omgjøring og annullert vedtak
- Frosset vilkårsvurdering gir klagebehandler nøyaktig det beslutningsgrunnlaget som forelå
- Redusert beløp blir målbart og begrunnet
- Antall returer fra beslutter blir en målbar kvalitetsindikator

**Kostnader og ny kompleksitet**

- Migrering fra `SoknadStatus` til behandling + foreløpig vedtak + vedtak
- Versjonering og frysing av vilkårsvurdering
- Fristmotor: refusjonsfrist med oppgaveutløp, sluttrapportpåminnelse, klagefrist per part
- Oppgave-støtte mot notifikasjonsplattformen: sluttrapport, refusjon, retting av krav
- Kafka-integrasjon mot tiltaksøkonomi: fire utgående meldingstyper, to statustopics
- Økonomistatus som egen tilstand, med avstemmingsliste for utakt (B19)
- Aktiv oppgjørslogikk for ubrukte restmidler (`GjorOppBestilling`)
- Nye dokgen-maler: vedtaksbrev per part for tilskudd og refusjon, samt mangelbrev,
  klagevedtak og oversendelsesbrev
- **Klageveiledning i alle vedtaksbrev** (fvl. § 27 tredje ledd) — med manuelt inntak er
  brevet eneste sted parten får vite hvordan de klager
- Backend-håndheving av tomannsregelen (B17)
- Integrasjon mot Nav klageinstans

**Forenklet av manuelt klageinntak**

- Ingen klageflate for arbeidsgiver, ingen klagefunksjon i deltakers microfrontend
- Ingen validering av innsendt klage i sanntid — formkrav vurderes av saksbehandler
- Til gjengjeld hviler klagerettens realitet helt på at brevteksten er god

---

## Åpne spørsmål

**Å1 — Hvem har klagerett?** *(blokkerende)*

Prosesskartleggingen forutsetter at **deltaker har klagerett**, og setter spørsmålstegn
ved arbeidsgivers («ikke klagerett?»). Det er motsatt av hva man kanskje skulle tro, siden
tilskuddet utbetales til arbeidsgiver.

Vurderingen bør skille mellom hvem vedtaket *retter seg mot* og hvem det *direkte gjelder*
(fvl. § 2 e). Det er fullt mulig at begge er parter med klagerett mot ulike sider av
vedtaket: deltaker mot tiltaket, arbeidsgiver mot beløpet. Modellen støtter det gjennom
klagefrist per underretning (B4), men svaret må komme fra fag.

Merk at hvis arbeidsgiver ikke har klagerett på tilskuddsvedtaket, blir spørsmålet ekstra
skarpt for refusjonsvedtaket — der er arbeidsgiver utvilsomt den avgjørelsen retter seg mot.

**Å2 — Oppfyller e-post skriftlighetskravet i fvl. § 32?**

Klagen kommer på e-post og registreres manuelt av saksbehandler (B16). Det må bekreftes at
denne kombinasjonen holder — både at e-post regnes som skriftlig, og at manuell
registrering i fagsystemet med journalføring av e-posten er tilstrekkelig dokumentasjon.

Telefon er ikke lenger et åpent spørsmål: den som ringer henvises til e-post.

Dagpenger-teamet har et beslektet uavklart punkt: «det må være en form for bekreftelse på
at det er en gyldig klage, enten den er skriftlig eller muntlig». Verdt å avklare felles.

**Å3 — Kan et klagemedhold som øker tilsagnsbeløpet gjennomføres økonomisk?**
*(blokkerende for reell klagestøtte)*

B11 scoper ut endring av fattet tilsagn som brukerinitiert flyt, men mekanismen kreves
fortsatt ved medhold. To varianter må avklares med team Valp:

- **Før utbetaling** — kan vi sende `Annullering` på en bekreftet bestilling og opprette
  en ny med høyere beløp på samme sak? Dette er den viktige varianten: en klage på
  tilskuddsvedtaket vil nesten alltid behandles i dette vinduet, jf. bestillingens
  livsløp i B18
- **Etter utbetaling** — bestillingen kan ikke annulleres når det er betalt ut mot den.
  Kan vi opprette en *tilleggsbestilling* med egen faktura for differansen, eller finnes
  det ingen vei? Gjelder først og fremst klage på refusjonsvedtaket. Der kan vi kjøpe oss
  handlingsrom ved å utsette `GjorOppBestilling`, jf. B18

Uten svar på minst den første har vi en klagerett vi ikke kan innfri på beløpsklager.

Merk koblingen til avveiningen i B11: droppes `delvis_innvilget` på søknad, blir dette
spørsmålet vesentlig mindre kritisk — medhold på et rent avslag krever bare en ny
bestilling, ikke annullering av en eksisterende.

**Å4 — Er trukket søknad et vedtak?** Har betydning for om det kreves begrunnelse og
underretning med klagerett, særlig når saksbehandler trekker på vegne av arbeidsgiver.

**Å5 — Skal deltaker underrettes om refusjonsvedtaket?** Refusjonen er et økonomisk
oppgjør mellom Nav og arbeidsgiver. Antatt nei, men henger sammen med Å1.

**Å6 — Sluttrapport som vilkår.** Utenfor vilkårene, eller gult vilkår med «fortsett uten»
og begrunnelse? Se B8.

**Å7 — Hva gjør vi når deler av et bilag ikke er støtteberettiget?** B20 gir binære utfall
på refusjon, og B21 gir veien tilbake: returner kravet til retting. Det fungerer så lenge
arbeidsgiver retter — se Å13 for tilfellet der de ikke gjør det.

**Å8 — Tilbakekreving.** Hva skjer ved feilutbetaling? Tiltaksøkonomi har ingen
meldingstype for tilbakekreving, jf. tabellen i B11, så dette må eventuelt løses utenfor
integrasjonen. Ikke vurdert i dette dokumentet.

**Å9 — Klageinstans-integrasjon.** Tar Nav klageinstans imot via Kabal for vår tiltakstype,
under hvilket tema og i hvilket format?

**Å10 — Kontonummer.** Er det krav om oppgitt kontonummer, og må vi gjøre noe for å kunne
bruke kontonummer for refusjoner fra Nav? Blokkerer utbetaling.

**Å11 — Hvor mye av søknaden skal deltaker se?** Antatt hele, gitt B15, men bør bekreftes
med personvern.

**Å12 — Tilsagnsår og årsskifte.** OeBS avviser bestillinger for langt frem i tid
(`PO_PDOI_INVALID_PROJ_INFO`). Et tilsagn gitt i desember med tiltaksperiode inn i neste
år kan feile. Hvordan budsjettår håndteres må avklares med team Valp før B18
implementeres.

**Å13 — Hva skjer når arbeidsgiver aldri retter et returnert refusjonskrav?**
Etter hvor mange purringer og hvor lang tid avsluttes saken, og avsluttes den med avslag
(vedtak, klagerett) eller med at bestillingen bare gjøres opp? Se B21.

---

## Avgrensning: økonomioversikt

Beslutter — eller en egen økonomirolle — skal kunne se et regnskap per år, avdeling og
sektor. Rolletildeling og tilgangsstyring på enhet og sak er uavklart. **Dette planlegges i
egen sesjon og får sin egen ADR**, og er bevisst holdt utenfor dette dokumentet.

To ting fra B18 og B19 som bør tas med inn i den:

**Oversikten må skille mellom tre tall.** *Disponert* (vedtatt hos oss), *bekreftet*
(kvittert av OeBS) og *utbetalt*. De divergerer i det asynkrone vinduet, og permanent
dersom en melding feiler. En oversikt som viser ett tall vil før eller siden vise feil tall.

**Vi er ikke fasit.** OeBS er den autoritative økonomikilden. Vår oversikt er en projeksjon
over egne vedtak og mottatte kvitteringer, og bør presenteres som det — ikke som regnskap.
Projeksjonsmekanikken finnes allerede i `event/projections/`.

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
- Designutkast ny saksbehandlerløsning (Figma) — vilkårsvurdering, foreløpig vedtak,
  beslutters notat, refusjonskrav, sluttrapportkontroll
- Teamets prosesskartlegging
- Dagpenger-teamets kartlegging av klageflyt
