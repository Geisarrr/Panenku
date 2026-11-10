CREATE TABLE buyer_profiles (
    id UUID PRIMARY KEY REFERENCES users(id), full_name VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(20), delivery_address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
