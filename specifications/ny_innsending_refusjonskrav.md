# Ny innsending av refusjonskrav

Trello: https://trello.com/c/7KPgxb8o/729
Kort-ID: `6b03f0a30a1bbe70c725c033`

## Mål

Saksbehandler skal kunne kreve at arbeidsgiver sender refusjonskravet på
nytt når dokumentasjonen ikke er tilstrekkelig. Arbeidsgiver ser en
forhåndsdefinert beskjed og får tilgang til skjemaet igjen.

Dette er dokumentkontroll, ikke et vedtak om avslag på refusjon.
Sluttrapporten må være bekreftet før saksbehandler kontrollerer
refusjonskravet. Sakens status endres ikke.

## Bakgrunn

- `refusjonskrav` har allerede `sak_id`, beløp, status og opprettelsestidspunkt.
  Innsending setter status til `MOTTATT`.
- `refusjonskrav_sak_id_key` fra V35 tillater bare én rad per sak.
  Både `RefusjonApi` og `RefusjonDb` avviser innsending hvis saken har et krav.
- Lesefunksjonene henter allerede nyeste krav etter `opprettet DESC, id DESC`,
  men returnerer ikke kravets id, status eller vurdering.
- Saksbehandling og arbeidsgiver bruker samme `RefusjonStatusDto`.
  Saksbehandlers ident og navn må ikke legges i denne felles DTO-en.
- `Refusjonskontroll` er en lokal skisse. Ingen vurdering lagres,
  og knappene for vedtak og utbetaling er deaktivert.
- Refusjonsfanen vises bare når `innsendinger.refusjonskrav` er satt.
  Den ville forsvunnet etter krav om ny innsending uten et eget eksistensfelt.
- `hentMottatteInnsendinger` filtrerer refusjonskrav på `MOTTATT` før den
  velger en rad. Med flere innsendinger må nyeste rad velges først.

## Beslutninger

Kortet er lest med kommentarer, sjekklister og vedlegg 2026-11-26.
Kortet har ingen kommentarer, sjekklister eller vedlegg. Figma-lenkene
står i beskrivelsen.

| Kilde | Beslutning |
|-------|------------|
| Kort 729 | Saksbehandler skal kunne be om nytt refusjonskrav, og arbeidsgiver skal kunne sende inn på nytt. |
| Figma, lest 2026-11-26 | Saksbehandler velger årsaker og ser beskjeden før innsending. Etterpå vises vurderingsmetadata og «Venter nytt refusjonskrav». |
| Figma, lest 2026-11-26 | Arbeidsgiver ser «Refusjonskrav avvist», vurderingsdato og beskjed i et åpent kort. «Søk refusjon» vises igjen. |
| Avklaring i samtalen, 2026-11-26 | Varsel via notifikasjonsplattformen og påminnelse etter én uke utsettes, som for sluttrapport. Dette erstatter kortets krav om å lage dem nå. |
| Avklaring i samtalen, 2026-11-26 | Ingen fritekst. Bare forhåndsdefinerte beskjeder, også ved «Annet». |

### Valg i spesifikasjonen

Disse forslagene skal gjennomgås før implementasjon:

- Hver innsending får en egen rad. Nyeste rad bestemmer gjeldende status.
  En avvist innsending overskrives ikke.
- Bruk `NY_INNSENDING_KREVES`, ikke `AVSLATT`, til dokumentkontroll.
  Et senere avslag på refusjon er et eget vedtak med beslutter.
- Bare kontrollens «nei»-løp lagres i dette kortet. «Ja» gir ingen ny
  status, event, godkjenning eller utbetaling. Videre behandling er ikke
  tilgjengelig ennå.
- Dokumentkontrollen krever at nyeste sluttrapport er `BEKREFTET`.
  Arbeidsgiver kan fortsatt sende inn refusjonskrav før den er bekreftet.
- Tidligere krav, beløp, vurderinger og vedlegg beholdes. Automatisk
  sletting av erstattede refusjonsvedlegg innføres ikke i dette kortet.
  Dette er et annet valg enn for erstattede sluttrapportfiler.
- Backend lager arbeidsgiverbeskjeden fra valgte årsaker. Frontend sender
  ikke en vilkårlig begrunnelse.

## Skisser og Aksel

