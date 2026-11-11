-- Shared account/profile tables are integration foundations for Rampan's modules.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('pembeli', 'petani', 'admin')),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE farmer_profiles (
    id UUID PRIMARY KEY REFERENCES users(id), store_name VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(20), pickup_location TEXT, service_area TEXT NOT NULL DEFAULT '',
    delivery_fee BIGINT NOT NULL DEFAULT 0 CHECK (delivery_fee >= 0), shipping_enabled BOOLEAN NOT NULL DEFAULT false,
    time_slots TEXT NOT NULL DEFAULT '',
    ktp_status VARCHAR(20) NOT NULL DEFAULT 'belum_diajukan' CHECK (ktp_status IN ('belum_diajukan','menunggu','disetujui','ditolak')),
    ktp_rejection_reason TEXT, ktp_document_path VARCHAR(512), ktp_submitted_at TIMESTAMPTZ,
    ktp_reviewed_at TIMESTAMPTZ, ktp_reviewed_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE farmer_payment_methods (
    id UUID PRIMARY KEY, farmer_id UUID NOT NULL REFERENCES users(id),
    method_type VARCHAR(20) NOT NULL CHECK (method_type IN ('qris','bank_transfer')),
    qris_image_path VARCHAR(512), bank_name VARCHAR(100), account_number VARCHAR(50), account_holder_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((method_type = 'qris' AND nullif(trim(qris_image_path),'') IS NOT NULL AND nullif(trim(account_holder_name),'') IS NOT NULL)
        OR (method_type = 'bank_transfer' AND nullif(trim(bank_name),'') IS NOT NULL
            AND nullif(trim(account_number),'') IS NOT NULL AND nullif(trim(account_holder_name),'') IS NOT NULL))
);
CREATE TABLE settings (
    key VARCHAR(100) PRIMARY KEY, value TEXT NOT NULL,
    updated_by UUID REFERENCES users(id), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO settings(key, value) VALUES ('transaction_limit', '10000000');
CREATE TABLE commodities (
    id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX commodities_name_unique ON commodities(lower(name));
CREATE TABLE products (
    id UUID PRIMARY KEY, farmer_id UUID NOT NULL REFERENCES users(id), commodity_id UUID NOT NULL REFERENCES commodities(id),
    name VARCHAR(255) NOT NULL, description TEXT, photo_url VARCHAR(512), price_per_kg BIGINT NOT NULL CHECK (price_per_kg > 0),
    stock_physical NUMERIC(10,2) NOT NULL DEFAULT 0, stock_reserved NUMERIC(10,2) NOT NULL DEFAULT 0,
    ready_date DATE NOT NULL, publication_status VARCHAR(20) NOT NULL DEFAULT 'draft' CHECK (publication_status IN ('draft','aktif','nonaktif')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (stock_physical >= 0 AND stock_reserved >= 0 AND stock_reserved <= stock_physical)
);
CREATE INDEX products_farmer_idx ON products(farmer_id);
CREATE INDEX products_catalog_idx ON products(publication_status, commodity_id, price_per_kg, id);
CREATE TABLE cart (
    id UUID PRIMARY KEY, buyer_id UUID NOT NULL REFERENCES users(id), product_id UUID NOT NULL REFERENCES products(id),
    quantity_kg NUMERIC(10,2) NOT NULL CHECK (quantity_kg > 0), UNIQUE(buyer_id, product_id)
);
CREATE TABLE orders (
    id UUID PRIMARY KEY, buyer_id UUID NOT NULL REFERENCES users(id), farmer_id UUID NOT NULL REFERENCES users(id),
    fulfillment_method VARCHAR(20) NOT NULL CHECK (fulfillment_method IN ('pickup','delivery')),
    delivery_fee BIGINT NOT NULL CHECK (delivery_fee >= 0), subtotal_items BIGINT NOT NULL CHECK (subtotal_items >= 0),
    total_payment BIGINT NOT NULL CHECK (total_payment >= 0), transaction_limit_applied BIGINT NOT NULL CHECK (transaction_limit_applied > 0),
    order_status VARCHAR(30) NOT NULL DEFAULT 'menunggu_petani', payment_status VARCHAR(20) NOT NULL DEFAULT 'belum_dibayar',
    scheduled_date DATE NOT NULL, scheduled_time_slot VARCHAR(100) NOT NULL,
    pickup_location_snapshot TEXT, delivery_address_snapshot TEXT, delivery_district_snapshot VARCHAR(100), buyer_notes TEXT,
    received_at TIMESTAMPTZ, reserved_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL, request_hash VARCHAR(64) NOT NULL,
    UNIQUE(buyer_id, idempotency_key), CHECK (total_payment = subtotal_items + delivery_fee AND total_payment <= transaction_limit_applied),
    CHECK (fulfillment_method <> 'pickup' OR delivery_fee = 0)
);
CREATE INDEX orders_buyer_created_idx ON orders(buyer_id, created_at DESC);
CREATE INDEX orders_farmer_status_idx ON orders(farmer_id, order_status);
CREATE INDEX orders_expiry_idx ON orders(order_status, expires_at);
CREATE TABLE order_items (
    id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id), product_id UUID NOT NULL REFERENCES products(id),
    product_name_snapshot VARCHAR(255) NOT NULL, commodity_snapshot VARCHAR(100) NOT NULL, ready_date_snapshot DATE NOT NULL,
    quantity_kg NUMERIC(10,2) NOT NULL CHECK (quantity_kg > 0), price_per_kg_snapshot BIGINT NOT NULL CHECK (price_per_kg_snapshot > 0),
    item_subtotal BIGINT NOT NULL CHECK (item_subtotal >= 0), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(order_id, product_id)
);
CREATE TABLE stock_reservations (
    id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES products(id), order_id UUID NOT NULL REFERENCES orders(id),
    quantity_kg NUMERIC(10,2) NOT NULL CHECK (quantity_kg > 0), status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active','released','fulfilled')),
    reserved_at TIMESTAMPTZ NOT NULL, released_at TIMESTAMPTZ, fulfilled_at TIMESTAMPTZ, UNIQUE(order_id, product_id),
    CHECK ((status='active' AND released_at IS NULL AND fulfilled_at IS NULL) OR
           (status='released' AND released_at IS NOT NULL AND fulfilled_at IS NULL) OR
           (status='fulfilled' AND fulfilled_at IS NOT NULL AND released_at IS NULL))
);
CREATE INDEX reservations_product_status_idx ON stock_reservations(product_id, status);
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id), from_status VARCHAR(30), to_status VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(id), actor_role VARCHAR(20) NOT NULL, changed_at TIMESTAMPTZ NOT NULL, note TEXT
);
CREATE INDEX order_status_history_order_idx ON order_status_history(order_id, changed_at);

