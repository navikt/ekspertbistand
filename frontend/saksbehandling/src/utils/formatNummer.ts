export function formatOrganisasjonsnummer(nummer: string) {
  return nummer.replace(/^(\d{3})(\d{3})(\d{3})$/, "$1 $2 $3");
}

export function formatFodselsnummer(nummer: string) {
  return nummer.replace(/^(\d{6})(\d{5})$/, "$1 $2");
}
