package no.nav.ekspertbistand.dokument.pdf

/** Felles unntak for alt som går galt under dokumentgenerering. */
class PdfGenerationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

