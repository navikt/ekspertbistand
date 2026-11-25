# Nye vilkår og låsing av vilkårsvurdering ved beslutning

Trello: https://trello.com/c/hLd2jDAj/656
Kort-ID: `6ad5d6218d2e6ef78049ce39`

Trello: https://trello.com/c/eUsR2C7t/636
Kort-ID: `6acf2c21d77599058e567bec`

## Mål

Nye ekspertbistandssaker får tre nye vilkår i tillegg til dagens fem.
Saksbehandler vurderer dem manuelt før saken kan sendes til beslutning.
Eksisterende ekspertbistandssaker viser også de tre nye vilkårene.
Vi bruker samme åtte vilkår for alle saker, uten databasemigrering.

Når saken er `TIL_BESLUTNING`, kan beslutter lese vilkårsvurderingene,
men ikke endre dem. Frontend skjuler redigerings- og lagringshandlingene
og viser de lagrede vurderingene og begrunnelsene.

## Bakgrunn

- `Vilkar` i `saksbehandling/Db.kt` og `SakDomain.Vilkarsvurdering.Vilkar`
  har fem verdier. `TilDomain.kt` oversetter mellom dem med uttømmende `when`.
- `OpprettSak.opprettVilkarForSak` oppretter alle verdier fra `Vilkar.entries`.
  Den beholder eksisterende rader, men et nytt kall kan legge til nye
  enumverdier på en eksisterende sak.
- `hentVilkarsvurdering` returnerer alle enumverdier. Manglende rader vises
  som ikke vurdert. Bare å utvide enumen ville derfor gitt eksisterende saker
  tre nye vurderingskrav.
- `ForelopigVedtakApi` bruker `hentVilkarsvurdering` for å kontrollere at alle
  vilkår er vurdert. `hentSakDomain` bruker samme funksjon for vedtaksgrunnlaget.
- `VilkårItem` har ingen skrivebeskyttet modus. `SakPage` gir komponenten
  lagringsfunksjonen uten å sjekke status eller hvem som har saken.
- Backend krever allerede rollen `SAKSBEHANDLER`, tildeling til innlogget
  saksbehandler og status `UNDER_BEHANDLING` ved endring av vilkår.
- Arena-saker behandles i Arena og skal fortsatt ikke få vilkårsrader.

## Beslutninger

Begge kort er lest med kommentarer, sjekklister og vedlegg 2026-11-25.
Kortene har ingen kommentarer, sjekklister eller vedlegg.

| Kilde | Beslutning |
|-------|-----------|
| Kort 656 | Deltaker deltar ikke i andre arbeidsmarkedstiltak. |
| Kort 656 | Deltaker har ikke tidligere mottatt tilskudd til ekspertbistand for samme problemstilling. |
| Kort 656 | Innholdet i ekspertbistanden følger §§ 3 og 4 i forskriften. |
| Kort 636 | Lås feltene når saken er til beslutning. Fjern «Endre», fordi beslutter ikke skal kunne endre vilkårsvurderinger. |
| Siste avklaring i samtalen, 2026-11-25 | Løsningen er ikke i produksjon. Nye saker skal få de nye vilkårene; eksisterende saker håndteres på enkleste måte uten migrering. Dette erstatter den første avklaringen om at bare nye saker skulle få vilkårene. |
| Avklaring i samtalen, 2026-11-25 | Begge kort løses på samme branch, `feature/636-656-vilkarsvurdering`, i separate delproblemer med gjennomgang før hver commit. |
| Avklaring under implementasjon, 2026-11-25 | Frontend får ikke ansattroller fra API-et. Redigering styres etter status og tildeling i frontend; backend håndhever fortsatt rollen `SAKSBEHANDLER`. API-et utvides ikke for kort 636. |

### Valg i spesifikasjonen

Disse forslagene skal gjennomgås før implementasjon:

- Alle ekspertbistandssaker bruker de samme åtte vilkårene.
  Vi innfører ingen versjonskolonne, datoavgrensning eller migrering.
- Dagens lesefunksjon viser manglende vilkårsrader som ikke vurdert.
  Før en lovlig vurdering publiseres, oppretter backend manglende
  vilkårsrader med dagens idempotente opprettelsesfunksjon. Eksisterende
  vurderinger beholdes. GET-kall skal ikke skrive til databasen.
