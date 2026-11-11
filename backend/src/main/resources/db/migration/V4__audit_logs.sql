-- Audit trail for admin actions (KTP verification, suspend/restore, settings changes).
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES users(id),
    actor_role VARCHAR(20) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50),
    target_id UUID,
    old_values TEXT,
    new_values TEXT,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_actor_created_idx ON audit_logs(actor_id, created_at DESC);
CREATE INDEX audit_logs_target_idx ON audit_logs(target_type, target_id);
