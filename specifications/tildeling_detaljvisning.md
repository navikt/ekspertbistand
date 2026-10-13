# Tildeling av saksbehandler og beslutter i saksdetaljen

Trello: https://trello.com/c/qNWp34z2/634-tildeling-av-saksbehandler-og-beslutter-i-detaljvisningen
Kort-ID: `6acdf140701a0154a2783092`

## Mål

Saksdetaljen skal vise hvem som har oppgaven saken venter på, og la en kollega
overta den fra menyen:

- Venter saken på saksbehandler («Søknad»), viser linjen over saken
  «Saksbehandler {navn}». Menyen har «Overta som saksbehandler».
- Venter saken på beslutter («Søknad - beslutter»), viser linjen
  «Beslutter {navn}». Menyen har «Overta som beslutter».
- «Tildel meg» vises bare når ingen har oppgaven. Knappen står ikke lenger ved
  siden av navnet til en kollega.
- Er saken «Innvilget», «Avslått» eller «Avsluttet», vises verken
  saksbehandler, beslutter eller valg for å overta.

Backend avviser tildeling av saksbehandler når saken ikke venter på
saksbehandler, slik at knappen og API-et svarer likt.

## Bakgrunn

Fra før (`tildel_meg_sak.md`, `listevisning.md`):

- `SakPage.tsx` har komponenten `Tildeling` til høyre i linjen med
  «Tilbake til liste av saker», foran «Logg». Den viser:
  - «Saken er tildelt meg» og «Meny» med «Frigjør oppgave» når innlogget er
    saksbehandler,
  - «Saken er tildelt {navn}» og «Tildel meg» (når `kanTildeleMeg`) når en
    kollega er saksbehandler,
  - bare «Tildel meg» når saken ikke er tildelt.
- `Tildeling` ser bare på saksbehandler. Beslutter vises ikke på `SakPage`.
  `listevisning.md` holdt det utenfor (*Avgrensning*).
- `SakDetaljer` har allerede `oppgavetype`, `beslutterIdent`, `beslutterNavn`,
  `kanTildeleMeg` og `kanTildeleMegSomBeslutter`. I detaljoppslaget er
  `kanTildeleMeg = tildelingHindring(...) == null`, også når en kollega har
  saken, slik at en kollega kan overta.
- `useTildeling(sakId, { rolle })` tildeler som saksbehandler eller beslutter.
  Frigjøring finnes bare for saksbehandler.
- `tildelingHindring` avviser bare `AVSLATT` og `AVSLUTTET`. `INNVILGET` og
  `TIL_BESLUTNING` kan tildeles en saksbehandler (Ken, 2026-10-07, se
  *Beslutninger*). `tildelSak` endrer ikke status for disse.
- `beslutterTildelingHindring` krever `TIL_BESLUTNING` og at innlogget ikke
  sendte det foreløpige vedtaket.
- `oppgavetype(status)` gir `SOKNAD` for `OPPRETTET` og `UNDER_BEHANDLING`,
  `SOKNAD_BESLUTTER` for `TIL_BESLUTNING`, og `null` ellers. `harOppgaveFor`
  kobler `SOKNAD` til rollen saksbehandler og `SOKNAD_BESLUTTER` til rollen
  beslutter.
- Oppgavetypene «Søknad - retur», «Refusjon» og «Refusjon - beslutter» finnes
  ikke ennå. De kommer med beslutterflyten og kortene for refusjon.

## Skisser

