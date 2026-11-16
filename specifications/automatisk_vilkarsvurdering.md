# Automatisk vilkårsvurdering for arbeidsforhold og enighet

Trello: https://trello.com/c/WCMkpira/655
Kort-ID: `6ad5d4aee6934d1b253616e9`

## Mål

Vilkårene «Arbeidsforhold» og «Deltaker er enig» er forhåndsutfylt som
«Oppfylt» når en sak opprettes i ny saksbehandling. Begge viser taggen
«Automatisk» ved siden av statusen. Det samme gjelder eksisterende saker der
disse vilkårene ennå ikke er vurdert eller endret manuelt.

Saksbehandler kan overstyre vurderingen med dagens «Endre»-knapp. Enhver
vellykket manuell lagring fjerner «Automatisk», også når saksbehandler lagrer
«Oppfylt». Taggen kommer ikke tilbake ved senere manuelle endringer.

## Bakgrunn

- `OpprettSak.opprettVilkarForSak` oppretter i dag alle fem vilkår med
  `godkjent = null`. Eksisterende rader beholdes med `ignore = true`.
- `saksvilkar` har allerede `godkjent`, `notat`, `vurdert_av_ident` og
  `vurdert_tidspunkt`. Manuell lagring setter ident og tidspunkt, også ved
  nullstilling gjennom API-et.
- `hentVilkarsvurdering` returnerer de lagrede verdiene. Manglende rader
  returneres som ikke vurdert.
- `VilkårItem` viser status, redigering og opplysninger om hvem som vurderte
  vilkåret. Komponenten viser i dag «Vurdert av» også når ident og tidspunkt
  mangler.
- Frontend og backend krever at alle fem vilkår er vurdert før foreløpig
  vedtak kan sendes videre. Standardverdiene må derfor lagres, ikke bare
  vises som «Oppfylt» i frontend.
- Arena-saker behandles ikke i denne vilkårsvurderingen og skal ikke ha
  vilkårsrader. `SakProjection` endres ikke.

## Skisser

Figma-ramme `438:22366` i filen «Ekspertbistand», lest med Figma MCP
2026-11-16:
<https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=438-22366>.

Rammen viser vilkårsvurderingen i høyre panel. «Arbeidsforhold» og «Deltaker
er enig» har status «Oppfylt» og en «Endre»-knapp. «Deltaker er enig» har
også en nøytral «Automatisk»-tag ved siden av statusen. De øvrige vilkårene
har «Vurder»-knapp.

Skissen mangler «Automatisk» på «Arbeidsforhold». Avklaringen i denne
samtalen gjelder foran skissen: begge vilkår skal ha taggen.

## Beslutninger

Kortet har ingen kommentarer eller sjekklister (sjekket 2026-11-16).
Vedlegget er et skjermbilde. Figma-lenken i beskrivelsen er lest.

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | «Deltaker er enig» og «Arbeidsforhold» forhåndsutfylles som oppfylt. Saksbehandler kan overstyre, og «Automatisk» fjernes ved overstyring. |
| Avklaring i denne samtalen, 2026-11-16 | Begge vilkår får «Automatisk», slik kortet ber om, selv om skissen bare viser taggen på «Deltaker er enig». |
| Avklaring i denne samtalen, 2026-11-16 | Forhåndsutfyllingen gjelder også eksisterende saker der vilkårene ikke er vurdert. |
| Avklaring i denne samtalen, 2026-11-16 | Enhver vellykket manuell lagring fjerner taggen, også når verdien fortsatt er «Oppfylt». |

### Valg i spec-en

Disse kan endres ved review:

- Standardverdiene lagres som `godkjent = true`, uten ident, tidspunkt eller
  notat fra en saksbehandler. Vi bruker ikke en fiktiv systemident.
- Frontend utleder `automatisk` for de to aktuelle vilkårene når
  `godkjent = true` og både ident og tidspunkt mangler. Andre vilkår får aldri
  taggen. Vi trenger derfor ingen ny databasekolonne eller nytt API-felt.
  Dette forutsetter at manuelle vurderinger fortsatt lagrer ident og
  tidspunkt, slik handleren gjør i dag.
- Eksisterende urørte vilkår oppdateres med en datamigrering av
  `saksvilkar`. Eventloggen og tidligere vedtak endres ikke. Migreringen
  legger ikke inn manglende vilkårsrader.
