-- A background job is a system actor, not an impersonated buyer/farmer/admin.
ALTER TABLE order_status_history ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE order_status_history ADD CONSTRAINT history_actor_kind CHECK (
    (actor_role = 'system' AND actor_id IS NULL) OR
    (actor_role IN ('pembeli','petani','admin') AND actor_id IS NOT NULL)
);