Fil: `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand). Lest med Figma MCP 2026-10-13.

| Node | Lenke | Viser |
|------|-------|-------|
| `836:28667` | [overta som saksbehandler](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=836-28667) | Saksdetaljen sett av en beslutter. Til høyre i linjen med «Tilbake til liste av saker»: teksten «Saksbehandler {navn}», så «Meny» (åpen), så «Logg». Menyen har gruppen «Legg behandlingen tilbake» med «Overta som saksbehandler», og gruppen «Avslutt behandling» med «Avvis søknad» og «Trekk søknad». Gruppene har chevron. |
| `836:28943` | [overta som beslutter](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=836-28943) | Samme side og meny, men teksten er «Beslutter {navn}» og valget er «Overta som beslutter». |

Skissene har realistiske testdata (navn, fødselsnummer). De er ikke kopiert hit.

Avvik mellom kort og skisse:

- **«Avslutt behandling».** Skissene har gruppen med «Avvis søknad» og
  «Trekk søknad». Kortet nevner den ikke, og `tildel_meg_sak.md` la den til et
  senere kort. Vi bygger den ikke nå.
- **Sammenleggbare grupper.** Aksel `ActionMenu.Group` kan ikke legges sammen.
  Vi dropper chevronene på gruppene, som i `tildel_meg_sak.md`.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | «Tildel meg» vises bare når saken ikke er tildelt noen. Å overta flyttes til menyen. |
| Kortbeskrivelsen; Figma `836:28667` | Oppgavetypene «Søknad», «Søknad - retur» og «Refusjon» viser saksbehandler. Menyen har «Overta som saksbehandler». |
| Kortbeskrivelsen; Figma `836:28943` | Oppgavetypene «Søknad - beslutter» og «Refusjon - beslutter» viser beslutter. Menyen har «Overta som beslutter». |
| Kortbeskrivelsen | Saker med status «Innvilget», «Avslått» eller «Avsluttet» viser verken saksbehandler eller beslutter, og kan ikke overtas fra menyen. |
| Ken, 2026-10-07 (`tildel_meg_sak.md`) | `INNVILGET`-saker kan tildeles og overtas, fordi saken venter på refusjonskravet. **Overstyrt av kortet over**, som er nyere (opprettet 2026-10-13). Når refusjon får egen oppgavetype, kan en innvilget sak med refusjonskrav overtas igjen. |
| Ken, 2026-10-13 | Innvilgede saker kan ikke tildeles eller overtas før refusjonskortet gir dem oppgavetypen «Refusjon». Backend avviser også, slik at knappen og API-et svarer likt. |
| Ken, 2026-10-13 | «Frigjør oppgave» skjules i frontend når saken ikke venter på saksbehandler. `DELETE …/tildeling` endres ikke. |
| Ken, 2026-10-13 | Teksten er «Saksbehandler {navn}» også når innlogget har saken. |
| `tildel_meg_sak.md` | «Frigjør oppgave» og navigering til oversikten etter frigjøring står. «Avvis søknad» og «Trekk søknad» kommer i et senere kort. |
| `listevisning.md` | Frigjøring av beslutter og «Tildel beslutter» (tildele en annen) er ikke bygd. |
| Ken, 2026-10-06 (`tildel_meg_sak.md`) | API-et publiserer en event, og en handler gjør endringen. Saksloggen skrives via `SakOppdatert`. |
| Ken, 2026-10-10 (`forelopig_vedtak.md`) | Backend bruker testoppsettet som finnes, uten ny testteknologi. |

Kortet har ingen kommentarer og ingen sjekklister.

Valg i spec-en:

- **Rollen følger oppgavetypen.** Hvilken rolle linjen viser, utledes av
  `oppgavetype`, ikke av status. Når «Søknad - retur», «Refusjon» og
  «Refusjon - beslutter» kommer, holder det å koble dem til en rolle. Uten
  oppgavetype vises ingenting.
- **Teksten er «Saksbehandler {navn}» også når innlogget har saken.** Skissen
  bruker samme mønster for alle, og «Saken er tildelt meg» går ut. Det gir én
  regel for teksten. Ulempen er at innlogget må kjenne igjen sitt eget navn.
- **«Meny» vises bare når den har minst ett valg.** Har innlogget
  beslutteroppgaven selv, finnes det ikke noe valg (frigjøring av beslutter er
  ikke bygd), og «Meny» skjules.
- **Ingen bekreftelse før man overtar.** Skissen har ingen. Saksloggen viser
  hvem som tok over.
- **Etter «Overta» blir man stående på saken.** Bare «Frigjør oppgave» går til
  oversikten, som før.

## Avgrensning

- Gruppen «Avslutt behandling» («Avvis søknad», «Trekk søknad»).
- Oppgavetypene «Søknad - retur», «Refusjon» og «Refusjon - beslutter». De
  kobles til en rolle når de bygges, se *2*.
- Frigjøring av beslutter og «Tildel beslutter».
- `DELETE …/tildeling` (frigjøring av saksbehandler) endres ikke i backend.
  Frontend viser bare «Frigjør oppgave» når saken venter på saksbehandler.
- `POST …/tildeling/beslutter` og `beslutterTildelingHindring` endres ikke.
  Regelen krever allerede `TIL_BESLUTNING`.
- Handleren `TildelSaksbehandler` endres ikke. Se *Kanttilfeller*.
- Saksoversikten endres ikke, utover at `kanTildeleMeg` i listen følger den nye
  regelen i *1*.

## Tilnærming

```
SakDetaljer.oppgavetype ──> rolleForOppgave ──> saksbehandler | beslutter | ingen
                                                      │
                     ┌────────────────────────────────┴───────────────────┐
         «Saksbehandler {navn}»                                 «Beslutter {navn}»
         «Tildel meg» / Meny: Overta, Frigjør                   «Tildel meg» / Meny: Overta
                     │                                                    │
           POST|DELETE …/tildeling                           POST …/tildeling/beslutter
