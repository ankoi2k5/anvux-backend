ALTER TABLE tasks
    ADD COLUMN recurrence       VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN spawned_next_id  UUID;

ALTER TABLE tasks
    ADD CONSTRAINT chk_tasks_recurrence CHECK (recurrence IN ('NONE', 'DAILY', 'WEEKLY'));