- Eksisterende saker under behandling må også få alle åtte vilkår vurdert
  før de sendes til beslutning. Saker som allerede er sendt eller avgjort,
  beholder status og tidligere vedtaksgrunnlag.
- De nye vilkårene opprettes som ikke vurdert, uten notat, ident eller
  vurderingstidspunkt. De får ikke automatisk godkjenning.
- Frontend tillater bare redigering når saken er `UNDER_BEHANDLING`,
  og innlogget ansatt er tildelt saken som saksbehandler.
  Backend håndhever i tillegg rollen `SAKSBEHANDLER`, som frontend ikke
  har opplysninger om. Tildeling som beslutter gir ikke redigeringstilgang.
- Vi viser begrunnelser som lesbar tekst i låst modus, ikke som deaktiverte
  tekstfelt. «Endre», «Vurder», «Oppfylt», «Ikke oppfylt» og «Avbryt»
  vises bare i redigerbar modus.

## Skisser og Aksel

Kortene og vedleggene inneholder ingen Figma-lenker. Vi beholder dagens
oppsett for vilkårsvurdering i høyre panel.

Frontend bruker Aksel 8 (`@navikt/ds-react` med versjonskrav `^8.16.1`).
Aksel MCP er brukt til å finne komponentene og lese dokumentasjonen for
`Accordion`, API-et for `Textarea` og `Link`, og tokenet `space-16`.
Accordion-dokumentasjonen beskriver 8.23.1. Ved implementasjon kontrollerer
vi versjonsavhengige props mot prosjektets installerte pakker.

- Behold dagens `Accordion` og åpne vilkårselementer. Låsing skal ikke
  hindre beslutter i å åpne eller lukke et element.
- Gjenbruk dagens visning av status, automatisk vurdering, begrunnelse og
  vurderingsmetadata. Ikke innfør egne komponenter for de tre nye vilkårene.
- Bruk dagens `Textarea` for begrunnelse når redigering er tillatt.
  MCP bekrefter at komponenten også har `readOnly`, men vi trenger ikke
  et tekstfelt i den låste visningen.
- Bruk `Link` med beskrivende lenketekst til forskriften. Behold understreking.
- Behold dagens avstander. `space-16` er bekreftet som `1rem`.
  Endringen krever ingen nye farger, ikoner eller CSS-verdier.

## Tilnærming

### 1. Spesifikasjon

Denne filen gjennomgås før implementasjon og committes alene:

`docs: spesifiser nye vilkår og låsing ved beslutning`

### 2. Skrivebeskyttet visning (kort 636)

`SakPage` utleder om den innloggede kan redigere vilkårene fra saksstatus
og `saksbehandlerIdent`, sammenlignet med innlogget ansatts ident.
Den sender dette til `VilkårItem`.

Komponenten viser alltid lagret status, begrunnelse og vurderingsmetadata
i låst modus. Et ikke vurdert vilkår vises som «Ikke vurdert», uten
«Vurder»-knapp. Urørte automatiske vurderinger viser fortsatt «Automatisk».

Hvis saken blir låst mens redigering er åpen, fjernes redigeringsfelt og
lagringshandlinger. Ulagret tekst må ikke dukke opp igjen ved senere
opplåsing. Ved retur til `UNDER_BEHANDLING` kan tildelt saksbehandler åpne
redigering på nytt med den lagrede begrunnelsen.

Frontend må også sjekke redigeringstilgangen ved lagringshandlingen.
Backend er fortsatt ansvarlig for tilgangskontroll og statuskontroll ved
samtidige endringer. En allerede sendt forespørsel avvises som i dag hvis
saken ikke lenger er under behandling.

Legg til Playwright-tester i dagens vilkårsvurderingstester for låst visning,
ulike tildelinger og statusendring under redigering. Returflyten skal
fortsatt gi saksbehandler redigeringstilgang.

Commit:
`fix(saksbehandling): lås vilkårsvurderinger til beslutning`

### 3. Nye vilkår i backend (kort 656)

Utvid `Vilkar` med tre verdier. Behold `Vilkar.entries` som felles utvalg
ved opprettelse og lesing. `saksvilkar.vilkar_id` er allerede tekst, så
endringen krever ingen nye kolonner eller migrering.

Foreslåtte nye enumverdier:

- `DELTAKER_DELTAR_IKKE_I_ANDRE_ARBEIDSMARKEDSTILTAK`
- `DELTAKER_HAR_IKKE_MOTTATT_EKSPERTBISTAND_FOR_SAMME_PROBLEMSTILLING`
- `EKSPERTBISTAND_FOLGER_FORSKRIFTEN`

Oppdater også domain-enumen og den uttømmende oversettelsen i `TilDomain.kt`.

`opprettVilkarForSak` oppretter automatisk alle åtte vilkår på nye saker.
Gjentatte kall legger til manglende rader uten å overskrive vurderinger.
Arena-saker skal fortsatt ikke få vilkårsrader.

`hentVilkarsvurdering` returnerer alle åtte vilkår i enum-rekkefølge.
En manglende rad returneres fortsatt som ikke vurdert. De tre nye vilkårene
legges sist, i rekkefølgen fra kortet.

Ved PATCH gjenbruker `publiserVilkarsvurdering` opprettelsesfunksjonen
før kontrollen av om vilkåret finnes. Dette skjer i samme transaksjon,
etter at saken er låst og statusen er kontrollert. Tilgangskontrollene
skal fortsatt være gjennomført før noe skrives. Kontroller også at kilden
er `EKSPERTBISTAND`, slik at denne initialiseringen ikke oppretter vilkår
på Arena-saker.

Dermed kan de nye vilkårene på en eksisterende sak vurderes uten
datamigrering eller manuell utfylling av databasen. Den asynkrone
lagringshandleren kan fortsatt kreve en eksisterende vilkårsrad.

Kontrollen i `ForelopigVedtakApi` og vedtaksgrunnlaget i `hentSakDomain`
bruker samme liste med åtte vilkår. Kravet er fortsatt at vilkårene er
vurdert, ikke at alle er oppfylt. Dagens avslagsflyt beholdes.

Test opprettelse, gjentatt opprettelse, manglende rader, lagring av hvert
nytt vilkår og mapping til vedtaksgrunnlaget. Dekk at en eksisterende sak
med fem lagrede vilkår kan få de nye vurderingene lagret uten å miste
tidligere vurderinger. Innsending avvises til alle åtte er vurdert.

Commit:
`feat(saksbehandling): legg til tre nye vilkår`

### 4. Frontendtekster og flyt (kort 656)

Utvid `VilkarId` og `VILKAR_TEKSTER` i `useVilkår.ts`.
Frontend viser de åtte vilkårene som backend returnerer.

Foreslåtte tekster:

| Tittel | Beskrivelse |
|--------|-------------|
| Andre arbeidsmarkedstiltak | Deltaker deltar ikke i andre arbeidsmarkedstiltak. |
| Tidligere ekspertbistand | Deltaker har ikke tidligere mottatt tilskudd til ekspertbistand for samme problemstilling. |
| Innholdet i ekspertbistanden | Innholdet i ekspertbistanden følger §§ 3 og 4 i forskriften. |

Den siste beskrivelsen får lenken
<https://lovdata.no/forskrift/2019-08-29-1105/%C2%A73>,
med lenketeksten «§§ 3 og 4 i forskriften». Ikke legg til egne juridiske
tolkninger eller nye oppslag mot eksterne registre.

Oppdater MSW-mockene og lokale backend-testdata til åtte vilkår.
Behold testdekning av eksisterende saker der de tre nye radene ennå
ikke er opprettet i databasen.

Playwright-testene dekker de nye tekstene, forskriftslenken, vurdering
med begrunnelse, sperre før alle vilkår er vurdert og låst visning hos
beslutter. Oppdater berørte vedtaks- og tilgjengelighetstester.

Commit:
`feat(saksbehandling): vis tre nye vilkår`

## Berørte filer

Backend-stier under `backend/src/main/kotlin/no/nav/ekspertbistand/`:

| Fil | Endring |
|-----|---------|
| `saksbehandling/Db.kt` | Nye enumverdier og oppdatert dokumentasjon om nye vilkår. |
| `event/handlers/OpprettSak.kt` | Gjenbruk dagens idempotente opprettelse av alle vilkår. Tilpass bare hvis gjenbruk fra API-et krever det. |
| `saksbehandling/VilkarsvurderingApi.kt` | Oppretter manglende vilkårsrader før en lovlig vurdering publiseres. |
| `event/domain/SakDomain.kt` | Nye vilkårsverdier i vedtaksgrunnlaget. |
| `event/domain/TilDomain.kt` | Oversettelse av de nye vilkårsverdiene. |
| `saksbehandling/ForelopigVedtakApi.kt` | Behold dagens felles lesefunksjon for vurderingskravet. |

