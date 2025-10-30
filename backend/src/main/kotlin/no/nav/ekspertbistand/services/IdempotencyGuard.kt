package no.nav.ekspertbistand.services

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class IdempotencyGuard(private val database: Database) {
    fun <T: EventData> guard(event: Event<T>, subTask: String) {
        transaction(database) {
            IdempotencyGuardRecords.insert {
                it[this.eventId] = event.id
                it[this.subTask] = subTask
                it[this.eventName] = event::class.simpleName!!
                it[this.status] = IdempotencyStatus.COMPLETED
            }
        }
    }

    fun isGuarded(eventId: Long, subTask: String): Boolean {
        return transaction(database) {
            IdempotencyGuardRecords
                .select(
                    IdempotencyGuardRecords.status
                ).where(
                    (IdempotencyGuardRecords.eventId eq eventId)
                            and (IdempotencyGuardRecords.subTask eq subTask)
                ).map { it[IdempotencyGuardRecords.status] }
                .firstOrNull() == IdempotencyStatus.COMPLETED
        }
    }
}

object IdempotencyGuardRecords : CompositeIdTable("idempotency_guard_records") {
    val eventId = long("event_id").entityId()
    val subTask = varchar("sub_task", 50).entityId()
    val eventName = varchar("event_name", 50)
    val status = enumerationByName("status", 13, IdempotencyStatus::class)
}

enum class IdempotencyStatus {
    COMPLETED,
    NOT_COMPLETED;
}
