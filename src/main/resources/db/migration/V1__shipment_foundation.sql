CREATE TABLE customer (
    customer_id UUID PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE shipment (
    shipment_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customer (customer_id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    pickup_instructions TEXT NOT NULL,
    delivery_instructions TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT shipment_status_allowed CHECK (status IN ('REQUESTED', 'DISPATCHED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT shipment_pickup_instructions_nonblank CHECK (length(btrim(pickup_instructions)) > 0),
    CONSTRAINT shipment_delivery_instructions_nonblank CHECK (length(btrim(delivery_instructions)) > 0),
    CONSTRAINT shipment_version_nonnegative CHECK (version >= 0)
);

CREATE INDEX shipment_customer_created_id_idx
    ON shipment (customer_id, created_at DESC, shipment_id);
