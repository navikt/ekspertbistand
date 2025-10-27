CREATE TABLE IF NOT EXISTS event_queue
(
    id         BIGSERIAL PRIMARY KEY,
    event_json JSON                                NOT NULL,
    status     INT       DEFAULT 0                 NOT NULL,
    attempts   INT       DEFAULT 0                 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX statusOppdatert ON event_queue (status, updated_at, id);
CREATE TABLE IF NOT EXISTS event_log
(
    id         BIGINT PRIMARY KEY,
    event_json JSON                                NOT NULL,
    status     INT       DEFAULT 0                 NOT NULL,
    errors     JSON      DEFAULT '[]'::json        NOT NULL,
    attempts   INT       DEFAULT 0                 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS event_handler_states
(
    id            BIGINT,
    handler_id    TEXT,
    "result"      JSON NOT NULL,
    error_message TEXT NULL,
    CONSTRAINT pk_event_handler_states PRIMARY KEY (id, handler_id)
);
CREATE SEQUENCE IF NOT EXISTS event_queue_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807;