Øvrige filer:

| Fil | Endring |
|-----|---------|
| `backend/src/test/kotlin/no/nav/ekspertbistand/` | Berørte opprettelses-, API- og vedtaksgrunnlagstester, samt lokale testdata. |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Utleder redigeringstilgang og sender den til vilkårskomponenten. |
| `frontend/saksbehandling/src/components/VilkårItem.tsx` | Låst visning, statusendring under redigering og forskriftslenke. |
| `frontend/saksbehandling/src/hooks/useVilkår.ts` | Nye typer, tekster og nødvendig lenkedata. |
| `frontend/saksbehandling/src/mocks/handlers.ts` | Mocker for gamle og nye saker. |
| `frontend/saksbehandling/e2e/` | Vilkårsvurdering, vedtaksflyt og berørte tilgjengelighetstester. |
| `specifications/sak-datamodell.md` | Dokumenterer åtte vilkår og opprettelse av manglende rader ved vurdering. |

## Kanttilfeller og feilmodus

- Eksisterende saker får også de tre nye vilkårene i visningen. Etter retur
  fra beslutter må alle åtte vurderes før saken sendes på nytt.
- Mangler en vilkårsrad, vises den som ikke vurdert og hindrer innsending
  til beslutning. Raden opprettes ved en lovlig vurdering, ikke ved lesing.
- Har innlogget ansatt begge roller, gjelder tildeling og status.
  Beslutterrollen skal ikke låse en sak vedkommende lovlig behandler som
  tildelt saksbehandler.
- Status eller tildeling kan endres mens siden er åpen. Frontend fjerner
  redigeringsmuligheten når den mottar oppdaterte saksdata. Backend
  avviser ulovlige endringer selv om frontend ennå ikke har mottatt dem.
- Backend og frontend må deployes som et koordinert sett. Gammel frontend
  filtrerer bort ukjente vilkår og kan derfor ikke fullføre alle vurderingene.
  Ikke rollback backend til en versjon som ikke kjenner de nye vilkårene
  etter at nye vilkårsvurderinger er lagret.
- Arena-projeksjonen får ingen versjonsbump eller nye vilkår. Tidligere
  eventer og vedtaksgrunnlag skrives ikke om.

## Utenfor scope

- Databasemigrering, vilkårssettversjoner og særregler for eldre saker.
- Automatiske vurderinger av de tre nye vilkårene.
- Endringer i søknadsskjemaet eller arbeidsgiver-frontenden.
- Nye registeroppslag eller juridiske regler utover teksten i kortet.
- Endringer i beslutters godkjenning, retur eller backend-tilgangsreglene.

## Ferdig når

- [ ] Nye ekspertbistandssaker får åtte vilkår; de tre nye er ikke vurdert.
- [ ] Eksisterende ekspertbistandssaker viser åtte vilkår og kan lagre de nye vurderingene uten migrering.
- [ ] Gjentatt opprettelse beholder alle manuelle vurderinger.
- [ ] GET-kall oppretter ikke vilkårsrader.
- [ ] Frontend og backend krever at alle åtte vilkår er vurdert før innsending til beslutning.
- [ ] Vedtaksgrunnlaget inneholder de samme vilkårene og vurderingene som saken.
- [ ] Nye vilkår kan vurderes som oppfylt og ikke oppfylt, med dagens begrunnelsesflyt.
- [ ] Forskriftsvilkåret viser lenken til §§ 3 og 4.
- [ ] Beslutter kan lese alle vurderinger, begrunnelser og metadata uten redigerings- eller lagringshandlinger.
- [ ] Statusendring under redigering fjerner redigeringsfeltet og hindrer ny lagring.
- [ ] Tildelt saksbehandler kan redigere igjen etter retur til `UNDER_BEHANDLING`.
- [ ] Arena-saker og de to eksisterende automatiske vurderingene oppfører seg som før.
- [ ] Berørte backend- og Playwright-tester dekker både gamle og nye saker.
