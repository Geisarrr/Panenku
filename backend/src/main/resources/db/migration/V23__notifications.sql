CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL CHECK(length(trim(title)) > 0),
    body TEXT,
    type VARCHAR(50) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT false,
    deep_link VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX notifications_user_created_idx ON notifications(user_id, created_at DESC, id DESC);
CREATE INDEX notifications_user_unread_idx ON notifications(user_id, created_at DESC, id DESC) WHERE is_read = false;