| Figma-lenke | Innhold |
|-------------|---------|
| https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1246-16693 | Dokumentkontroll, nedtrekksliste, årsaker, forhåndsvisning av beskjed og knapp for å be om nytt krav. |
| https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1247-11220 | Lagret vurdering, vurdert av og tidspunkt, årsak og beskjed. Midtfeltet venter på nytt krav. |
| https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1262-19510 | Arbeidsgivers vedtaksside med åpent avvisningskort og «Søk refusjon». |

Arbeidsgiverfiguren var for stor for designkontekst. De relevante delene
er lest separat: `1262:20198` (kortet) og `1262:19517` (handlingene).

Frontend-appene bruker Aksel 8 med versjonskrav `^8.16.1`.
Aksel MCP er brukt til å lese API-ene for `Select`, `Checkbox` og
`CheckboxGroup`, dokumentasjonen for `ExpansionCard`, ikonet `FileXMark`
og tokenet `space-16` (`1rem`). ExpansionCard-dokumentasjonen beskriver
8.23.2. Versjonsavhengige props kontrolleres mot installerte pakker.

- `Select` med synlig label for dokumentkontrollen.
- `CheckboxGroup` med synlig legend og `Checkbox` for årsakene.
  Minst én årsak kreves ved «nei».
- Behold eksisterende knapper, layout og statusvisning fra sluttrapportkontrollen.
- Behold `ExpansionCard` på arbeidsgiversiden. Ved avvisning åpnes det
  som standard og får `FileXMarkIcon`, vurderingsdato og beskjed øverst.
- Behold eksisterende avstander, blant annet `space-16`. Ingen nye
  farger, ikonpakker eller delte UI-komponenter.

Skissens tekst om påminnelse utelates, fordi funksjonen er utsatt.
Skissen er ikke grunnlag for nye kontonummeroppslag eller endringer i
andre deler av vedtakssiden.

## Tilstander

```text
Ingen rad → MOTTATT → NY_INNSENDING_KREVES
                ▲               │
                └── ny innsending, ny rad
```

`IKKE_MOTTATT` utledes av at saken ikke har noen rad.
Dette kortet trenger bare de lagrede statusene `MOTTATT` og
`NY_INNSENDING_KREVES`.

Bare nyeste krav med `MOTTATT` kan få krav om ny innsending.
Et eldre krav beholder `NY_INNSENDING_KREVES` når arbeidsgiver sender nytt.

## Krav

### Årsaker og arbeidsgiverbeskjed

Nedtrekkslista har label «Kan refusjonskravet behandles med dokumentasjonen
som er mottatt?» og valgene «Ja» og «Nei, arbeidsgiver må sende inn
refusjonskravet på nytt». Ingen valg er forhåndsvalgt.

Ved «nei» vises «Beskriv hva arbeidsgiver må rette opp i»:

| Årsak | Valg i skjemaet | Tekst etter «Kontroller at» |
|-------|----------------|---------------------------|
| `BELOP_MAA_JUSTERES` | Justere beløp for refusjonskravet | Beløpet i refusjonskravet stemmer med de dokumenterte utgiftene. |
| `DOKUMENT_IKKE_FAKTURA` | Dokumentet er ikke en faktura | Vedleggene er fakturaer som dokumenterer utgiftene til ekspertbistand. |
| `DOKUMENT_IKKE_LESELIG` | Dokumentet er ikke leselig | Vedleggene er leselige og av god kvalitet. |
| `ANNET` | Annet | Opplysningene og vedleggene gjelder riktig person og tiltak. |

Foreslått innledning til den faste beskjeden:

«For at vi skal kunne behandle refusjonskravet, må du korrigere
opplysningene. Send refusjonskravet på nytt.»

Deretter vises «Kontroller at» og punktene for valgte årsaker i
tabellrekkefølge. Ingen fritekstfelt, heller ikke ved `ANNET`.
Tekstene skal gjennomgås sammen med specen.

### Endepunkt og tilgang

`POST /api/saksbehandling/v1/saker/{sakId}/refusjon/{refusjonskravId}/vurdering`

Request har `arsaker`, en liste med én til fire ulike årsaker.
Det er ingen klientstyrt beskjed eller vurderingsmetadata.
Ukjente årsaker, tom liste og duplikater gir 400 med feltfeil.

