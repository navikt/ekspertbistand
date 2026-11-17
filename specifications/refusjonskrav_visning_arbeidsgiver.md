# Vis innsendt refusjonskrav til arbeidsgiver

Trello: https://trello.com/c/hxzNc8Z4/684-oppdatere-visning-av-refusjonskrav-for-arbeidgsiver
Kort-ID: `6ae479e0d6be2b25d2643998`

Bygger videre på kort 683,
[sluttrapport_visning_arbeidsgiver.md](sluttrapport_visning_arbeidsgiver.md),
og kort 693, [vis_vedtak_arbeidsgiver.md](vis_vedtak_arbeidsgiver.md).
Branchen `trello-684-refusjonskrav-visning` bygger på
`trello-683-sluttrapport-visning`, etter avklaring med brukeren 2026-11-17.

## Mål

Når arbeidsgiver har sendt inn refusjonskravet, viser vedtakssiden et lukket
kort med «Refusjonskrav sendt inn». Kortet ligger over sluttrapporten, vedtaket
og søknaden. Vedtakets statusbanner beholdes. Vi utleder ikke behandlingsstatus
for refusjonskravet fra at det finnes.

Åpnet kort viser beløpet arbeidsgiver krever refundert, vedleggene som
nedlastingslenker med filstørrelse og datoen kravet ble sendt inn til Nav.
Vi viser ikke kontonummer.

Etter vellykket innsending går arbeidsgiver direkte til vedtakssiden og får
«Refusjonskravet er sendt inn». Feil i det etterfølgende metadataoppslaget
skal ikke fremstilles som feil ved innsendingen eller gi tilbud om å sende
kravet på nytt.

## Bakgrunn

- `v2/pages/VedtakPage.tsx` viser vedtaket, nøkkeldata og kortene fra kort
  683 og 693. Den henter sluttrapportstatus, men ikke refusjonsstatus.
  «Søk refusjon» vises derfor også når et krav allerede er mottatt.
- `v2/pages/SokRefusjonPage.tsx` har både skjemaet og en egen visning av
  innsendt krav. Den visningen starter med et åpent ekspansjonskort og har
  kontonummer, beløp, nedlastingslenker og innsendingsdato.
  Etter vellykket POST oppdaterer siden status og navigerer til kvitteringen.
- `v2/hooks/useRefusjonStatus.ts` returnerer `refusjon`, `isLoading`, `error`
  og `mutate`. Kravet har `belopKroner`, `opprettet`, `kontonummer` og
  `vedlegg`, der hvert vedlegg har `id`, `filnavn` og `storrelse`.
- `GET /api/soknad/v1/{id}/refusjon` returnerer metadata ved treff og 204 når
  kravet mangler. Filinnhold og behandlingsstatus følger ikke med.
- `GET /api/soknad/v1/{id}/refusjon/vedlegg/{vedleggId}` returnerer hele PDF-en
  som vedlegg. Backend kontrollerer tilgang til virksomheten og at vedlegget
  tilhører søknadens refusjonskrav. Ruten finnes allerede.
- `RefusjonDb.finnRefusjonskravStatusForSak` returnerer foreløpig
  `kontonummer = null`. Vi trenger ikke et nytt registeroppslag for denne
  visningen.
- Backend virusskanner og lagrer kravet og vedleggene før den svarer 201.
  `RefusjonskravMottatt` legges i eventkøen i samme transaksjon som lagringen.
  Asynkron sakslogging trenger ikke være ferdig før arbeidsgiver får
  bekreftelse og ser kortet.
- Frontend v2 er isolert fra v1. Endringene legges i v2, med eksisterende
  infrastruktur og hjelpere der.

## Skisser

Figma-fil `lvEwrzOupYmavMI2QmsMPI`, lest med Figma MCP 2026-11-17.

| Tilstand | Ramme | Lenke |
|----------|-------|-------|
| Lukket refusjonskort | `299:45359` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=299-45359> |
| Åpent refusjonskort | `299:45679` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=299-45679> |

