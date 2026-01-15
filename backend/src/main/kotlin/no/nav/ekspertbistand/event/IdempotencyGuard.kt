package no.nav.ekspertbistand.event

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class IdempotencyGuard(
    private val caller: String,
    private val database: Database
) {
    private fun taskName(subTask: String) = "$caller-$subTask"

    fun <T : EventData> guard(event: Event<T>, subTask: String) {
        transaction(database) {
            IdempotencyGuardRecords.insert {
                it[this.eventId] = event.id
                it[this.subTask] = taskName(subTask)
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
                            and (IdempotencyGuardRecords.subTask eq taskName(subTask))
                ).map { it[IdempotencyGuardRecords.status] }
                .firstOrNull() == IdempotencyStatus.COMPLETED
        }
    }


    companion object {
        inline fun <reified T> T.idempotencyGuard(database: Database): IdempotencyGuard {
            val caller = T::class.simpleName
            if (caller == null) {
                throw IllegalArgumentException("caller's simpleName is null")
            }
            return IdempotencyGuard(
                caller,
                database
            )

        }
    }
}

object IdempotencyGuardRecords : CompositeIdTable("idempotency_guard_records") {
    val eventId = long("event_id").entityId()
    val subTask = text("sub_task").entityId()
    val eventName = text("event_name")
    val status = enumerationByName("status", 13, IdempotencyStatus::class)
}

enum class IdempotencyStatus {
    COMPLETED,
    NOT_COMPLETED;
}