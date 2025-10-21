# EventQueue

Durable, at-least-once event processing using a relational database queue and log.

## Overview

- QueuedEvents are stored in the `event_queue` table.
- EventManager acquires events using `SELECT ... FOR UPDATE SKIP LOCKED` to avoid races across pods/processes.
- On completion, events are moved to `event_log` with a terminal status and removed from `events`.
- If a process dies mid-processing, the event becomes eligible for re-processing after an abandonment timeout.

## Lifecycle

- `publish(event: Event)`: Insert into `events` with status PENDING, attempts=0.
- `poll(clock: Clock = Clock.System): QueuedEvent?`: Atomically select the next eligible row and mark it PROCESSING, incrementing attempts.
  - Eligibility: status = PENDING, or status = PROCESSING and `updated_at` older than the abandonment timeout.
  - Uses `FOR UPDATE SKIP LOCKED` so only one process acquires a row.
  - On acquire: set `status = PROCESSING`, `updated_at = now`, and increment `attempts`.
- `finalize(id: Long, errorResults: List<EventHandeledResult.Error> = emptyList())`: Insert into `event_log` with status COMPLETED or COMPLETED_WITH_ERRORS, and delete from `events`.
  - Idempotent: If already finalized, does nothing.

### Sequence diagram

Event processing is managed by `EventManager`, which polls events from the queue and dispatches them to registered `EventHandler`s. On completion, events are finalized and moved to the log.

```mermaid
sequenceDiagram
    participant P as Producer
    participant Q as EventQueue (DB)
    participant M as EventManager
    participant H as EventHandlers
    participant L as Event Log

    P->>Q: publish(event)
    Note over Q: events += {status: PENDING, attempts: 0}

    M->>Q: poll()
    activate Q
    Q-->>M: select next (row lock via FOR UPDATE SKIP LOCKED)
    Q->>Q: set status=PROCESSING, attempts=+1, updated_at=now
    deactivate Q

    M->>H: handle(event) (dispatch to registered handlers)
    alt success
      H-->>M: OK
      M->>Q: finalize(id, errorResults=[])
      Q->>L: insert log(COMPLETED)
      Q->>Q: delete from events
    else failure
      H-->>M: error(s)
      M->>Q: finalize(id, errorResults=[Error])
      Q->>L: insert log(COMPLETED_WITH_ERRORS)
      Q->>Q: delete from events
    end

    Note over M: process dies while PROCESSING
    M--xH: crash
    Note over Q: time passes
    M->>Q: poll()
    Q-->>M: select PROCESSING row where updated_at < now - timeout
    Q->>Q: attempts=+1, updated_at=now (re-acquired)
    M->>H: handle(event)
    H-->>M: OK or error
    M->>Q: finalize(...)
```


## Abandoned events

- Default timeout: 1 minute (configurable in code).
- Criteria: status = PROCESSING and `updated_at < now - timeout`.
- Re-acquire effect: attempts is incremented and `updated_at` is refreshed to now.

## API (Kotlin)

- `publish(event: Event): QueuedEvent` 
- `poll(clock: Clock = Clock.System): QueuedEvent?`  // non-blocking; returns null if none
- `finalize(id: Long, errorResults: List<EventHandeledResult.Error> = emptyList())`

## Operational notes

- Monitor queue depth, attempts, processing latency, and failure rate.
- Consider a max attempts policy to shunt poison events to COMPLETED_WITH_ERRORS in `event_log`.

## Source

- Implementation: `EventQueue.kt`
- Manager and handler registration: `EventManager.kt`
