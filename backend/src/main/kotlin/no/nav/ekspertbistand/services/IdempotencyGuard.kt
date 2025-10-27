package no.nav.ekspertbistand.services

import no.nav.ekspertbistand.event.Event
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class IdempotencyGuard(private val database: Database) {
    fun guard(event: Event, subTask: String) {
        transaction(database) {
            IdempotencyRecords.insert {
                it[this.eventId] = event.id
                it[this.subTask] = subTask
                it[this.eventName] = event::class.simpleName!!
                it[this.status] = IdempotencyStatus.COMPLETED
            }
        }
    }

    fun isGuarded(eventId: Long, subTask: String): Boolean {
        return transaction(database) {
            IdempotencyRecords
                .select(
                    IdempotencyRecords.status
                ).where(
                    (IdempotencyRecords.eventId eq eventId)
                            and (IdempotencyRecords.subTask eq subTask)
                ).map { it[IdempotencyRecords.status] }
                .first() == IdempotencyStatus.COMPLETED
        }
    }
}

object IdempotencyRecords : CompositeIdTable("idempotency_records") {
    val eventId = long("event_id").entityId()
    val subTask = varchar("sub_task", 50).entityId()
    val eventName = varchar("event_name", 50)
    val status = enumerationByName("status", 13, IdempotencyStatus::class)
}

enum class IdempotencyStatus {
    COMPLETED,
    NOT_COMPLETED;
}
