# Kategorisere saker etter virksomhetstype

Trello: https://trello.com/c/Yq3L6At8/737-kategorisering-av-saker-etter-virksomhetstype
Kort-ID: `6b056f50d695207b18a11889`

Henger sammen med:

- [ruting_egne_ansatte_og_kommune.md](ruting_egne_ansatte_og_kommune.md), del 2.
  Kommunefilteret og kolonnen «Kommune» er mønsteret vi følger.
- [listevisning.md](listevisning.md). Lista over saker som får ny kolonne og
  nytt filter.

## Mål

Saksbehandler ser i saksoversikten om arbeidsgiveren er statlig, kommunal eller
privat, og kan filtrere lista på det.

Vi lagrer virksomhetens sektorkode fra Ereg på saken når saken opprettes.
Backend mapper sektorkoden til en virksomhetstype når lista hentes. Da kan vi
endre kategoriseringen uten å røre lagrede data. Saker der sektorkoden mangler
eller ikke kan mappes, får typen «Ukjent».

## Bakgrunn

- **Sektorkode** er SSBs institusjonelle sektorkode. Brønnøysundregistrene
  registrerer den på juridiske enheter og organisasjonsledd, og Ereg gir den
  videre. Kodene står i Navs felles kodeverk
  [Sektorkoder](https://kodeverk.ansatt.nav.no/kodeverk/Sektorkoder).
- **Søknaden har virksomhetsnummeret til underenheten** (`BEDR`). Underenheten
  har ingen sektorkode. Den må hentes fra juridisk enhet på toppen av kjeden.
- **Ereg** (`EregClient.hentOrganisasjon`, `GET /v2/organisasjon/{orgnr}`) har
  feltene `juridiskEnhetDetaljer.sektorkode` og
  `organisasjonsleddDetaljer.sektorkode` i modellen allerede. Ingen kode leser dem
  i dag.
- **Undersøkt mot Ereg i dev** (`ereg-services.dev-fss-pub.nais.io`, 2026-11-25):
  - Uten `inkluderHierarki=true` har en `BEDR` ingen lenke til enheten over.
  - Med `inkluderHierarki=true` har en vanlig `BEDR` juridisk enhet i
    `inngaarIJuridiskEnheter`. En statlig `BEDR` har i stedet organisasjonsleddet
    i `bestaarAvOrganisasjonsledd` (for eksempel Nav Lørenskog under Nav
    Akershus). Detaljene, også sektorkoden, er ikke med for enheten over.
  - Sektorkoden ligger på enheten over: `juridiskEnhetDetaljer.sektorkode` for
    juridiske enheter (`KOMM` 6500, `ENK` 8200, `STI` 2100) og
    `organisasjonsleddDetaljer.sektorkode` for organisasjonsledd (`ORGL` 6100).
  - Flere syntetiske `AS` i dev mangler sektorkode.
  - Enhetene over har bare orgnr og navn, ingen detaljer.
- **Ereg-koden, lest 2026-11-25** (`navikt/ereg-services` og
  `navikt/ereg-ajourholdsfiler-lasting`). Vi har sjekket om sektorkoden settes
  på vilkår, slik poststed gjør. Poststed fylles bare fra `poststedUtenlandsk`
  (`RsProviderOrganisasjonV2Mapper.map(ForretningsAdresse)`). Sektorkoden har
  ingen slike vilkår:
  - V2-mapperen kopierer `sektorkode` fra gjeldende detaljrad
    (`datoBrukTil IS NULL`) for både juridisk enhet og organisasjonsledd. Den
    sjekker bare null langs veien.
  - Lastejobben (`SektorkodeProcessor`) skriver sektorkoden fra Brønnøysunds
    ajourholdsfiler for alle juridiske enheter og organisasjonsledd, uten filter
    på enhetstype. Den nuller koden bare når Brønnøysund melder feltet som
    utgått.
  - Andre felt på samme detaljrad for juridisk enhet (`harAnsatte`,
    `registrertStiftelsesregisteret`) lager ny rad ved endring. Kopien tar med
    sektorkoden (`JuridiskEnhetDetaljerTeknisk.createFunctionalClone`).
  - Kopien for organisasjonsledd tar ikke med sektorkoden. Den brukes bare av
    `SektorkodeProcessor`, som setter koden rett etterpå, så det gir ikke tap i
    dag. Det er et svakt punkt hvis andre felt flyttes inn på samme rad.
  - Når en `BEDR` slås opp, er hverken `juridiskEnhetDetaljer` eller
    `organisasjonsleddDetaljer` med, fordi `BEDR` ikke har sektorkode.
- **`inkluderHierarki=true` gir hele kjeden til toppen.**
  `AbstractOrganisasjonService.loadOverordnedeEnheterForOrgledd` kaller seg selv
  for hvert gjeldende `organisasjonsleddOver` og stopper ved juridisk enhet
  (`inngaarIJuridiskEnheter`). Hver enhet over bygges med
  `getOrganisasjonWithOrganisasjonsnummerAndNavn` og har bare orgnr og navn.
  Sektorkoden på toppen krever derfor ett kall til. Det finnes ikke vern mot
  sykluser.
- **Prøvd mot Ereg i dev med flere nivåer** (Ken, 2026-11-25). NAV IKT
  (990983666, `BEDR`) med `inkluderHierarki=true` gir kjeden nestet:
  `bestaarAvOrganisasjonsledd[0].organisasjonsledd` er NAV IKT (990983291,
  `ORGL`), dens `organisasjonsleddOver[0].organisasjonsledd` er Arbeids- og
  velferdsetaten (889640782, `ORGL`), og dens `inngaarIJuridiskEnheter[0]` er
  Arbeids- og sosialdepartementet (983887457). Enhetene over har bare orgnr, type
  og navn, uten detaljer og sektorkode. Juridisk enhet i
  `inngaarIJuridiskEnheter` har ikke `type`.
- **Organisasjonsledd eller juridisk enhet?** Vi har gått gjennom alle 1600
  `ORGL` i Brønnøysundregistrene (2026-11-25), fulgt kjeden til toppen og
  sammenlignet sektorkoden:
  - 1295 har 6500 og ligger under `KOMM` eller `FYLK`. 300 har 6100 og ligger
    under `STAT`.
  - 1593 har samme kode som toppen.
  - 1491 ligger rett under toppen, 89 har to nivåer `ORGL` og 19 har tre.
  - Av de sju med avvik har fem en mer presis statlig kode enn departementet over
    seg: Husbanken og Lånekassen har 3900, SDØE har 1110, og Eksportfinansiering
    Norge har 4900 («øvrige finansielle foretak»). Svalbards miljøvernfond mangler
    kode. De to siste ligger under et `ORGL` som er slettet.
  - Med koden fra nærmeste `ORGL` ville Eksportfinansiering Norge blitt privat,
    og Svalbards miljøvernfond ukjent. Med koden fra toppen blir alle sju
    statlige eller kommunale.

  Vi bruker derfor alltid sektorkoden fra toppen av kjeden.
- **Ekte enheter i Brønnøysundregistrene** (`data.brreg.no`, 2026-11-25), for å
  sjekke mappingen. De fleste finnes ikke i Ereg i dev.

  | Enhet | Form | Sektorkode |
  |-------|------|-----------|
  | Arbeids- og velferdsetaten | `ORGL` | 6100 |
  | Bergen kommune | `KOMM` | 6500 |
  | Vestland fylkeskommune | `FYLK` | 6500 |
  | Oslo universitetssykehus HF | `SÆR` | 6100 |
  | Equinor ASA | `ASA` | 1120 |
  | Redd Barna | `FLI` | 7000 |
  | REMA 1000 Norge AS | `AS` | 2100 |

  Helseforetak og fylkeskommuner får altså 6100 og 6500.
- **`OpprettSak`** slår opp adressebeskyttelse i PDL og skjerming i
  `skjermede-personer-pip` før transaksjonen. Feiler et oppslag, gir handleren
  transient error, og saken opprettes ikke før oppslaget lykkes. Svarene lagres
  på saken og står i `SakOpprettet`.
- **Kommunen** kommer fra rutingen i `JournalfoerInnsendtSoknad`, går gjennom
  `InnsendtSoknadJournalfoert.virksomhetKommunenummer` og lagres som
  `sak.kommunenummer` av `OpprettSak`.
- **Lista** (`GET /api/saksbehandling/v1/saker`, `SakListeElement`) skjuler
  arbeidsgiver, kommune og deltaker på rader uten tilgang (`utenDeltaker`).
  Frontend viser alltid disse radene, også når kommunefilteret er satt
  (`brukKommunefilter`).
- **Kommunefilteret** (`Kommunefilter.tsx`) er en `UNSAFE_Combobox` med
  flervalg, der tomt felt betyr alle. Valget lagres per saksbehandler i
  `saksbehandler_kommunefilter`. Komponenten vises ikke når saksbehandler har én
  enhet og ingen saker har kommune.
- **Arena-saker** lages i prod av `SakProjection` og har verken kommune eller
  andre data fra `OpprettSak`.

## Skisser

Figma-fil `lvEwrzOupYmavMI2QmsMPI`, lest med Figma MCP 2026-11-25.

| Tilstand | Ramme | Lenke |
|----------|-------|-------|
| Saksoversikt med filter og kolonne | `695:23735` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=695-23735> |
| Kolonnen «Type virksomhet» | `1273:11630` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1273-11630> |

- **Filteret** står over fanene som tredje felt i raden «Enhet», «Kommune»,
  «Type virksomhet». Det er en nedtrekksliste med samme utseende som
  kommunefeltet. Skissen viser ikke valgene.
- **Kolonnen** «Type virksomhet» står mellom «Arbeidsgiver» og «Deltakere». Hver
  rad har en tag: «Kommunal», «Privat» eller «Statlig». Lagene heter
  «Type virksomhet: Kommunal» osv.
- **Fargene** (lest av skjermbildet): «Kommunal» er lilla, «Privat» blå og
  «Statlig» gulgrønn.
- Skissen viser ikke rader der typen er ukjent, eller rader uten tilgang.

## Beslutninger

Kortet hadde to kommentarer da spec-en ble skrevet (2026-11-25), ingen
sjekklister, og ett vedlegg (`image.png`). Ifølge Ken er vedlegget ikke
relevant for kortet.

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Sakene kategoriseres etter arbeidsgivers virksomhetstype: statlig, kommunal eller privat. |
| Kortbeskrivelsen | Virksomhetstypen vises som tag i saksoversikten, og lista kan filtreres på den. |
| Kortbeskrivelsen | Virksomheter der typen ikke kan fastslås, må håndteres. |
| Jonas, 2026-11-24 (kommentar) | Lenke til `hentOrganisasjon` i Ereg. Kilden er Ereg. |
| Ken, 2026-11-25 (kommentar) | Vi bruker `sektorkode` på juridisk enhet fra Ereg og mapper koden til kategori. |
| Ken, 2026-11-25 (kommentar) | Vi lagrer sektorkoden på saken og har en mapping fra kode til kategori, så vi kan endre kategoriseringen ved behov. |
| Ken, 2026-11-25 (review av spec) | Statlig eide aksjeselskaper (1120), ideelle organisasjoner (7000) og utlandet (9000) er privat. |
| Ken, 2026-11-25 (review av spec) | Filteret lagres per saksbehandler, som kommunefilteret. |
| Ken, 2026-11-25 (review av spec) | Ingen backfill. Eksisterende saker og Arena-saker får «Ukjent». |
| Ken, 2026-11-25 (review av spec) | Vedlegget `image.png` på kortet er ikke relevant. |
| Ken, 2026-11-25 (review av spec) | Sjekk at Ereg alltid gir sektorkoden, og at koden på organisasjonsledd er god nok. Resultat: Ereg setter den uten vilkår, men vi må gå til juridisk enhet på toppen. Se *Bakgrunn*. |
| Ken, 2026-11-25 (review av spec) | Bruk `inkluderHierarki=true`. Det gir hele kjeden, men uten sektorkode, så det blir to kall. |
| Ken, 2026-11-25 (review av spec) | Virksomhetsnummeret er alltid en underenhet (`BEDR`). |
| Ken, 2026-11-25 (review av spec) | `saksbehandler_kommunefilter` finnes bare i dev. Tabellen blir `saksbehandler_saksfilter`, og endepunkt og klasser får tilsvarende navn. |
| Ken, 2026-11-25 (review av spec) | Finner vi ikke sektorkoden, logger vi `log.error` uten orgnr, med henvisning til `teamLog.error` med detaljene. Orgnr maskeres i vanlig logg. |
| Minne: eventmodell | Data til visning lagres i egen tabell av en handler. API-er slår ikke opp i `event_log`. |
| Minne: eventmodell | `SakProjection` backfiller bare Arena-saker. Funksjonalitet for nye saker skal ikke inn i den. |
| Minne: kodekonvensjoner | Nye parametere får ingen standardverdi. Unntaket er felt i eventer, der gamle eventer må kunne leses. |

### Valg i spec-en

Disse står ikke i kortet:

- **`OpprettSak` slår opp sektorkoden**, ved siden av PDL og skjerming. Feiler
  Ereg, gir handleren transient error, og saken venter. Alternativet var å slå
  opp i `JournalfoerInnsendtSoknad` sammen med kommunen. Det ville gjort
  journalføringen avhengig av et oppslag den ikke trenger.
- **Sektorkoden hentes alltid fra toppen av kjeden**, som er juridisk enhet
  (`STAT`, `KOMM`, `FYLK`, `AS` osv.), ikke fra nærmeste organisasjonsledd. Det
  følger Kens kommentar og gir riktig type for alle `ORGL` i
  Brønnøysundregistrene, se *Bakgrunn*.
- **To kall mot Ereg: `inkluderHierarki=true` og så toppen.** Enhetene over har
  ikke sektorkode i svaret, så to kall er minimum. `inkluderHierarki` er
  dokumentert i Swagger og allerede modellert i `EregClient`, så vi slipper ny
  klientkode. Vi går selv ned i den nestede strukturen og stopper etter ti nivåer
  for å unngå uendelig løkke. Alternativet var å følge kjeden med ett kall per
  nivå, opptil fire kall.
- **Mappingen ligger i backend**, og frontend får bare typen. Sektorkoden sendes
  ikke til frontend.
- **Typen skjules på rader uten tilgang**, som arbeidsgiver og kommune. Radene
  vises alltid, uansett filter.
- **Filteret har fire faste valg** i fast rekkefølge: Statlig, Kommunal, Privat,
  Ukjent. Flervalg, og tomt felt betyr alle, som kommunefeltet.
- **Filteret lagres sammen med kommunefilteret, og vi gir det nytt navn.**
  Tabellen blir `saksbehandler_saksfilter` med ny kolonne for typene, og
  endepunktet blir `/meg/saksfilter`. Tabellen er bare i bruk i dev, så vi kan
  endre navnet med en migrering uten å kopiere data. Alternativet med egen tabell
  og eget endepunkt gir to lagringer per endring i filterraden.
- **Fargene følger skissen**: `meta-purple` for kommunal, `info` for privat,
  `meta-lime` for statlig og `neutral` for ukjent. `meta-purple` og `meta-lime`
  brukes også i oppgavemerkene for refusjon og sluttrapport.

## Avgrensning

- Ingen backfill av eksisterende saker eller Arena-saker. De får «Ukjent». I prod
  er alle saker Arena-saker fram til lansering.
- Sektorkoden oppdateres ikke etter at saken er opprettet.
- Typen vises ikke på saksdetaljen, i brev eller i statistikken.
- Ingen hurtigfilter ved klikk på taggen, slik kommunen har.
- Ingen endring i `SakProjection`.

## Tilnærming

### 1. Mapping fra sektorkode til virksomhetstype

Ny fil `saksbehandling/Virksomhetstype.kt`:

```kotlin
@Serializable
enum class Virksomhetstype {
    STATLIG, KOMMUNAL, PRIVAT, UKJENT;

    companion object {
        fun fraSektorkode(sektorkode: String?): Virksomhetstype = when (sektorkode) {
            "1110", "3100", "3900", "6100" -> STATLIG
            "1510", "1520", "6500" -> KOMMUNAL
            "1120", "2100", "2300", "2500", "3200", "3500", "3600", "4100",
            "4300", "4500", "4900", "5500", "5700", "7000", "8200", "8300",
            "9000" -> PRIVAT
            else -> UKJENT
        }
    }
}
```

| Kode | Sektor | Type |
|------|--------|------|
| 1110 | Statens forretningsdrift | Statlig |
| 1120 | Statlig eide aksjeselskaper mv. | Privat |
| 1510 | Kommunale foretak med ubegrenset ansvar | Kommunal |
| 1520 | Kommunale aksjeselskaper mv. | Kommunal |
| 2100 | Private aksjeselskaper mv. | Privat |
| 2300 | Personlige foretak | Privat |
| 2500 | Private produsentorienterte organisasjoner uten profittformål | Privat |
| 3100 | Norges Bank | Statlig |
| 3200 | Banker | Privat |
| 3500 | Kredittforetak | Privat |
| 3600 | Finansieringsselskaper | Privat |
| 3900 | Statlige låneinstitutter mv. | Statlig |
| 4100 | Finansielle holdingselskaper | Privat |
| 4300 | Verdipapirfond | Privat |
| 4500 | Investeringsselskaper og aktive eierfond / PE-fond | Privat |
| 4900 | Øvrige finansielle foretak unntatt forsikring | Privat |
| 5500 | Livsforsikringsselskaper og pensjonskasser | Privat |
| 5700 | Skadeforsikringsselskaper | Privat |
| 6100 | Stats- og trygdeforvaltningen | Statlig |
| 6500 | Kommuneforvaltningen | Kommunal |
| 7000 | Ideelle organisasjoner | Privat |
| 8200 | Personlig næringsdrivende | Privat |
| 8300 | Borettslag o.l. | Privat |
| 8500 | Lønnstakere, pensjonister, trygdede, studenter o.a. | Ukjent |
| 9000 | Utlandet | Privat |
| Mangler, eller annen kode | | Ukjent |

### 2. Ereg: hente sektorkoden

- `EregClient` får `hentOrganisasjonMedHierarkiEllerNull(orgnr)`: som
  `hentOrganisasjonEllerNull`, med `inkluderHierarki=true`. Modellen
  (`OrganisasjonResponse`) har allerede de nestede feltene.
- `EregService` får `suspend fun hentSektorkode(virksomhetsnummer: String): String?`.
  Virksomhetsnummeret på søknaden er alltid en underenhet (`BEDR`), så den gjør
  alltid to kall:
  1. Hent virksomheten med hierarki. 404 gir null.
  2. Finn toppen ved å gå oppover i svaret:
     - Har noden `inngaarIJuridiskEnheter`, er toppen den første juridiske
       enheten der.
     - Ellers, har noden `bestaarAvOrganisasjonsledd` (`BEDR`) eller
       `organisasjonsleddOver` (`ORGL`), gå videre til første
       `organisasjonsledd`.
     - Ellers er noden toppen. For et `ORGL` betyr det at kjeden er brutt. En
       `BEDR` uten enhet over gir null.
     - Stopp og gi null etter ti nivåer.
  3. Hent toppen med `hentOrganisasjonEllerNull`. 404 gir null.
  4. Bruk `juridiskEnhetDetaljer.sektorkode`, eller
     `organisasjonsleddDetaljer.sektorkode` når toppen er et organisasjonsledd
     fordi kjeden er brutt. Ingen sektorkode gir null.

  Andre feil fra Ereg kastes, som i `hentVirksomhetsnavn`, så kalleren kan skille
  «finnes ikke» fra «Ereg er nede».

  Hver gang funksjonen gir null, logger den to linjer, som i
  `TiltaksokonomiConsumer`. Orgnr maskeres i vanlig logg.
  - `teamLog.error` med virksomhetsnummeret, orgnr til toppen når det er kjent,
    og årsaken: virksomheten finnes ikke, ingen enhet over, toppen finnes ikke,
    toppen mangler sektorkode eller kjeden er for dyp.
  - `log.error` med årsaken, uten orgnr, og «Detaljer i teamLogs.».

  Saken opprettes som vanlig med `sektorkode` null.

### 3. Event, lagring og `OpprettSak`

- Migrering `V41__virksomhetstype.sql`, med `SET lock_timeout = '3s'` som i V27:
  - `ALTER TABLE sak ADD COLUMN IF NOT EXISTS sektorkode TEXT`. Ingen utfylling
    av gamle rader.
  - `ALTER TABLE IF EXISTS saksbehandler_kommunefilter RENAME TO saksbehandler_saksfilter`,
    og `ALTER INDEX IF EXISTS saksbehandler_kommunefilter_pkey RENAME TO saksbehandler_saksfilter_pkey`.
  - `ALTER TABLE saksbehandler_saksfilter ADD COLUMN IF NOT EXISTS virksomhetstyper TEXT[] NOT NULL DEFAULT '{}'`,
    se steg 4.
  - Kommentaren sier at tabellen lagrer saksfilteret: enhet, kommuner og typer.
- `SakTable.sektorkode`, nullable.
- `OpprettSak` får `EregService` i konstruktøren. Før transaksjonen kaller den
  `hentSektorkode(event.data.soknad.virksomhet.virksomhetsnummer)`. Kaster
  kallet, gir handleren `transientError("Feil ved oppslag av sektorkode i Ereg", e)`.
- `opprettSakForSoknad` får parameteren `sektorkode: String?` uten standardverdi,
  og skriver den både ved insert og i update-grenen for saker uten enhet.
- `EventData.SakOpprettet` får `sektorkode: String? = null`, med KDoc: sektorkoden
  til juridisk enhet på toppen av kjeden da saken ble opprettet. Null når Ereg ikke har
  den, og for eldre eventer.

Ingen nye eventer eller handlere, så `event-flow.md` regenereres ikke.

### 4. Backend: lista og lagret filter

Lista:

- `SakListeElement` får `virksomhetstype: Virksomhetstype?`.
  `hentSakerForSaksbehandling` setter
  `Virksomhetstype.fraSektorkode(row[SakTable.sektorkode])`.
- `utenDeltaker` setter `virksomhetstype = null`. Det gjelder også når
  Tilgangsmaskinen ikke svarer.

`SakDetaljer` endres ikke.

Lagret filter. `KommunefilterApi.kt` blir `SaksfilterApi.kt`, og navnene følger
med:

| I dag | Nytt navn |
|-------|-----------|
| `/api/saksbehandling/v1/meg/kommunefilter` | `/api/saksbehandling/v1/meg/saksfilter` |
| `configureKommunefilterApiV1` | `configureSaksfilterApiV1` (`Application.kt`, `LocalApplication.kt`) |
| `SaksbehandlerKommunefilterTable` | `SaksbehandlerSaksfilterTable` |
| `LagretKommunefilter` | `LagretSaksfilter` |
| `hentKommunefilter`, `lagreKommunefilter` | `hentSaksfilter`, `lagreSaksfilter` |
| `KommunefilterRequest`, `KommunefilterResponse` | `SaksfilterRequest`, `SaksfilterResponse` |

- `SaksbehandlerSaksfilterTable.virksomhetstyper`, `array<String>`.
- `SaksfilterRequest` får `virksomhetstyper: List<Virksomhetstype>`, uten
  standardverdi. Frontend bytter sti samtidig, så ingen gammel frontend sender
  til det nye endepunktet. Manglende felt eller ukjent verdi gir 400.
- PUT lagrer `virksomhetstyper.distinct()` i enumrekkefølge, sammen med enhet og
  kommuner.
- `SaksfilterResponse` får `virksomhetstyper: List<Virksomhetstype>`. GET
  returnerer de lagrede typene også når enheten ikke lenger er tilgjengelig,
  fordi typene ikke hører til en enhet. Verdier som ikke lenger finnes i enumen,
  hoppes over.
- KDoc på API-et beskriver hele saksfilteret.

### 5. Frontend: kolonnen

- `SakListeElement` i `useSaker.ts` får
  `virksomhetstype: Virksomhetstype | null`, med typen
  `"STATLIG" | "KOMMUNAL" | "PRIVAT" | "UKJENT"`.
- `OversiktPage.tsx` får kolonnen «Type virksomhet» mellom «Arbeidsgiver» og
  «Deltaker».
- `VirksomhetstypeCelle` viser `Tag size="xsmall" variant="outline"` med tekst
  og farge fra en `Record<Virksomhetstype, …>`:

  | Verdi | Tekst | `data-color` |
  |-------|-------|--------------|
  | `STATLIG` | Statlig | `meta-lime` |
  | `KOMMUNAL` | Kommunal | `meta-purple` |
  | `PRIVAT` | Privat | `info` |
  | `UKJENT` | Ukjent | `neutral` |

  Cellen er tom når verdien er null.

### 6. Frontend: filteret

- Ny funksjon `brukVirksomhetstypefilter(saker, valgte, tilgangssjekkFeilet)` i
  `hooks/useVirksomhetstypefilter.ts`. Den kjører etter kommunefilteret. Tom
  `valgte` gir alle. Ellers vises rader med en valgt type, rader uten tilgang og
  alle rader ved `tilgangssjekkFeilet`.
- `useKommunefilter.ts` blir `useSaksfilter.ts`. `useKommunefilter` blir
  `useSaksfilter`, typen `Kommunefilter` blir `Saksfilter`, og
  `SAKSBEHANDLING_KOMMUNEFILTER_URL` i `constants.ts` blir
  `SAKSBEHANDLING_SAKSFILTER_URL` med den nye stien. `brukKommunefilter` og
  `Kommune` beholder navnet, fordi de bare gjelder kommunedelen.
- `Saksfilter` får `virksomhetstyper: Virksomhetstype[]`, og `lagre` sender
  feltet i PUT. Uten lagret enhet beholdes de lagrede typene.
- `Kommunefilter.tsx` blir `Saksfilter.tsx` og viser enhet, kommune og type
  virksomhet i samme rad, som i skissen. Feltet «Type virksomhet» er en
  `UNSAFE_Combobox` med `isMultiSelect`, `size="small"` og de fire valgene. Det
  vises alltid når lista har saker. Endring lagres med `lagre`, som for kommune.
  Meldingen om at filteret ikke ble lagret, gjelder hele raden.
- Teksten under raden teller saker skjult av begge filtrene: «1 sak skjult av
  filteret» eller «N saker skjult av filteret».

### 7. Mock og lokale data

- `LokalVirksomhet` får `sektorkode: String`. Lista får minst én statlig, én
  kommunal og én privat. «Nav Lokalkontor Drammen» blir statlig. Ingen
  virksomhet mangler sektorkode, fordi det gir `log.error` i mock-backend i dev.
  Manglende sektorkode dekkes av `EregServiceTest`.
- Ereg-mocken i `LocalApplication.kt` svarer med nestet hierarki når
  `inkluderHierarki=true`, og på orgnr til toppen med
  `juridiskEnhetDetaljer.sektorkode`. Den statlige virksomheten får to nivåer
  (`BEDR` → `ORGL` → `ORGL` → `STAT`). Mocken i dev (`deploy-dev-mock.yml`,
  bygget med `-Pmock`) får dermed samme data.
- `frontend/saksbehandling/src/mocks/handlers.ts`: mockede saker får
  `virksomhetstype` med alle fire verdier. Arena-saker får `UKJENT`. `utenDeltaker`
  setter `null`. Handlerne for saksfilteret bruker den nye URL-en, og GET og PUT
  tar med `virksomhetstyper`.

### 8. Dokumentasjon

- `specifications/sak-datamodell.md`: kolonnen `sektorkode` i `sak`.
- KDoc på `SakOpprettet`, `EregService.hentSektorkode`, `Virksomhetstype` og
  `SakListeElement.virksomhetstype`.

### 9. Tester

Backend følger eksisterende oppsett.

- `VirksomhetstypeTest`: hver kode i tabellen gir riktig type. Null, tom streng
  og en ukjent kode gir `UKJENT`.
- `EregServiceTest` med `mockEreg`:
  - `BEDR` rett under juridisk enhet gir enhetens sektorkode.
  - `BEDR` under to nivåer `ORGL` gir sektorkoden til `STAT` på toppen, også
    når leddet har en annen kode (som Eksportfinansiering Norge, 4900 mot 6100).
    Svaret i mocken følger formen fra dev for NAV IKT.
  - `BEDR` uten enhet over gir null.
  - Brutt kjede der toppen er et `ORGL` gir leddets sektorkode, eller null når
    leddet mangler kode.
  - Kjede dypere enn ti nivåer gir null.
  - 404 på virksomheten eller toppen gir null.
  - Topp uten sektorkode gir null.
  - 500 fra Ereg kaster.
- `OpprettSakTest`:
  - Sektorkoden lagres på saken og står i `SakOpprettet`.
  - Feil fra Ereg gir transient error, og ingen sak opprettes.
  - Null fra Ereg gir sak med `sektorkode` null.
- `OpprettSakTest`: JSON for en `SakOpprettet` uten `sektorkode` (som fra før
  endringen) dekodes med `sektorkode` null.
- `SaksbehandlingSakApiTest`: `virksomhetstype` i lista for en sak med
  sektorkode, `UKJENT` uten sektorkode, og null på rader uten tilgang.
- `SaksbehandlingSakApiTest`, saksfilteret (eksisterende tester for
  kommunefilteret bytter til ny sti og nye navn):
  - PUT og GET gir tilbake de lagrede typene, uten duplikater.
  - PUT uten `virksomhetstyper` gir 400.
  - Ukjent verdi i `virksomhetstyper` gir 400.
  - GET returnerer typene også når den lagrede enheten ikke lenger er
    tilgjengelig.

Frontend: `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling`, og manuell
sjekk mot MSW: kolonnen, filter på én og flere typer, «Ukjent», rader uten
tilgang, teksten om skjulte saker og at valget står etter ny innlasting. Ingen ny
Playwright-test.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V41__virksomhetstype.sql` | Kolonne på `sak`, nytt navn og kolonne for `saksbehandler_saksfilter` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Virksomhetstype.kt` | Ny: enum og mapping |
| `backend/src/main/kotlin/no/nav/ekspertbistand/ereg/EregClient.kt` | Oppslag med hierarki |
| `backend/src/main/kotlin/no/nav/ekspertbistand/ereg/EregService.kt` | `hentSektorkode` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Db.kt` | `SakTable.sektorkode` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/OpprettSak.kt` | Oppslag og lagring |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/Events.kt` | Felt i `SakOpprettet` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SakApi.kt` | `virksomhetstype` i lista, `utenDeltaker` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/KommunefilterApi.kt` → `SaksfilterApi.kt` | Nye navn, ny sti, lagret typefilter |
| `backend/src/main/kotlin/no/nav/ekspertbistand/Application.kt` | `configureSaksfilterApiV1`, `EregService` til `OpprettSak` om DI krever det |
| `backend/src/test/kotlin/…` | Testene i steg 9, `LokaleSaker.kt`, `LocalApplication.kt` |
| `frontend/saksbehandling/src/hooks/useSaker.ts` | Type og felt |
| `frontend/saksbehandling/src/hooks/useVirksomhetstypefilter.ts` | Ny: filterfunksjon |
| `frontend/saksbehandling/src/hooks/useKommunefilter.ts` → `useSaksfilter.ts` | Nye navn, `virksomhetstyper` i lagret filter |
| `frontend/saksbehandling/src/utils/constants.ts` | `SAKSBEHANDLING_SAKSFILTER_URL` |
| `frontend/saksbehandling/src/components/Kommunefilter.tsx` → `Saksfilter.tsx` | Nytt felt, felles tekst om skjulte saker |
| `frontend/saksbehandling/src/pages/OversiktPage.tsx` | Kolonne, celle, filter |
| `frontend/saksbehandling/src/mocks/handlers.ts` | Mockdata, saksfilter |
| `specifications/sak-datamodell.md` | Ny kolonne |

## Kanttilfeller og feilmodus

- **Ereg er nede når saken skal opprettes:** `OpprettSak` gir transient error og
  prøver igjen, som ved feil mot PDL. Saken vises ikke i lista før oppslaget
  lykkes.
- **Virksomheten eller toppen finnes ikke i Ereg (404):** sektorkoden er null,
  saken får «Ukjent», og vi logger error.
- **Toppen mangler sektorkode:** «Ukjent» og error i loggen. Gjelder flere
  syntetiske virksomheter i dev, så dev vil logge error for dem.
- **Brutt kjede:** et `ORGL` peker på et slettet `ORGL`, så toppen blir et
  organisasjonsledd. Vi bruker koden på toppen. Et slettet ledd har som regel
  ingen gjeldende sektorkode, og saken får da «Ukjent» med error i loggen. Vi
  faller ikke tilbake til et ledd lenger ned, fordi enhetene i kjeden mangler
  detaljer og det ville krevd ett kall per nivå. I Brønnøysundregistrene gjelder
  det 3 av 1600 `ORGL`, blant annet under det slettede Skattedirektoratet
  (996250318).
- **Syklus eller svært dyp kjede:** vi stopper etter ti nivåer, gir «Ukjent» og
  logger error. Dypeste kjede i Brønnøysundregistrene er tre `ORGL`.
- **Virksomheten bytter sektor etter at saken er opprettet:** saken beholder
  koden fra opprettelsen.
- **Statlig virksomhet med flere nivåer organisasjonsledd:** vi bruker koden på
  toppen. 108 av 1600 `ORGL` har mer enn ett nivå.
- **Kode 6:** oppslaget gjelder virksomheten, ikke deltakeren, og gjøres som for
  andre saker. Typen vises bare for dem som har tilgang til deltakeren.
- **Tilgangsmaskinen svarer ikke:** alle rader mangler type og vises uansett
  filter.
- **Saker opprettet før endringen og Arena-saker:** «Ukjent». Ingen backfill.
- **Kategoriseringen endres senere:** bare mappingen i `Virksomhetstype.kt`
  endres. Ingen migrering.
- **Lagret filter mot ny eller fjernet type:** typer som ikke finnes i enumen,
  hoppes over ved lesing. Et lagret filter på «Ukjent» skjuler saker som senere
  får en type.
- **Lagring av filteret feiler:** valget gjelder resten av økten, og raden viser
  samme melding som for kommune i dag.
- **Deploy av nytt navn i dev:** pods med gammel kode bruker gammelt tabellnavn
  til de er byttet ut. Frontend kaller gammel sti til den er deployet. Filteret
  feiler da kort i dev. Vi deployer backend og saksbehandling rett etter
  hverandre. Tabellen er bare i bruk i dev.

## Åpne spørsmål

Ingen.

## Ferdig når

- Nye saker får sektorkoden til juridisk enhet på toppen av kjeden, lagret på saken og i
  `SakOpprettet`.
- Saksoversikten har kolonnen «Type virksomhet» med taggene Statlig, Kommunal,
  Privat og Ukjent, som i skissen.
- Saksbehandler kan filtrere på én eller flere typer, og rader uten tilgang
  vises alltid. Valget lagres per saksbehandler og står etter ny innlasting.
- Gamle `SakOpprettet`-eventer uten `sektorkode` kan fortsatt leses.
- Testene i steg 9 er grønne, og `pnpm typecheck` og `pnpm lint` går gjennom.