- Et manuelt nullstilt vilkår regnes fortsatt som manuelt endret.
  Migreringen lar det stå, selv om `godkjent` er null, fordi ident eller
  tidspunkt viser at saksbehandler har gjort en vurdering.

## Avgrensning

- Bare `DELTAKER_HAR_ARBEIDSFORHOLD` og `FYLLES_UT_I_SAMRAD_GODKJENT`.
  De øvrige tre vilkårene er ikke vurdert som standard.
- Ingen nye oppslag mot Aa-registeret eller andre eksterne tjenester.
- Ingen endring i tilgangskontroll, krav til saksstatus eller hvem som kan
  endre vurderinger.
- Ingen endring i Arena-flyten, `SakProjection`, søknadsskjemaet eller
  eksisterende hjelpetekster.
- Ingen endring i eventformatene eller `SakDomain`. Eksisterende felt
  beholder vurderingen og hvem som eventuelt overstyrte den.

## Tilnærming

🔴 Rød sone: vilkårsvurdering er forretningslogikk. Gå grundig gjennom hvilke
vilkår som forhåndsutfylles, og at manuelle vurderinger aldri overskrives.

### 1. Standardverdier ved opprettelse

`OpprettSak.opprettVilkarForSak` setter `godkjent = true` for de to
vilkårene. Andre vilkår får fortsatt null. Ident, tidspunkt og notat er null
for alle urørte vilkår.

Behold idempotensen: et nytt kall skal ikke skrive over noen eksisterende
rad, heller ikke en manuelt nullstilt vurdering. Oppdater KDoc-en som nå
sier at alle vilkår opprettes som ikke vurdert.

### 2. Eksisterende saker

Ny datamigrering, foreløpig navn
`V37__automatisk_vilkarsvurdering.sql`. Bruk neste ledige versjon ved
implementasjon.

Migreringen setter bare `godkjent = true`, og bare på rader som oppfyller
alle disse kravene:

- Saken har kilde `EKSPERTBISTAND`.
- Vilkåret er ett av de to som forhåndsutfylles.
- `godkjent`, `notat`, `vurdert_av_ident` og `vurdert_tidspunkt` er alle null.

Ingen eksisterende godkjenning, avvisning, begrunnelse eller manuell
nullstilling endres. Migreringen endrer ikke de tre øvrige vilkårene eller
sakens status. Den kan kjøres på nytt uten å endre flere rader etter første
kjøring.

### 3. Visning og manuell lagring

I `hooks/useVilkår.ts` får frontend-modellen for vurderingen et avledet
`automatisk`-flagg. `tilVilkår` beregner flagget fra vilkårs-ID, godkjenning
og vurderingsmetadata. Statusen kommer fortsatt fra `dto.godkjent`, uten en
frontend-standardverdi.

I `VilkårItem` vises «Automatisk» ved siden av «Oppfylt» når flagget er satt.
Ikke vis en tom «Vurdert av»-linje for automatiske vurderinger. Manuelle
vurderinger beholder dagens visning av ident, tidspunkt og eventuell
kommentar.

Prosjektet bruker Aksel 8 (`@navikt/ds-react: ^8.16.1`). Bekreftet gjennom
Aksel MCP:

| Del | Komponent eller token |
|-----|----------------------|
| «Automatisk» | `Tag`, `variant="outline"`, `data-color="neutral"`, `size="small"` |
| Status og automatikk på samme rad | Eksisterende `HStack`, med `space-8` mellom taggene og linjebryting ved behov |

Ingen nye ikoner eller egne farger.

`useVilkårsvurdering` setter allerede tidspunkt og ident i SWR-dataene etter
et vellykket PATCH-kall. Den avledede taggen forsvinner dermed når lagringen
aksepteres. Kontroller at dette også gjelder når saksbehandler velger
«Oppfylt» uten å endre statusen. En avvist lagring eller «Avbryt» skal ikke
endre taggen.

### 4. Mock og dokumentasjon

- MSW-funksjonen `lagVilkårsvurdering` speiler standardverdiene i backend.
  Eksplisitte manuelle vurderinger i `vilkårStore` beholdes som manuelle.
- Oppdater `sak-datamodell.md`, særlig påstanden om at alle vilkår opprettes
  som ikke vurdert. Beskriv hvordan automatiske og manuelle vurderinger
  skilles med eksisterende felt.

