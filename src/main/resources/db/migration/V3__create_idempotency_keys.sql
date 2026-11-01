CREATE TABLE idempotency_keys (
                                  id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                  user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                  idempotency_key VARCHAR(100) NOT NULL,
                                  resource_type   VARCHAR(30)  NOT NULL,
                                  resource_id     UUID         NOT NULL,
                                  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                  CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);