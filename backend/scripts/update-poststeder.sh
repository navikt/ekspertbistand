#!/usr/bin/env bash
#
# Laster ned postnummerregisteret fra Posten/Bring [0] og skriver det som
# postnummer -> poststed i src/main/resources/poststeder.json.
#
# EregService bruker registeret til å fylle inn poststed når Ereg mangler det.
# Kjøres månedlig av .github/workflows/update-poststeder.yaml, som oppretter en
# PR dersom filen endrer seg.
#
# [0]: https://www.bring.no/tjenester/adressetjenester/postnummer
#
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

tmpfile=$(mktemp)
trap 'rm -f "$tmpfile"' EXIT

curl -fsSL -o "$tmpfile" "https://www.bring.no/postnummerregister-ansi.txt"

iconv -f WINDOWS-1252 -t UTF-8 "$tmpfile" \
  | awk -F'\t' '{ gsub("\r", ""); if (NF >= 2) print $1"\t"$2 }' \
  | jq --sort-keys -R -s '
      split("\n")
      | map(select(length > 0))
      | map(split("\t"))
      | map({(.[0]): .[1]})
      | add
    ' > src/main/resources/poststeder.json

echo "Oppdatert src/main/resources/poststeder.json"
