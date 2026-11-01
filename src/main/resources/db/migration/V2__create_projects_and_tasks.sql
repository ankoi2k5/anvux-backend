CREATE TABLE projects (
                          id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                          user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                          name        VARCHAR(120) NOT NULL,
                          description VARCHAR(1000),
                          color       VARCHAR(9),
                          archived    BOOLEAN      NOT NULL DEFAULT FALSE,
                          version     BIGINT       NOT NULL DEFAULT 0,
                          created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                          updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_user_id ON projects (user_id);

CREATE TABLE tasks (
                       id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                       project_id        UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
                       title             VARCHAR(200) NOT NULL,
                       description       VARCHAR(2000),
                       status            VARCHAR(20)  NOT NULL DEFAULT 'TODO',
                       priority          VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
                       due_at            TIMESTAMPTZ,
                       completed_at      TIMESTAMPTZ,
                       estimated_minutes INTEGER,
                       actual_minutes    INTEGER,
                       version           BIGINT       NOT NULL DEFAULT 0,
                       created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       CONSTRAINT chk_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
                       CONSTRAINT chk_tasks_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
                       CONSTRAINT chk_tasks_estimated CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
                       CONSTRAINT chk_tasks_actual CHECK (actual_minutes IS NULL OR actual_minutes >= 0)
);

CREATE INDEX idx_tasks_user_project ON tasks (user_id, project_id);
CREATE INDEX idx_tasks_user_status ON tasks (user_id, status);
CREATE INDEX idx_tasks_user_due ON tasks (user_id, due_at);