Begge viser «Nav behandler refusjonskravet» over nøkkeldataene.
Kortrekkefølgen er refusjonskrav, sluttrapport, vedtak og søknad.
Refusjonskortets hode har dokumentikon, «Refusjonskrav sendt inn» og dato.

Åpent kort viser kontonummer, beløp, vedlegg som PDF-lenker med filstørrelse,
og «Sendt inn til Nav» med dato. Opplysningene har skillelinjer.

Skissene har disse avvikene fra det brukeren har avklart:

- «Nav behandler refusjonskravet» tas ikke med, fordi backend ikke returnerer
  en eksplisitt behandlingsstatus. Vedtakets statusbanner beholdes.
- Kontonummer tas ut.
- Begge rammer lover vanligvis én ukes behandlingstid og beskjed på e-post.
  Den teksten tas ikke med.
- Avsatt beløp vises i lukket ramme, men mangler i åpen ramme.
  Vi beholder beløpet uavhengig av om kortet er åpent eller lukket.

Eksempelverdiene i skissene er ikke testdata for implementasjonen.

## Beslutninger

Kort 684 har ingen kommentarer, sjekklister eller vedlegg (sjekket 2026-11-17).
Spørsmålet om kontonummer står i kortbeskrivelsen og ble avklart i samtalen.

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Innsendt refusjonskrav vises i et ekspansjonskort som starter lukket. |
| Kortbeskrivelsen | Arbeidsgiver med tilgang skal kunne hente hele refusjonsvedleggene, i motsetning til sluttrapporten. |
| Figma `299:45359` og `299:45679` | Refusjonskortet ligger før sluttrapporten, vedtaket og søknaden. Statusen er «Nav behandler refusjonskravet». |
| Brukeren, 2026-11-17 | Ny branch legges oppå sluttrapportbranchen fra kort 683. |
| Brukeren, 2026-11-17 | Ikke vis kontonummer i refusjonskortet. |
| Brukeren, 2026-11-17, første avklaring | Vis «Nav behandler refusjonskravet», behold avsatt beløp og utelat løfte om behandlingstid og e-post. Statusvalget erstattes av presiseringen under. |
| Brukeren, 2026-11-17 | Gjør samme type endringer som for sluttrapport: direkte retur til vedtaket, bekreftelse og separat håndtering av metadatafeil etter innsending. |
| Brukeren, 2026-11-17 kl. 14:16 | Behandlingsstatus i frontend er prematurt uten eksplisitt behandlingsstatus fra backend. Behold vedtakets statusbanner og vis bare bekreftet mottak av refusjonskravet. |

### Valg i spec-en

- Endringen gjelder v2, ved både ordinær innvilgelse og redusert beløp.
  V1 og Arena-visningene endres ikke.
- Vedlegg vises som nedlastingslenker, slik skissen og dagens refusjonsside
  gjør. Vi legger ikke PDF-forhåndsvisning eller automatisk dokumenthenting
  inn i kortet.
- Et eget `RefusjonskravSendtInnKort` følger kortmønsteret fra 683.
  Det tar metadata som props. Dato og beløp bruker eksisterende
  formateringshjelpere i v2.
- Innsending av sluttrapport og refusjon styres uavhengig. Et refusjonskrav
  skjuler ikke muligheten til å sende sluttrapport dersom den fortsatt mangler.
  Vi innfører ikke et nytt krav om rekkefølge på innsendingene.
- Når et krav allerede finnes og arbeidsgiver åpner `/refusjon`, sendes hen
  til vedtakssiden. Vi beholder ikke to ulike visninger av innsendt krav.

## Avgrensning

Ingen backend-endring, ny API-kontrakt eller endring i tilgangskontroll.
Vi endrer ikke beløpsvalidering, filgrenser, skanning, eventer eller
asynkron behandling. Kortet viser mottatt krav, ikke resultatet av en
fremtidig refusjonsbehandling.

Kontonummerkontrollen før innsending beholdes. Det er visningen av innsendt
krav som ikke skal vise kontonummer. Ingen nye registeroppslag, e-postvarsler
eller løfter om behandlingstid.