Tilgang følger sluttrapportvurderingen: rollen `SAKSBEHANDLER`, tilgang
til behandlende enhet og i Tilgangsmaskinen, kilde `EKSPERTBISTAND` og
tildeling til innlogget saksbehandler.

På den låste sakraden kontrolleres kilde, tildeling og status på nytt,
samt nyeste sluttrapport og refusjonskrav:

| Brudd | Svar |
|-------|------|
| Saken eller kravet på saken finnes ikke | 404 |
| Saksbehandler er ikke lenger tildelt saken | 403 |
| Saken har feil kilde eller er ikke `INNVILGET` | 409 med eksisterende sakskode |
| Nyeste sluttrapport mangler eller er ikke `BEKREFTET` | 409 `SLUTTRAPPORT_IKKE_BEKREFTET` |
| Det finnes et nyere refusjonskrav | 409 `REFUSJONSKRAV_IKKE_NYESTE` |
| Kravet har ikke `MOTTATT` | 409 `REFUSJONSKRAV_ALLEREDE_VURDERT` |

Ved gyldig vurdering publiseres eventen i transaksjonen. Svaret er 202,
og handleren lagrer vurderingen asynkront. Feil følger eksisterende
feltfeil og svar med `kode` og `message`, som i sluttrapportvurderingen.

### Hendelse og lagring

Ny event: `RefusjonskravNyInnsendingKrevd`, med `aggregateRootId = soknadId`.

Eventen inneholder `refusjonskravId`, `sakId`, `soknadId`, valgte årsaker,
den ferdige arbeidsgiverbeskjeden, `vurdertAvIdent`, `vurdertAvNavn` og
`tidspunkt`. Årsakene får en egen enum i `event/domain`, ikke en API-enum.
Den lagrede beskjeden skal ikke endres når faste tekster endres senere.

`LagreRefusjonskravNyInnsendingKrevd` bruker idempotensvern og oppdaterer
bare riktig krav på riktig sak som fortsatt er nyeste og `MOTTATT`.
Handleren låser samme sakrad som innsendingen. Duplikater skal ikke
skrive ny vurdering eller gi ny saksloggpost. Manglende eller ugyldige
referanser i eventen behandles som en eksplisitt handlerfeil.

Ved oppdatering settes status, årsaker, beskjed og vurderingsmetadata.
`sak.sist_endret` oppdateres, og `SakOppdatert` publiseres med notatet
«Ny innsending av refusjonskrav kreves». Dette skjer i samme transaksjon
som vurderingen og idempotensvernet.

`RefusjonskravMottatt` og `SkrivSaksloggForRefusjonskrav` beholdes.
Hver ny innsending publiserer sin egen mottatt-event.

### Migrering og ny innsending

Lag en ny migrering, foreslått `V43__refusjonskrav_vurdering.sql`,
med neste ledige nummer kontrollert mot main før implementasjon.
Tidligere migreringer endres ikke.

- Fjern `refusjonskrav_sak_id_key`, slik at en sak kan ha flere krav.
- Legg til nullable vurderingskolonner og årsaker, uten backfill.
  Eksisterende krav beholder `MOTTATT`.
- Behold en indeks for oppslag på `sak_id`.
- Behold fremmednøkler og dagens sletting via sak.

Innsending tillates bare uten krav eller når nyeste har
`NY_INNSENDING_KREVES`. Kontrollen gjøres før filene leses og skannes,
og på nytt under saklåsen i `lagreRefusjonskrav`.

Ny innsending oppretter nytt krav og nye vedlegg i samme transaksjon
som `RefusjonskravMottatt`. Beløpet i tidligere krav endres ikke.
Dagens beløpsgrense, PDF-validering, filgrenser, virusskanning og
arbeidsgivertilgang gjelder også ved ny innsending.

### Visning for saksbehandler

Lag en egen saksbehandlings-DTO med kravets id, status, årsaker, beskjed,
vurderingsmetadata og eksisterende beløp og vedlegg.

- Dokumentkontrollen kan brukes bare av tildelt saksbehandler på en
  innvilget sak med bekreftet sluttrapport og et mottatt krav.
  Andre med lesetilgang ser opplysningene uten lagringshandling.