```

### 1. Backend: `tildelingHindring` følger oppgavetypen

🔴 Rød sone: regelen styrer hvem som kan ta en sak.

`Oppgavetype.kt` får rollen som hører til hver oppgavetype, og `harOppgaveFor`
bruker den:

```kotlin
val Oppgavetype.rolle: Role
    get() = when (this) {
        Oppgavetype.SOKNAD -> Role.SAKSBEHANDLER
        Oppgavetype.SOKNAD_BESLUTTER -> Role.BESLUTTER
    }

fun harOppgaveFor(oppgavetype: Oppgavetype?, principal: AzureAdPrincipal): Boolean =
    oppgavetype != null && principal.harRolle(oppgavetype.rolle)
```

`tildelingHindring` i `SakApi.kt` avviser når saken ikke venter på
saksbehandler:

```kotlin
fun tildelingHindring(
    status: Saksstatus,
    saksbehandlerIdent: String?,
    principal: AzureAdPrincipal,
): TildelingHindring? = when {
    !principal.harRolle(Role.SAKSBEHANDLER) -> TildelingHindring.MANGLER_ROLLE
    oppgavetype(status)?.rolle != Role.SAKSBEHANDLER -> TildelingHindring.UGYLDIG_STATUS
    saksbehandlerIdent == principal.navIdent -> TildelingHindring.HAR_SAKEN_ALLEREDE
    else -> null
}
```

`statuserSomIkkeKanTildeles` fjernes. Konsekvenser:

| Status | Før | Etter |
|--------|-----|-------|
| `OPPRETTET`, `UNDER_BEHANDLING` | kan tildeles | kan tildeles |
| `TIL_BESLUTNING` | kan tildeles | 409 `SAK_UGYLDIG_STATUS` |
| `INNVILGET` | kan tildeles | 409 `SAK_UGYLDIG_STATUS` |
| `AVSLATT`, `AVSLUTTET` | 409 | 409 |

`kanTildeleMeg` i listen og detaljoppslaget bruker samme funksjon og blir
`false` for `TIL_BESLUTNING` og `INNVILGET`. KDoc-en på `tildelingHindring`
peker hit.

### 2. Frontend: rollen for oppgaven

`useSaker.ts` får:

```ts
export const rolleForOppgave = (oppgavetype: Oppgavetype | null): Tildelingsrolle | null => {
  switch (oppgavetype) {
    case "SOKNAD":
      return "saksbehandler";
    case "SOKNAD_BESLUTTER":
      return "beslutter";
    default:
      return null;
  }
};
```

`Tildelingsrolle` flyttes fra `useTildeling.ts` til `useSaker.ts`, slik at
hooken og komponenten bruker samme type uten sirkulær import. Funksjonen
speiler `Oppgavetype.rolle` i backend. Nye oppgavetyper legges til begge
steder.

### 3. Frontend: `Tildeling`

`Tildeling` flyttes fra `SakPage.tsx` til `components/Tildeling.tsx`, fordi
den nå har to roller og to hooks. `SakPage` sender bare `sak` og en
`onError`, se *4*.

Komponenten kaller `useTildeling(sak.sakId)` og
`useTildeling(sak.sakId, { rolle: "beslutter" })`, og velger én ut fra
`rolleForOppgave(sak.oppgavetype)`.

Når rollen er **saksbehandler**:

| Tilstand | Tekst | Knapp | «Meny» |
|----------|-------|-------|--------|
| `saksbehandlerIdent === null` | – | «Tildel meg» når `kanTildeleMeg` | skjult |
| Innlogget er saksbehandler | «Saksbehandler {`saksbehandlerNavn`}» | – | «Frigjør oppgave» |
| En kollega er saksbehandler | «Saksbehandler {`saksbehandlerNavn`}» | – | «Overta som saksbehandler» når `kanTildeleMeg`, ellers skjult |

Når rollen er **beslutter**:

| Tilstand | Tekst | Knapp | «Meny» |
|----------|-------|-------|--------|
| `beslutterIdent === null` | – | «Tildel meg» når `kanTildeleMegSomBeslutter` | skjult |
| Innlogget er beslutter | «Beslutter {`beslutterNavn`}» | – | skjult |
| En kollega er beslutter | «Beslutter {`beslutterNavn`}» | – | «Overta som beslutter» når `kanTildeleMegSomBeslutter`, ellers skjult |

Når saken ikke har oppgavetype, returnerer komponenten `null`. Da står bare
«Logg» igjen.

Utforming, som i dag:

- Teksten er `BodyShort`. «Tildel meg» er `Button size="small" variant="secondary"`.
- «Meny» er `ActionMenu` med `HeaderKnapp` som trigger. `loading` følger
  `isSaving` for hooken som er i bruk.
- Valgene ligger i én `ActionMenu.Group` med label «Legg behandlingen
  tilbake», slik skissene viser.
- «Overta som saksbehandler» og «Overta som beslutter» kaller `tildelMeg()` i
  hooken for rollen. «Frigjør oppgave» kaller `frigjoer()` og går til
  oversikten ved OK-svar.
- Rekkefølgen i linjen er tekst, «Tildel meg» eller «Meny», så «Logg».

Hooken oppdaterer cachen optimistisk som før. Overtar man som saksbehandler,
blir teksten «Saksbehandler {mitt navn}» og menyen viser «Frigjør oppgave».
Overtar man som beslutter, forsvinner menyen.

`beslutterNavn` er null når `beslutterIdent` er null (CHECK fra V23). Teksten
viser derfor aldri «Beslutter null». Det samme gjelder saksbehandler (V20).

### 4. Frontend: feil

I dag sender `SakPage` `tildelingError` fra én hook og viser den i en `Alert`
under linjen. Med to hooks i `Tildeling` sender `SakPage` en `onError` til
komponenten og holder feilen i egen state. `useTildeling` har allerede
`onError`. `Alert`-en står der den står.

`feilmelding` i `useTildeling.ts` er uendret. 409 for saksbehandler gir
fortsatt «Saken kan ikke tildeles med den statusen den har nå.», og 403
`BESLUTTER_INHABIL` viser begrunnelsen fra backend.

### 5. MSW-mock

`mocks/handlers.ts`:

- `kanTildeles` speiler den nye `tildelingHindring`: bare `OPPRETTET` og
  `UNDER_BEHANDLING`.
- `POST …/tildeling` svarer 409 `SAK_UGYLDIG_STATUS` for andre statuser.
- Ny mock-sak i `TIL_BESLUTNING` der mock-brukeren er beslutter, for tilstanden
  uten «Meny». Vedtaket er sendt av `kollega`.

Mock-sakene dekker da alle radene i tabellene i *3*:

| Tilstand | Mock-sak |
|----------|----------|
| Søknad, ikke tildelt | `OPPRETTET` uten saksbehandler |
| Søknad, tildelt meg | `UNDER_BEHANDLING` tildelt mock-brukeren |
| Søknad, tildelt kollega | `UNDER_BEHANDLING` tildelt `kollega` |
| Beslutter, ikke tildelt | `…2012` |
| Beslutter, tildelt kollega | `…2013` (`mockBeslutter`) |
| Beslutter, tildelt meg | ny |
| Beslutter, sendt av meg | `TIL_BESLUTNING` med vedtak fra mock-brukeren (ingen «Tildel meg», `INHABIL`) |
| Ingen oppgave | `INNVILGET`, `AVSLATT`, `AVSLUTTET` |

### 6. Dokumentasjon

- `sak-datamodell.md`, avsnittet *Oppgavetype*: kolonnen «Rolle» i tabellen,
  og at tildeling av saksbehandler krever oppgavetype med rolle saksbehandler.
- `tildel_meg_sak.md`: raden om `INNVILGET` under *Beslutninger* får en
  henvisning hit.

### 7. Tester

Backend følger testoppsettet som finnes: `kotlin.test`,
`testApplicationWithDatabase`, `MockAzureAdIntrospector` og `QueuedEvents`.

- `OppgavetypeTest`: `rolle` for hver oppgavetype, og `harOppgaveFor` uendret.
- Ny `TildelingHindringTest` (ren enhetstest), etter
  `BeslutterTildelingHindringTest`: null for `OPPRETTET` og `UNDER_BEHANDLING`,
  `UGYLDIG_STATUS` for de andre statusene, `HAR_SAKEN_ALLEREDE` og
  `MANGLER_ROLLE`.
- `SaksbehandlingSakApiTest`:
  - `POST tildeling på INNVILGET sak tildelt en annen publiserer` blir
    `… gir 409 SAK_UGYLDIG_STATUS`, uten event.
  - Ny: `POST tildeling` på `TIL_BESLUTNING` gir 409 uten event.
  - Detaljoppslaget og listen: `kanTildeleMeg` er `false` for `TIL_BESLUTNING`
    og `INNVILGET`.
- `TildelSaksbehandlerTest` er uendret. Handleren endrer fortsatt ikke status.

Frontend har ikke testoppsett. `pnpm typecheck` og `pnpm lint` i
`frontend/saksbehandling`, og manuell sjekk mot MSW av hver rad i *5*.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Oppgavetype.kt` | `Oppgavetype.rolle`, `harOppgaveFor` bruker den |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SakApi.kt` | `tildelingHindring` følger oppgavetypen |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/OppgavetypeTest.kt` | `rolle` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/TildelingHindringTest.kt` | Ny |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/SaksbehandlingSakApiTest.kt` | `INNVILGET` og `TIL_BESLUTNING` gir 409 |
| `frontend/saksbehandling/src/hooks/useSaker.ts` | `Tildelingsrolle`, `rolleForOppgave` |
| `frontend/saksbehandling/src/hooks/useTildeling.ts` | Importerer `Tildelingsrolle` |
| `frontend/saksbehandling/src/components/Tildeling.tsx` | Ny, flyttet fra `SakPage.tsx` |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Bruker `Tildeling`, feil via `onError` |
| `frontend/saksbehandling/src/mocks/handlers.ts` | `kanTildeles`, 409, ny mock-sak |
| `specifications/sak-datamodell.md`, `specifications/tildel_meg_sak.md` | Se *6* |