## Tilnærming

### 1. Refusjonskort

Lag `v2/components/RefusjonskravSendtInnKort.tsx` med et lukket
`ExpansionCard`, `size="small"`, tittel som `h2` og tilgjengelig navn
«Refusjonskrav sendt inn». Bruk samme hodeoppsett som sluttrapportkortet,
med dekorativt `FileTextIcon` og formatert dato fra `opprettet`.

Innholdet er en semantisk definisjonsliste med «Beløp for refusjonskravet»,
«Vedlegg» og «Sendt inn til Nav». Vedleggene er en liste med `Link`,
`FilePdfIcon`, filnavn og størrelse fra `formatBytes`.
Lenkene bruker `EKSPERTBISTAND_REFUSJON_VEDLEGG_PATH` og eksisterende
nedlastingsrute. Kortet henter ikke filene ved lasting eller åpning.
Lange filnavn skal bryte på smal skjerm.

Aksel-versjonskravet er `^8.16.1`. `ExpansionCard` med underkomponenter,
`Link`, `BodyShort`, ikonene og tokenene er slått opp via Aksel MCP.
Avstander og skillelinjer følger kort 683 med `space-16` og
`border-neutral-subtle`. Vedtakets statusbanner og farger beholdes.

### 2. Vedtakssiden

Hent refusjonsstatus med `useRefusjonStatus` for innvilgede vedtak, også ved
redusert beløp. Ikke hent status for avslag eller før vedtaket er kjent.
Sluttrapport og refusjon hentes uavhengig av hverandre.

| Status | Visning |
|--------|---------|
| Laster | Tilgjengelig ventemarkering. Ingen «Søk refusjon» før status er avklart. |
| 200 med metadata | Vedtaksstatus og avsatt beløp beholdes, og refusjonskortet vises øverst. Ingen «Søk refusjon» eller refusjonsfrist. |
| 204 | Dagens vedtaksstatus, vilkår, refusjonsknapp og frist beholdes. |
| Feil | Synlig feilmelding med beskjed om å laste siden på nytt. Ingen ny refusjonsinnsending mens status er ukjent. Resten av vedtaket er tilgjengelig. |

Når kravet er mottatt, fjernes den generelle teksten om å dokumentere
utgifter før fristen. Eventuell knapp for manglende sluttrapport styres
fortsatt av sluttrapportstatusen.

Alle dokumentkort starter lukket. Åpning og lukking av et kort skal ikke
endre statusbanneret, avsatt beløp eller åpningstilstanden til de andre kortene.
Sluttrapporten får fortsatt ingen dokumentlenke.

### 3. Etter innsending

Følg mønsteret fra kort 683 i `SokRefusjonPage`:

1. Vent på vellykket POST. Ved feil beholdes beløp, valgt dokumentasjon og
   bekreftelsen på betalte utgifter, sammen med feilmelding.
2. Hent refusjonsmetadata på nytt med `mutateStatus`.
3. Naviger direkte til `/v2/skjema/:id/vedtak` med bekreftet innsending,
   også hvis metadataoppslaget feiler.

Vedtakssiden viser «Refusjonskravet er sendt inn». Ved metadatafeil vises i
tillegg at kravet er sendt inn, men opplysningene ikke kunne hentes.
Brukeren skal ikke få beskjed om at POST feilet eller tilbys ny innsending.
Bekreftet innsending bekrefter mottak, ikke at Nav behandler kravet.
Vedtakets statusbanner beholdes også mens metadata mangler.

Bekreftelsen vises bare etter innsending, ikke ved vanlig åpning av siden.
Navigeringstilstanden konsumeres som i kort 683. Vi legger ikke konstruerte
refusjonsmetadata i SWR-cachen.

Når `/refusjon` åpnes på nytt, avklar status før skjemaet vises. Et eksisterende
krav sender videre til vedtaket uten en ny innsendingsbekreftelse.
Statusfeil vises som feil, ikke som et tomt skjema for ny innsending.

### 4. Tester