- Manglende bekreftelse av sluttrapport vises som en forklaring, ikke
  som tomt panel eller som en gjennomført kontroll.
- Ved «nei» vises årsaker og «Tekst som vises til arbeidsgiver».
  Knappen heter «Avvis, be arbeidsgiver sende på nytt».
- Ved `NY_INNSENDING_KREVES` vises «Refusjonskrav avvist», «Venter nytt
  refusjonskrav», vurdert av med dato og klokkeslett, årsaker og beskjed.
- Midtfeltet viser «Ingen refusjonskrav til behandling» og «Venter på
  at arbeidsgiver sender inn et nytt refusjonskrav», som i skissen.
- Nytt krav viser beløp og vedlegg fra den nye innsendingen og en
  urørt dokumentkontroll. Tidligere skjemavalg skal ikke følge med.

Legg til `harRefusjonskrav`, tilsvarende `harSluttrapport`, for å holde
fanen synlig uansett status. `innsendinger.refusjonskrav` og oppgaven
gjelder bare nyeste krav med `MOTTATT`. Ny innsending gir oppgaven tilbake.

Hooken viser ventende lagring etter 202 og henter status igjen.
Lagret vurdering må ikke presenteres som bekreftet bare fordi en
optimistisk oppdatering ble gjort. Feil ved lagring eller ny henting
vises med mulighet til å prøve igjen.

### Visning for arbeidsgiver

Arbeidsgiverens DTO får `status`, `vurdertTidspunkt` og `begrunnelse`.
Begrunnelsen er den faste beskjeden og returneres bare ved
`NY_INNSENDING_KREVES`. Navn og ident på saksbehandler returneres aldri.

På vedtakssiden i v2 vises «Søk refusjon» igjen når nyeste krav har
`NY_INNSENDING_KREVES`. Kortet får «Refusjonskrav avvist»,
`FileXMarkIcon` og vurderingsdato, og åpnes som standard.
Beskjeden vises over beløp, vedlegg og innsendingsdato.
Overskriften gjentas ikke inne i kortet.

`SokRefusjonPage` tillater skjemaet ved `NY_INNSENDING_KREVES`, viser
«Send inn nytt refusjonskrav» og beskjeden øverst. Arbeidsgiver oppgir
beløp og laster opp alle vedlegg på nytt. Tidligere filer sendes ikke
automatisk med. Etter innsending brukes dagens kvitteringsflyt.

Statusfeil skal ikke åpne skjemaet. En gammel åpen side kan få 409
etter samtidig innsending; den viser konflikten og ber om ny henting.
Eksisterende arbeidsgiversider for Arena beholdes uendret.

### Observerbarhet

Logg sakens tekniske id og resultatkode, ikke beskjed, filnavn eller
ansattes navn og ident. Vurderingen finnes i eventen og på kravraden.
Saksloggen viser handlingen uten arbeidsgiverbeskjeden.

## Berørte filer

Backend-stier under `backend/src/main/kotlin/no/nav/ekspertbistand/`:

| Fil | Endring |
|-----|---------|
| `refusjon/RefusjonDb.kt` | Status, vurderingsmetadata, nyeste-krav-hjelper og ny innsending. |
| `refusjon/RefusjonApi.kt` | Innsendingsvakt og arbeidsgiver-DTO. |
| `saksbehandling/RefusjonskravVurderingApi.kt` (ny) | Vurderingsrute og validering. |
| `saksbehandling/SakDokumentApi.kt` | Egen saksbehandlings-DTO. |
| `saksbehandling/Db.kt`, `SakApi.kt` | Nyeste mottatte krav og `harRefusjonskrav`. |
| `event/Events.kt` | Ny event og registrering av handler. |
| `event/domain/` | Egen enum for årsakene. |
| `event/handlers/LagreRefusjonskravNyInnsendingKrevd.kt` (ny) | Vurdering, idempotens og `SakOppdatert`. |
| `Application.kt` | Registrer ruten utenfor prod, som sluttrapportvurderingen. |