### 5. Tester

- `OpprettSakTest`: akkurat de to vilkårene får `true`, de øvrige får null,
  og automatiske vurderinger mangler ident, tidspunkt og notat.
- Idempotens ved opprettelse bevarer manuelt godkjente, avviste og
  nullstilte vilkår.
- Migreringen testes mot urørte vurderinger, manuelle vurderinger og
  nullstillinger, andre vilkår og Arena-saker. Et nytt forsøk endrer
  ingenting.
- Backend-API-et returnerer de lagrede standardverdiene. Manuell lagring
  gjennom dagens eventhandler lagrer fortsatt ident og tidspunkt.
- Kontrollen før foreløpig vedtak godtar de to forhåndsutfylte vurderingene,
  men avviser fortsatt hvis ett av de andre vilkårene ikke er vurdert.
- Playwright med eksisterende MSW-oppsett dekker begge taggene, manuell
  lagring til begge utfall, avbrutt redigering og mislykket lagring.
  Kontroller også etter gjenåpning av siden at en manuell vurdering ikke
  får taggen tilbake.

## Berørte filer

Backend-stier er relative til `backend/src/main/`, frontend-stier til
`frontend/saksbehandling/`.

| Fil | Endring |
|-----|---------|
| `kotlin/no/nav/ekspertbistand/event/handlers/OpprettSak.kt` | Standardverdier og KDoc |
| `resources/db/migration/V37__automatisk_vilkarsvurdering.sql` | Oppdatere eksisterende urørte vilkår, versjonsnummer bekreftes ved implementasjon |
| `backend/src/test/.../event/handlers/OpprettSakTest.kt` | Standardverdier og idempotens |
| `backend/src/test/.../saksbehandling/VilkarsvurderingApiTest.kt` | Lagrede standardverdier |
| `backend/src/test/.../saksbehandling/ForelopigVedtakApiTest.kt` | Kontrollen med to automatiske vilkår |
| `backend/src/test/...` | Test av datamigreringen |
| `src/hooks/useVilkår.ts` | Avledet `automatisk`-flagg |
| `src/components/VilkårItem.tsx` | Tag og visning av vurderingsmetadata |
| `src/mocks/handlers.ts` | Standardverdier i MSW |
| `e2e/vilkarsvurdering.spec.ts` | Tester av visning og overstyring |
| `specifications/sak-datamodell.md` | Dokumentere standardverdiene |

## Kanttilfeller og feilmodus

- En manuell vurdering av «Oppfylt» skal ikke merkes som automatisk.
- Endring til «Ikke oppfylt» og senere tilbake til «Oppfylt» gjeninnfører
  ikke taggen.
- API-et støtter nullstilling selv om dagens UI ikke tilbyr det. Bevar
  vurderingsmetadata og «ikke vurdert» også etter opprettelses-retry og
  migrering.
- Et vilkår uten database-rad skal fortsatt være ikke vurdert. Verken API
  eller frontend skjuler manglende data med en standardverdi.
- Feiler lagringen, beholdes den automatiske vurderingen og dagens
  feilmelding vises. «Avbryt» endrer ingen vurdering.
- Automatiske vurderinger skal være forståelige uten farge. Tekstene
  «Oppfylt» og «Automatisk» vises begge, også når taggene må brytes over
  flere linjer.

## Åpne spørsmål

Ingen uavklarte krav fra kortet. Valgene om lagring og datamigrering over
inngår i review av denne spec-en.

## Ferdig når

- [ ] Nye saker får akkurat de to vilkårene forhåndsutfylt som oppfylt
- [ ] Eksisterende urørte vilkår får samme standardverdier, uten å endre manuelle vurderinger eller Arena-saker
- [ ] Begge vilkårene viser «Oppfylt» og «Automatisk» uten en tom «Vurdert av»-linje
- [ ] Enhver vellykket manuell lagring fjerner taggen, også ved uendret status
- [ ] Avbrutt eller mislykket lagring beholder taggen
- [ ] Gjenåpning av siden viser fortsatt riktig skille mellom automatisk og manuelt
- [ ] Kontrollen før foreløpig vedtak bruker de lagrede standardverdiene
- [ ] Mock, dokumentasjon og relevante tester er oppdatert
