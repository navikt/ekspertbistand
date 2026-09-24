package no.nav.ekspertbistand.executables

import java.io.File

/**
 * Genererer flytdiagram (Mermaid) over hvilke events som fører til hvilke events, ved statisk
 * tekstanalyse av `backend/src/main/kotlin`. Resultatet skrives til [EventFlowDiagram.DIAGRAM_FIL].
 *
 * Kjør `main` på nytt når events eller handlere er lagt til eller endret. Kan kjøres fra
 * repo-roten eller fra `backend/`.
 *
 * Hva som leses ut:
 * - **Handlere**: klasser som implementerer `EventHandler<EventData.X>`, og inline
 *   `register<EventData.X>("id")`.
 * - **Projeksjoner**: subklasser av `EventLogProjectionBuilder`, med `is EventData.X` som input.
 * - **Publisering**: `EventData.X(...)`-konstruktørkall i filer som kaller `publishEventQueue`.
 *   Publisereren er nærmeste omsluttende klasse.
 */
fun main() {
    val backendDir = listOf(File("backend"), File("."))
        .firstOrNull { File(it, "src/main/kotlin").isDirectory }
        ?: error("Fant ikke backend/src/main/kotlin fra ${File(".").absolutePath}")

    val fil = File(backendDir, EventFlowDiagram.DIAGRAM_FIL)
    fil.writeText(EventFlowDiagram.generate(File(backendDir, "src/main/kotlin")))
    println("Skrev ${fil.canonicalPath}")
}

object EventFlowDiagram {
    const val DIAGRAM_FIL = "src/main/kotlin/no/nav/ekspertbistand/event/event-flow.md"

    data class Handler(val name: String, val consumes: String)
    data class Projection(val name: String, val consumes: Set<String>)
    data class Publication(val publisher: String, val event: String)

    data class Model(
        val events: Set<String>,
        val handlers: List<Handler>,
        val projections: List<Projection>,
        val publications: Set<Publication>,
    )

