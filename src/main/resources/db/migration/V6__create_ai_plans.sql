CREATE TABLE ai_plans (
                          id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                          user_id            UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                          prompt             VARCHAR(2000) NOT NULL,
                          target_project_id  UUID          REFERENCES projects (id) ON DELETE SET NULL,
                          project_name       VARCHAR(120),
                          summary            VARCHAR(1000),
                          status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
                          provider           VARCHAR(30)   NOT NULL,
                          model              VARCHAR(60),
                          created_project_id UUID,
                          executed_at        TIMESTAMPTZ,
                          version            BIGINT        NOT NULL DEFAULT 0,
                          created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          CONSTRAINT chk_ai_plans_status CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'EXECUTED'))
);

CREATE INDEX idx_ai_plans_user_created ON ai_plans (user_id, created_at DESC);

CREATE TABLE ai_plan_items (
                               id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                               plan_id                 UUID         NOT NULL REFERENCES ai_plans (id) ON DELETE CASCADE,
                               sort_order              INTEGER      NOT NULL,
                               action                  VARCHAR(20)  NOT NULL DEFAULT 'CREATE_TASK',
                               title                   VARCHAR(200) NOT NULL,
                               description             VARCHAR(2000),
                               priority                VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
                               due_at                  TIMESTAMPTZ,
                               estimated_minutes       INTEGER,
                               reminder_minutes_before INTEGER,
                               created_task_id         UUID,
                               CONSTRAINT chk_ai_plan_items_action CHECK (action IN ('CREATE_TASK')),
    CONSTRAINT chk_ai_plan_items_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_ai_plan_items_plan ON ai_plan_items (plan_id);