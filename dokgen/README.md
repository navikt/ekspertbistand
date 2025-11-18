# ekspertbistand-dokgen

Denne tjenesten inneholder brevmaler som bruker av ekspertbistand-backend til å
generere brev.

Oppsettet for tjenesten er hentet fra [navikt/dokgen](https://github.com/navikt/dokgen)

### Forhåndsvise PDF-er

### Kjør lokalt

```
docker build -t permittering-dokgen . && docker run --rm -it -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev permittering-dokgen
```