## Kanttilfeller og feilmodus

- **Innlogget har begge rollene.** Linjen viser rollen saken venter på. En
  saksbehandler som sendte saken til beslutter, ser «Beslutter {navn}» eller
  «Tildel meg» (bare hvis habil, ellers ingenting) og mister «Frigjør oppgave»
  mens saken er hos beslutter.
- **Saksbehandleren vil frigjøre en sak i `TIL_BESLUTNING` eller `INNVILGET`.**
  Frontend tilbyr det ikke. `DELETE …/tildeling` godtar det fortsatt (Ken,
  2026-10-13).
- **Saken endrer status mens siden er åpen.** For eksempel sender
  saksbehandleren saken til beslutter mens en kollega har siden oppe. Kollegaen
  ser fortsatt «Overta som saksbehandler». Klikket gir 409, hooken ruller
  tilbake og revaliderer, og linjen viser beslutter.
- **Status endres mellom API og handler.** API-et sjekker status, men
  `TildelSaksbehandler` gjør det ikke. Sendes saken til beslutter i de få
  millisekundene mellom, får den en ny saksbehandler, men beholder status.
  Habiliteten til beslutter avhenger av `innstilt_av_ident`, ikke av
  saksbehandleren, så topartskontrollen holder. Vi godtar det.