Utvid eksisterende Vitest- og MSW-tester. Dekk ordinær innvilgelse og redusert
beløp, både med og uten sluttrapport.

Kontroller kortrekkefølge, lukket starttilstand, tastaturbetjening og at
beløpet ikke endres når kortet åpnes. Ingen kontonummer skal vises, heller
ikke når mock-responsen har et kontonummer. Vedtaksstatus beholdes både når
kravet finnes og etter vellykket innsending. Ingen utledet behandlingsstatus
skal vises, heller ikke ved metadatafeil.

Test lenkene for flere vedlegg, filstørrelser og lange filnavn.
Dokumenter hentes ikke ved lasting eller åpning av kortet.
Lenkene peker på riktig søknad og vedlegg.

Dekk direkte navigering etter POST, innsendingsbekreftelsen, metadatafeil
etter vellykket POST, vanlig POST-feil og ny åpning av `/refusjon`.
Eksisterende validering av beløp og påkrevde vedlegg skal fortsatt virke.
Sluttrapportflyten og v2-isolasjonstesten skal fortsatt passere.

## Berørte filer

| Fil under `frontend/client/src/` | Endring |
|--------------------------------|---------|
| `v2/components/RefusjonskravSendtInnKort.tsx` | Ny presentasjonskomponent for refusjonsmetadata og dokumentlenker. |
| `v2/components/RefusjonskravSendtInnKort.module.css` | Filnavnbryting og skillelinjer i kortet. |
| `v2/pages/VedtakPage.tsx` | Refusjonsmetadata, kort og innsendingsbekreftelse. Vedtakets statusbanner beholdes. |
| `v2/pages/VedtakPage.test.tsx` | Status, kortrekkefølge, vedlegg og samspill med sluttrapport. |
| `v2/pages/SokRefusjonPage.tsx` | Direkte retur til vedtaket, separat metadatafeil og videresending når krav finnes. |
| `v2/pages/SokRefusjonPage.test.tsx` | Oppdater tester for den nye flyten og bevar valideringstestene. |

## Kanttilfeller og feilmodus

- 403, 404 og serverfeil er ikke det samme som 204.
- Metadatafeil etter vellykket POST endrer ikke at kravet er mottatt.
- Feil ved refusjonsoppslaget skal ikke skjule søknaden eller vedtaksbrevet.
  Sluttrapportstatus skal fortsatt kunne vises.
- Refusjon uten sluttrapport skal fungere uten at vi later som rapporten er sendt.
- Tom vedleggsliste eller manglende dato vises med eksisterende plassholdere,
  ikke med oppdiktede data.
- Filnavn rendres som tekst. Kontonummer fra metadata ignoreres i kortet.
- Godkjent søknad og godkjent refusjonskrav er forskjellige ting.
  Vi viser ingen godkjenning eller utbetaling av refusjonen i dette kortet.

## Åpne spørsmål

Ingen som blokkerer utkastet. Spørsmålet om kontonummer og forskjellene mellom
skissene er avklart med brukeren og beskrevet over.

## Ferdig når

- Egen branch bygger på kort 683, uten å endre sluttrapportbranchen.
- Mottatt refusjonskrav vises i et lukket kort før de andre dokumentkortene.
- Kortet viser beløp, nedlastbare vedlegg med filstørrelse og innsendingsdato.
- Kontonummer vises ikke.
- Vedtakets statusbanner og avsatt beløp beholdes. Ingen behandlingsstatus
  utledes fra at refusjonskravet finnes. Ingen løfter om behandlingstid
  eller e-post vises.
- Refusjonsknappen og fristen er borte når kravet er mottatt.
- Vellykket POST fører direkte til vedtaket med bekreftelse, også ved metadatafeil.
- Direkte åpning av `/refusjon` med eksisterende krav fører til vedtaket.
- Ukjent status gir ingen ny innsendingsmulighet.
- V1, beløpsvalidering og sluttrapportflyten er uendret.
- Målrettede tester, typekontroll, lint og v2-isolasjonstesten passerer.
- Visningen er kontrollert på smal skjerm og med tastatur.