-- Status/received_at/expires_at may evolve; the commercial and schedule snapshots cannot.
CREATE FUNCTION protect_order_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.buyer_id,NEW.farmer_id,NEW.fulfillment_method,NEW.delivery_fee,NEW.subtotal_items,NEW.total_payment,
           NEW.transaction_limit_applied,NEW.scheduled_date,NEW.scheduled_time_slot,NEW.pickup_location_snapshot,
           NEW.delivery_address_snapshot,NEW.delivery_district_snapshot,NEW.buyer_notes,NEW.idempotency_key,NEW.request_hash)
       IS DISTINCT FROM
       ROW(OLD.buyer_id,OLD.farmer_id,OLD.fulfillment_method,OLD.delivery_fee,OLD.subtotal_items,OLD.total_payment,
           OLD.transaction_limit_applied,OLD.scheduled_date,OLD.scheduled_time_slot,OLD.pickup_location_snapshot,
           OLD.delivery_address_snapshot,OLD.delivery_district_snapshot,OLD.buyer_notes,OLD.idempotency_key,OLD.request_hash) THEN
        RAISE EXCEPTION 'Order snapshots are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER orders_snapshot_immutable BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION protect_order_snapshot();
CREATE FUNCTION protect_order_item() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Order items are immutable';
END;
$$;
CREATE TRIGGER order_items_immutable BEFORE UPDATE OR DELETE ON order_items FOR EACH ROW EXECUTE FUNCTION protect_order_item();
