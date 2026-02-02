package no.nav.ekspertbistand.event

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.json
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object EventLog : Table("event_log") {
    val id = long("id")
    val eventData = json<EventData>("event_json", Json.Default)
    val status = enumeration<ProcessingStatus>("status").default(ProcessingStatus.PENDING)
    val errors = json<List<EventHandledResult.UnrecoverableError>>("errors", Json.Default).default(emptyList())
    val attempts = integer("attempts").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

@OptIn(ExperimentalTime::class)
data class LoggedEvent(
    val id: Long,
    val eventData: EventData,
    val status: ProcessingStatus,
    val createdAt: kotlin.time.Instant,
    val updatedAt: kotlin.time.Instant
) {
    val event
        get() = Event(
            id = id,
            data = eventData
        )

    companion object {
        fun ResultRow.tilLoggedEvent() = LoggedEvent(
            id = this[EventLog.id],
            eventData = this[EventLog.eventData],
            status = this[EventLog.status],
            createdAt = this[EventLog.createdAt],
            updatedAt = this[EventLog.updatedAt]
        )
    }
}
