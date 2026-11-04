CREATE TABLE reminders (
                           id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                           user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                           task_id    UUID        NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
                           remind_at  TIMESTAMPTZ NOT NULL,
                           enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
                           version    BIGINT      NOT NULL DEFAULT 0,
                           created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                           updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                           CONSTRAINT uq_reminders_task_time UNIQUE (task_id, remind_at)
);

CREATE INDEX idx_reminders_user_remind ON reminders (user_id, remind_at);
CREATE INDEX idx_reminders_task ON reminders (task_id);