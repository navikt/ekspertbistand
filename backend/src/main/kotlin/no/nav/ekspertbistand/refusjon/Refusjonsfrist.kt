package no.nav.ekspertbistand.refusjon

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Hvor lenge etter sluttdatoen for tiltaket arbeidsgiver kan sende refusjonskrav. */
val REFUSJONSFRIST_ETTER_SLUTTDATO = DatePeriod(months = 6)

/** Når datoen ikke finnes i måneden fristen havner i, brukes siste dag i måneden. */
fun refusjonsfrist(sluttdato: LocalDate): LocalDate = sluttdato.plus(REFUSJONSFRIST_ETTER_SLUTTDATO)