    private val blockComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("""(?m)^\s*//.*$""")
    private val classDecl = Regex("""(?<!::)(?<!data |enum |annotation )\bclass\s+([A-Z]\w*)""")
    private val eventDataClass = Regex("""data class (\w+)\s*\(""")
    private val handlerImpl = Regex(""":\s*EventHandler<\s*EventData\.(\w+)\s*>""")
    private val inlineHandler = Regex("""register<\s*EventData\.(\w+)\s*>\(\s*"([^"]+)"""")
    private val projectionImpl = Regex(""":\s*EventLogProjectionBuilder\s*\(""")
    private val isEventData = Regex("""\bis\s+EventData\.(\w+)""")
    private val eventConstructor = Regex("""\bEventData\.(\w+)\s*\(""")

    fun generate(sourceRoot: File): String = render(parse(sourceRoot))

    fun parse(sourceRoot: File): Model {
        require(sourceRoot.isDirectory) { "Fant ikke ${sourceRoot.absolutePath}" }

        val sources = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .associate { it.relativeTo(sourceRoot).invariantSeparatorsPath to stripComments(it.readText()) }

        val eventsFile = sources.entries.single { it.key.endsWith("/event/Events.kt") }.value
        val events = eventDataClass.findAll(eventsFile).map { it.groupValues[1] }.toSortedSet()

        val handlers = mutableListOf<Handler>()
        val projections = mutableListOf<Projection>()
        val publications = mutableSetOf<Publication>()

        for ((path, src) in sources) {
            handlerImpl.findAll(src).forEach {
                handlers += Handler(enclosingClass(src, it.range.first, path), it.groupValues[1])
            }
            inlineHandler.findAll(src).forEach {
                handlers += Handler(it.groupValues[2], it.groupValues[1])
            }
            projectionImpl.findAll(src).forEach {
                val name = enclosingClass(src, it.range.first, path)
                val consumes = isEventData.findAll(src).map { m -> m.groupValues[1] }.toSortedSet()
                projections += Projection(name, consumes)
            }
            if (path.endsWith("/event/EventQueue.kt") || !src.contains("publishEventQueue(")) continue
            eventConstructor.findAll(src).forEach {
                publications += Publication(enclosingClass(src, it.range.first, path), it.groupValues[1])
            }
        }

        val referenced = handlers.map { it.consumes } +
                projections.flatMap { it.consumes } +
                publications.map { it.event }
        val ukjente = referenced.toSet() - events
        if (ukjente.isNotEmpty()) {
            error("Fant referanser til ukjente EventData-typer: $ukjente (kjente: $events)")
        }

        return Model(
            events = events,
            handlers = handlers.sortedBy { it.name },
            projections = projections.sortedBy { it.name },
            publications = publications.sortedWith(compareBy({ it.publisher }, { it.event })).toSet(),
        )
    }

    fun render(model: Model): String {
        val handlerNames = model.handlers.map { it.name }.toSet()
        val sources = model.publications.map { it.publisher }.filter { it !in handlerNames }.toSortedSet()
        val produced = model.publications.map { it.event }.toSet()
        val consumed = model.handlers.map { it.consumes }.toSet() + model.projections.flatMap { it.consumes }

        fun eventId(name: String) = "e_$name"
        fun handlerId(name: String) = "h_" + name.replace(Regex("\\W"), "_")
        fun sourceId(name: String) = "s_$name"
        fun projectionId(name: String) = "p_$name"
        fun publisherId(name: String) = if (name in handlerNames) handlerId(name) else sourceId(name)

        val sb = StringBuilder()
        sb.appendLine("# Event-flyt")
        sb.appendLine()
        sb.appendLine("<!-- GENERERT FIL – ikke rediger manuelt. -->")
        sb.appendLine("<!-- Oppdater ved å kjøre main i backend/src/test/kotlin/no/nav/ekspertbistand/executables/EventFlowDiagram.kt -->")
        sb.appendLine()
        sb.appendLine("Generert ved statisk analyse av `src/main/kotlin` (se `executables/EventFlowDiagram.kt`).")
        sb.appendLine()
        sb.appendLine("- **Kilde** (oransje parallellogram): kode utenfor event-systemet som publiserer events (API, Kafka-konsumenter).")
        sb.appendLine("- **Event** (blå, avrundet): `EventData`-type. Rød betyr at eventen mangler publiserer eller konsument.")
        sb.appendLine("- **Handler** (mørkt rektangel): `EventHandler` som konsumerer én event-type og kan publisere nye.")
        sb.appendLine("- **Projeksjon** (grønn, stiplet): `EventLogProjectionBuilder` som leser fra event-loggen.")
        sb.appendLine()
        sb.appendLine("```mermaid")
        sb.appendLine("flowchart TD")
        sb.appendLine("    classDef source fill:#ff9100,stroke:#7a3d00,stroke-width:2px,color:#000,font-weight:bold")
        sb.appendLine("    classDef event fill:#0067c5,stroke:#002d5a,stroke-width:2px,color:#fff,font-weight:bold")
        sb.appendLine("    classDef handler fill:#262626,stroke:#000,stroke-width:2px,color:#fff")
        sb.appendLine("    classDef projection fill:#06893a,stroke:#00341a,stroke-width:2px,stroke-dasharray: 5 3,color:#fff")
        sb.appendLine("    classDef orphan fill:#c30000,stroke:#5c0000,stroke-width:3px,color:#fff,font-weight:bold")
        sb.appendLine()
        sources.forEach { sb.appendLine("    ${sourceId(it)}[/\"$it\"/]:::source") }
        model.events.forEach {
            val cls = if (it in produced && it in consumed) "event" else "orphan"
            sb.appendLine("    ${eventId(it)}([\"$it\"]):::$cls")
        }
        model.handlers.forEach { sb.appendLine("    ${handlerId(it.name)}[\"${it.name}\"]:::handler") }
        model.projections.forEach { sb.appendLine("    ${projectionId(it.name)}[[\"${it.name}\"]]:::projection") }
        sb.appendLine()
        model.publications.forEach { sb.appendLine("    ${publisherId(it.publisher)} --> ${eventId(it.event)}") }
        model.handlers.forEach { sb.appendLine("    ${eventId(it.consumes)} --> ${handlerId(it.name)}") }
        model.projections.forEach { p ->
            p.consumes.forEach { sb.appendLine("    ${eventId(it)} -.-> ${projectionId(p.name)}") }
        }
        sb.appendLine("    linkStyle default stroke:#555,stroke-width:2px")
        sb.appendLine("```")

        val uten = model.events.filter { it !in produced || it !in consumed }
        if (uten.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Events markert i rødt mangler enten publiserer eller konsument:")
            sb.appendLine()
            uten.forEach {
                val mangler = listOfNotNull(
                    "publiserer".takeIf { _ -> it !in produced },
                    "konsument".takeIf { _ -> it !in consumed },
                ).joinToString(" og ")
                sb.appendLine("- `$it` – mangler $mangler")
            }
        }
        return sb.toString()
    }

    private fun stripComments(src: String) = src.replace(blockComment, "").replace(lineComment, "")

    private fun enclosingClass(src: String, index: Int, path: String): String =
        classDecl.findAll(src.substring(0, index)).lastOrNull()?.groupValues?.get(1)
            ?: path.substringAfterLast('/').removeSuffix(".kt")
}