- **To kolleger overtar samtidig.** Siste vinner, som før. Saksloggen får to
  linjer.
- **Tilgangsmaskinen avviser ved klikk.** «Overta» gir 403, og meldingen vises
  under linjen.
- **Arena-saker.** Får «Søknad» som oppgavetype og følger saksbehandlerreglene,
  som i listen. De når aldri `TIL_BESLUTNING`.
- **Personopplysninger.** Navn vises bare i UI. Ingenting nytt logges.

## Åpne spørsmål

Ingen. Ken besvarte spørsmålene om `INNVILGET`, frigjøring og teksten
2026-10-13. Svarene står under *Beslutninger*.

## Ferdig når

- [ ] Saksdetaljen viser «Saksbehandler {navn}» når saken venter på saksbehandler, og «Beslutter {navn}» når den venter på beslutter
- [ ] «Tildel meg» vises bare når ingen har oppgaven og innlogget kan ta den
- [ ] «Meny» har «Overta som saksbehandler» eller «Overta som beslutter» når en kollega har oppgaven og innlogget kan ta den
- [ ] «Meny» har «Frigjør oppgave» når innlogget er saksbehandler på en sak som venter på saksbehandler
- [ ] «Meny» vises ikke når den ikke har noen valg
- [ ] Saker uten oppgavetype («Innvilget», «Avslått», «Avsluttet») viser bare «Logg»
- [ ] `tildelingHindring` avviser `TIL_BESLUTNING` og `INNVILGET` med 409, og `kanTildeleMeg` følger
- [ ] Rollen for en oppgavetype er definert ett sted i backend (`Oppgavetype.rolle`) og speilet i `rolleForOppgave`
- [ ] Feil fra begge rollene vises under linjen
- [ ] MSW-mocken dekker hver tilstand i *3*
- [ ] `sak-datamodell.md` og `tildel_meg_sak.md` er oppdatert
- [ ] `mvn -B package` er grønn
- [ ] `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling` er grønne