Øvrige filer:

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/` | Ny migrering for flere krav og vurdering. |
| `backend/src/test/kotlin/no/nav/ekspertbistand/` | API-, DB-, handler- og dekningstester, lokale testdata og ruteregistrering i `LocalApplication`. |
| `frontend/saksbehandling/src/components/Refusjonskontroll.tsx` | Dokumentkontroll og lagret vurdering. |
| `frontend/saksbehandling/src/components/RefusjonskravVisning.tsx` | Ventetilstand etter krav om ny innsending. |
| `frontend/saksbehandling/src/hooks/useRefusjon.ts` | DTO, lagring og revalidering. |
| `frontend/saksbehandling/src/hooks/useSak.ts`, `pages/SakPage.tsx`, `utils/constants.ts` | Eksistensfelt, fanesynlighet, tilgang til kontroll og rute. |
| `frontend/client/src/v2/hooks/useRefusjonStatus.ts` | Arbeidsgiverstatus og beskjed. |
| `frontend/client/src/v2/components/RefusjonskravSendtInnKort.tsx` | Avvisningskort. |
| `frontend/client/src/v2/pages/VedtakPage.tsx`, `SokRefusjonPage.tsx` | Ny innsending og statusbasert tilgang til skjemaet. |
| Frontend-mocker og berørte tester | Begge statuser og ny innsending. |
| `specifications/sak-datamodell.md` | Oppdater berørt refusjonskardinalitet og vurderingsfelter. |
| `backend/.../event/event-flow.md` | Regenereres etter ny event og handler, ikke redigeres manuelt. |

Branch: `feat/vurdering-refusjonskrav` (Trello-kort 729).
Specen committes alene etter gjennomgang, før implementasjonen starter.

## Kanttilfeller og feilmodus

- To samtidige innsendinger: bare én får opprette nytt krav.
- To vurderinger før handleren kjører: bare én lagres og gir saksloggpost.
- Vurdering fra gammel side: et eldre krav kan ikke vurderes.
- Endret tildeling, kilde eller status: backend avviser selv om frontend
  ennå viser kontrollen.
- Nyeste sluttrapport mangler, er mottatt eller krever ny innsending:
  dokumentkontroll av refusjonen avvises.
- Nyeste krav er avvist, men eldre krav står som mottatt: det eldre
  kravet skal ikke gi oppgave eller åpne en kontroll.
- Reprosessering av eventen: ingen ekstra vurdering eller saksloggpost.
- Gammel frontend kan ikke sende inn på nytt, men skal fortsatt kunne
  lese eksisterende data. Backend og frontend deployes samlet for ny flyt.
- Migreringen kjører også i prod. Ruten monteres ikke der. Fjerningen
  av unikhetsregelen er likevel en reell schemaendring og må vurderes
  før merge. Bruk repoets lock-timeout-praksis for migreringen.

## Utenfor scope

- Varsel og oppgave via notifikasjonsplattformen.
- Påminnelse etter én uke. Ingen tekst skal love at påminnelsen sendes nå.
- Godkjenning, redusert beløp, avslag, beslutter og utbetaling.
- Klage, journalføring og nye kontonummeroppslag.
- Automatisk sletting av tidligere refusjonsvedlegg.
- Ny funksjonalitet i eksisterende Arena-visninger.

## Ferdig når

- [ ] Tildelt saksbehandler kan kreve ny innsending av nyeste mottatte krav.
- [ ] Nyeste sluttrapport må være bekreftet før dokumentkontrollen kan lagres.
- [ ] Minst én kjent årsak kreves; ingen fritekst godtas eller vises.
- [ ] Backend lagrer den forhåndsdefinerte beskjeden i eventen og på kravet.
- [ ] Handleren lagrer vurdering og gir én saksloggpost, også ved reprosessering.
- [ ] Refusjonsfanen blir stående etter vurderingen; mottatt-oppgaven forsvinner.
- [ ] Arbeidsgiver ser avvisningskort og beskjed, men ingen ansattmetadata.
- [ ] Skjemaet åpnes ved krav om ny innsending, ikke ved statusfeil.
- [ ] Ny innsending lager nytt krav og gir mottatt-oppgaven tilbake.
- [ ] Tidligere krav og vedlegg beholdes; beløps- og filkontroller gjelder fortsatt.
- [ ] Tester dekker tilgang, statuser, samtidighet, handler og begge frontend-flyter.
- [ ] Ingen varsler, påminnelser, vedtak eller utbetalinger utløses.
